package com.nirima.jenkins.plugins.docker;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.SlaveComputer;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;
import jenkins.model.Jenkins;

/**
 * Periodic job which gets executed by Jenkins automatically. Ensures the
 * consistency of the containers currently running on the docker and the nodes
 * which are attached to this Jenkins instance.
 * 
 * @author eaglerainbow
 */
@Extension
public class DockerContainerWatchdog extends AsyncPeriodicWork {
    private Clock clock;

    public DockerContainerWatchdog() {
        super(String.format("%s Asynchronous Periodic Work", DockerContainerWatchdog.class.getSimpleName()));
        clock = Clock.systemUTC();
    }

    /**
     * sets the internal clock of this class - only to be used in unit testing environment!
     * @param clock the clock which shall be used from now on
     */
    @Restricted(NoExternalUse.class)
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerContainerWatchdog.class);

    /**
     * The recurrence period how often this task shall be run
     * 
     * NB: Reading the property here statically is the right think to do,
     * because getRecurrencePeriod() is required to return a constant (i.e.
     * it may not change during runtime).
     */
    private static final long RECURRENCE_PERIOD_IN_MS = JenkinsUtils.getSystemPropertyLong(DockerContainerWatchdog.class.getName()+".recurrenceInSeconds", Long.valueOf(5*60))*1000L;

    /**
     * The duration, which defines the maximal amount of time the watchdog is allowed to run 
     * for a single execution.
     * The processing will be stopped on next occasion, if this threshold is exceeded.
     */
    private static final Duration PROCESSING_TIMEOUT_IN_MS = Duration.ofMillis(RECURRENCE_PERIOD_IN_MS * 4 / 5);

    private static final Statistics executionStatistics = new Statistics();

    @Override
    public long getRecurrencePeriod() {
        // value is in ms.
        return RECURRENCE_PERIOD_IN_MS;
    }

    /*
     * Methods used for decoupling on unit testing
     */

    protected List<DockerCloud> getAllClouds() {
        return DockerCloud.instances();
    }

    protected List<Node> getAllNodes() {
        return Jenkins.getInstance().getNodes();
    }

    protected String getJenkinsInstanceId() {
        return DockerTemplateBase.getJenkinsInstanceIdForContainerLabel();
    }

    protected void removeNode(DockerTransientNode dtn) throws IOException {
        Jenkins.getInstance().removeNode(dtn);
    }

    protected boolean stopAndRemoveContainer(DockerAPI dockerApi, Logger logger, String description, boolean removeVolumes, String containerId,
            boolean stop) {
        return DockerTransientNode.stopAndRemoveContainer(dockerApi, logger, description, removeVolumes, containerId, stop);
    }


    /*
     * Implementation of business logic
     */

    private static class WatchdogProcessingTimeout extends Error {
        private static final long serialVersionUID = 2162341066478288340L;

        public WatchdogProcessingTimeout() {
            super();
        }
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        if (!JenkinsUtils.getSystemPropertyBoolean(DockerContainerWatchdog.class.getName()+".enabled", true)) {
            LOGGER.info("Docker Container Watchdog is disabled based on system configuration");
            return;
        }

        LOGGER.info("Docker Container Watchdog has been triggered");

        executionStatistics.writeStatisticsToLog();

        executionStatistics.addExecution();

        Instant start = clock.instant();
        try {
            ContainerNodeNameMap csmMerged = new ContainerNodeNameMap();

            Instant snapshotInstance = clock.instant();
            Map<String, Node> nodeMap = loadNodeMap();

            try {
                for (DockerCloud dc : getAllClouds()) {
                    LOGGER.info("Checking Docker Cloud {} at {}", dc.getDisplayName(), dc.getDockerApi().getDockerHost().getUri());
                    listener.getLogger().println(String.format("Checking Docker Cloud %s", dc.getDisplayName()));

                    csmMerged = processCloud(dc, nodeMap, csmMerged, snapshotInstance);
                }

                if (csmMerged.isContainerListIncomplete()) {
                    LOGGER.info("Not checking the list of nodes, as list of containers is known to be incomplete");
                } else {
                    cleanUpSuperfluousComputer(nodeMap, csmMerged, snapshotInstance);
                }
            } catch (WatchdogProcessingTimeout timeout) {
                LOGGER.warn("Processing of cleanup watchdog took too long; current timeout value: {} ms, "
                        + "watchdog started on {}", PROCESSING_TIMEOUT_IN_MS, start.toString(), timeout);
                executionStatistics.addProcessingTimeout();
                executionStatistics.addOverallRuntime(Duration.between(start, clock.instant()).toMillis());
                return;
            }
        } finally {
            Instant stop = clock.instant();
            executionStatistics.addOverallRuntime(Duration.between(start, stop).toMillis());
        }

        LOGGER.info("Docker Container Watchdog check has been completed");
    }

    private Map<String, Node> loadNodeMap() {
        Map<String, Node> nodeMap = new HashMap<>();

        for (Node n : getAllNodes()) {
            nodeMap.put(n.getNodeName(), n);

            /*
             * Note: We are taking all nodes into consideration, and not just
             * those which have been created by this plugin.
             * Reasoning is as follows:
             * The node names of docker-plugin's created containers are generated by the plugin itself.
             * The identifiers are unique (within the Jenkins server).
             * If someone creates a node that exactly matches such a unique name, then
             * this is very likely to have been done on purpose.
             * If we ignored such nodes, we could end up removing containers that the
             * user did not want cleaned up.
             * Whereas if we do not ignore such nodes then there's no lasting harm.
             * 
             * For further details on that discussion, see https://github.com/jenkinsci/docker-plugin/pull/658#discussion_r192695136
             */
        }

        LOGGER.info("We currently have {} nodes assigned to this Jenkins instance, which we will check", nodeMap.size());

        return nodeMap;
    }

    private ContainerNodeNameMap processCloud(DockerCloud dc, Map<String, Node> nodeMap, ContainerNodeNameMap csmMerged, Instant snapshotInstant) {
        DockerAPI dockerApi = dc.getDockerApi();

        try (final DockerClient client = dockerApi.getClient()) {
            ContainerNodeNameMap csm = retrieveContainers(dc, client);

            DockerDisabled dcDisabled = dc.getDisabled();
            if (dcDisabled.isDisabled()) {
                LOGGER.info("Will not cleanup superfluous containers on DockerCloud [name={}, dockerURI={}], as it is disabled", dc.getDisplayName(), dc.getDockerApi().getDockerHost().getUri());
            } else {
                cleanUpSuperfluousContainers(client, nodeMap, csm, dc, snapshotInstant);
            }

            csmMerged = csmMerged.merge(csm);
        } catch (IOException e) {
            LOGGER.warn("Failed to properly close a DockerClient instance after reading the list of containers and cleaning them up; ignoring", e);
        } catch (ContainersRetrievalException e) {
            csmMerged.setContainerListIncomplete(true);
        }

        return csmMerged;
    }

    private class ContainersRetrievalException extends Exception {
        private static final long serialVersionUID = -3370783213009509440L;

        public ContainersRetrievalException(Throwable cause) {
            super(cause);
        }
    }

    private ContainerNodeNameMap retrieveContainers(DockerCloud dc, DockerClient client) throws ContainersRetrievalException {
        /*
         * Note:
         * There is no guarantee that each DockerCloud points to a different
         * Docker instance.  They may point to the same one, but, e.g. may
         * simply have different credentials, or different instance limits etc.
         * 
         * For example, Triton (which has an own implementation of the Docker API, and 
         * we are supporting this here with this plugin) will isolate the containers 
         * then between users.
         * The common community edition of Docker does NOT isolate the containers. 
         * 
         * This means that we could find a container via a different DockerClient to the
         * one from which it was created, and therefore we need to ensure that we do
         * not make any assumptions about its origin.
         */
        Map<String, String> labelFilter = new HashMap<>();

        labelFilter.put(DockerContainerLabelKeys.JENKINS_INSTANCE_ID, getJenkinsInstanceId());

        ContainerNodeNameMap result = new ContainerNodeNameMap();

        Instant start = clock.instant();

        try {
            List<Container> containerList = null;
            try {
                containerList = client.listContainersCmd()
                        .withShowAll(true)
                        .withLabelFilter(labelFilter)
                        .exec();
            } catch (Exception e) {
                LOGGER.warn("Unable to retrieve list of containers available on DockerCloud [name={}, dockerURI={}] while reading list of containers (showAll=true, labelFilters={})", dc.getDisplayName(), dc.getDockerApi().getDockerHost().getUri(), labelFilter.toString(), e);
                throw new ContainersRetrievalException(e);
            }

            for (Container container : containerList) {
                String containerId = container.getId();
                String status = container.getStatus();

                if (status == null) {
                    LOGGER.warn("Container {} has a null-status and thus cannot be checked; ignoring container", containerId);
                    continue;
                }

                Map<String, String> containerLabels = container.getLabels();

                /* NB: Null-map cannot happen here, as otherwise the container 
                 * would not have had the JenkinsId label, which we just have 
                 * requested it to have (see "withLabelFilter")
                 */

                String containerNodeName = containerLabels.get(DockerContainerLabelKeys.NODE_NAME);
                if (containerNodeName == null) {
                    LOGGER.warn("Container {} is said to be created by this Jenkins instance, but does not have any node name label assigned; manual cleanup is required for this", containerId);
                    continue;
                }

                result.registerMapping(container, containerNodeName);
            }
        } finally {
            Instant stop = clock.instant();
            executionStatistics.addRetrieveContainerRuntime(Duration.between(start, stop).toMillis());
        }

        return result;
    }

    private void cleanUpSuperfluousContainers(DockerClient client, Map<String, Node> nodeMap, ContainerNodeNameMap csm, DockerCloud dc, Instant snapshotInstant) {
        Collection<Container> allContainers = csm.getAllContainers();

        for (Container container : allContainers) {
            String nodeName = csm.getNodeName(container.getId());

            Node node = nodeMap.get(nodeName);
            if (node != null) {
                // the node and the container still have a proper mapping => ok
                continue;
            }

            /*
             * During startup it may happen temporarily that a container exists, but the
             * corresponding node isn't there yet.
             * That is why we have to have a grace period for pulling up containers.
             */
            if (isStillTooYoung(container.getCreated(), snapshotInstant)) {
                continue;
            }

            checkForTimeout(snapshotInstant);

            // this is a container, which is missing a corresponding node with us
            LOGGER.info("Container {}, which is reported to be assigned to node {}, "
                    + "is no longer associated (node might be gone already?). "
                    + "The container's last status is {}; it was created on {}", 
                    container.getId(), nodeName, container.getStatus(), container.getCreated());

            try {
                terminateContainer(dc, client, container);
            } catch (Exception e) {
                // Graceful termination failed; we need to use some force
                LOGGER.warn("Graceful termination of Container {} failed; terminating directly via API - this may cause remnants to be left behind", container.getId(), e);
            }
        }
    }

    private boolean isStillTooYoung(Long created, Instant snapshotInstant) {
        final Instant createdInstant = Instant.ofEpochSecond(created.longValue());

        final Duration containerLifetime = Duration.between(createdInstant, snapshotInstant);

        /**
         * We allow containers to have a grace duration, during which is permitted for them 
         * to start/run without having a node attached.
         * This is to prevent that the watchdog may be undesirably kill containers, which are just
         * on the way to the be started.
         * It automatically also is a "minimal lifetime" value for containers, before this watchdog
         * is allowed to kill any container. 
         */
        final Duration graceDurationForContainers = Duration.ofSeconds(JenkinsUtils.getSystemPropertyLong(DockerContainerWatchdog.class.getName()+".initialGraceDurationForContainersInSeconds", 60L));

        final Duration untilMayBeCleanedUp = containerLifetime.minus(graceDurationForContainers);

        return untilMayBeCleanedUp.isNegative();
    }

    private void terminateContainer(DockerCloud dc, DockerClient client, Container container) {
        boolean gracefulFailed = false;
        try {
            terminateContainerGracefully(dc, container);
        } catch (TerminationException e) {
            gracefulFailed = true;
        } catch (ContainerIsTaintedException e) {
            LOGGER.warn("Container {} has been tampered with; skipping cleanup", container.getId(), e);
            return;
        }

        if (gracefulFailed) {
            try {
                Instant start = clock.instant();
                client.removeContainerCmd(container.getId()).withForce(true).exec();
                Instant stop = clock.instant();
                executionStatistics.addContainerRemovalForce(Duration.between(start, stop).toMillis());
            } catch (RuntimeException e) {
                LOGGER.warn("Forced termination of container {} failed with RuntimeException", container.getId(), e);
                executionStatistics.addContainerRemovalFailed();
            }
        }
    }

    private static class TerminationException extends Exception {
        private static final long serialVersionUID = -7259431101547222511L;

        public TerminationException(String message) {
            super(message);
        }
    }

    private static class ContainerIsTaintedException extends Exception {
        private static final long serialVersionUID = -8500246547989418166L;

        public ContainerIsTaintedException(String message) {
            super(message);
        }
    }

    private void terminateContainerGracefully(DockerCloud dc, Container container) throws TerminationException, ContainerIsTaintedException {
        String containerId = container.getId();

        Map<String, String> containerLabels = container.getLabels();
        /* NB: Null-map cannot happen here, as otherwise the container 
         * would not have had the JenkinsId label, which is required for us to end up here.
         */

        /* 
         * double-confirm that no node appeared in the meantime
         * (A long time could have passed since our initial scanning; thus, 
         * the situation could have changed in the meantime)
         */
        final String nodeName = containerLabels.get(DockerContainerLabelKeys.NODE_NAME);
        if (nodeExistsBypassingCache(nodeName)) {
            LOGGER.warn("Attempt to terminate container ID {} gracefully, but a node for it suddenly has appeared", container.getId());
            throw new ContainerIsTaintedException(String.format("Node for container ID %s has appeared", container.getId()));
        }

        containerLabels.get(DockerContainerLabelKeys.REMOVE_VOLUMES);

        String removeVolumesString = containerLabels.get(DockerContainerLabelKeys.REMOVE_VOLUMES);
        if (removeVolumesString == null) {
            throw new ContainerIsTaintedException(String.format("Container ID %s has no '%s' label; skipping.",
                    container.getId(), DockerContainerLabelKeys.REMOVE_VOLUMES));
        }
        boolean removeVolumes = Boolean.parseBoolean(removeVolumesString);

        boolean containerRunning = true;
        if (container.getStatus().startsWith("Dead") 
                || container.getStatus().startsWith("Exited")
                || container.getStatus().startsWith("Created")) {
            containerRunning = false;
        }

        DockerAPI dockerApi = dc.getDockerApi();
        Instant start = clock.instant();
        boolean success = stopAndRemoveContainer(dockerApi, LOGGER, String.format("(orphaned container found by %s)", DockerContainerWatchdog.class.getSimpleName()),
                removeVolumes, container.getId(), !containerRunning);
        Instant stop = clock.instant();

        if (success) {
            executionStatistics.addContainerRemovalGracefully(Duration.between(start, stop).toMillis());
            LOGGER.info("Successfully terminated orphaned container {}", containerId);
        } else {
            throw new TerminationException("Graceful termination failed.");
        }
    }

    private boolean nodeExistsBypassingCache(String nodeName) {
        /* Note:
         * we are using "getAllNodes()" here to prevent that
         * we need yet another method to overwrite for our unit tests
         */
        for (Node node : getAllNodes()) {
            if (nodeName.equals(node.getNodeName())) {
                return true;
            }
        }
        return false;
    }

    private void cleanUpSuperfluousComputer(Map<String, Node> nodeMap, ContainerNodeNameMap csmMerged, Instant snapshotInstant) {
        for (Node node : nodeMap.values()) {
            if (! (node instanceof DockerTransientNode)) {
                // this node does not belong to us
                continue;
            }

            checkForTimeout(snapshotInstant);

            DockerTransientNode dtn = (DockerTransientNode) node;

            /*
             * Important note!
             * 
             * We cannot be sure that DockerTransientNode really knows the right getCloudId() (or even 
             * have the right getCloud() instance). This is due to the fact that the - for example - the 
             * node might be left-over from a previous configuration, which no longer is valid (e.g. the
             * slave was created with a DockerCloud configuration which did not work; that is why the admin
             * deleted that DockerCloud configuration while still the node was running; to clean up the mess,
             * he manually force-removed the containers from the docker instance). 
             * 
             * At the end, this means that we cannot verify strictly that the containerId stored with this
             * node, really exists on (any of the) DockerCloud instances or not. 
             * The only option is that, as long as we see "some container having an id like this" on "some of
             * our DockerCloud instances", then we should be careful with it, and do not delete the node (yet).
             * Even if the containerId originates from a collision, then we would just postpone the deletion
             * of the node up to that point in time, when that container gets deleted from the DockerContainer
             * instance (on the next recurrence of this watchdog, we then would detect that situation and the
             * node gets deleted automatically, as no container with the appropriate identifier could be detected
             * anymore). 
             * 
             * It is to be hoped that collision on container identifiers are happening rarely enough to such that
             * we do not run into troubles here. Otherwise, still a larger set of broken nodes could pile up...
             */

            boolean seenOnDockerInstance = csmMerged.isContainerIdRegistered(dtn.getContainerId());
            if (seenOnDockerInstance) {
                // the node and the container still might have a proper mapping => ignore
                continue;
            }

            SlaveComputer computer = dtn.getComputer();
            if (computer == null) {
                // Probably the node is being closed down right now, so we shouldn't touch it.
                continue;
            }

            if (!computer.isOffline()) {
                // the node is still running; we should not touch it.
                continue;
            }

            // the container is already gone for the node, but the node did not notice it yet properly
            LOGGER.info("{} has container ID {}, but the container does not exist in any docker cloud. Will remove node.", dtn, dtn.getContainerId());

            try {
                removeNode(dtn);
                executionStatistics.addNodeRemoved();
            } catch (IOException e) {
                LOGGER.warn("Failed to remove orphaned node {}", dtn.toString(), e);
                executionStatistics.addNodeRemovedFailed();
            }
        }
    }

    private void checkForTimeout(Instant startedTimestamp) {
        final Instant now = clock.instant();
        Duration runtime = Duration.between(startedTimestamp, now);

        if (runtime.compareTo(PROCESSING_TIMEOUT_IN_MS) > 0) {
            throw new WatchdogProcessingTimeout();
        }
    }

    /**
     * Stores the internal statistics.
     */
    private static class Statistics {
        private long executions;
        private long containersRemovedGracefully;
        private long containersRemovedGracefullyRuntimeSum;
        private long containersRemovedForce;
        private long containersRemovedForceRuntimeSum;
        private long containersRemovedFailed;
        private long nodesRemoved;
        private long nodesRemovedFailed;
        private long processingTimeout;
        private long overallRuntime;
        private long retrieveContainersRuntime;
        private long retrieveContainersCalls;

        public void writeStatisticsToLog() {
            LOGGER.info("Watchdog Statistics: "
                    + "Number of overall executions: {}, "
                    + "Executions with processing timeout: {}, "
                    + "Containers removed gracefully: {}, "
                    + "Containers removed with force: {}, "
                    + "Containers removal failed: {}, "
                    + "Nodes removed successfully: {}, "
                    + "Nodes removal failed: {}, "
                    + "Container removal average duration (gracefully): {} ms, "
                    + "Container removal average duration (force): {} ms, "
                    + "Average overall runtime of watchdog: {} ms, "
                    + "Average runtime of container retrieval: {} ms",
                    executions, 
                    processingTimeout,
                    containersRemovedGracefully,
                    containersRemovedForce,
                    containersRemovedFailed,
                    nodesRemoved,
                    nodesRemovedFailed,
                    getContainerRemovalAverageDurationGracefully(),
                    getContainerRemovalAverageDurationForce(),
                    getAverageOverallRuntime(),
                    getAverageRetrieveContainerRuntime()
                    );
        }

        private void addExecution() {
            executions++;
        }

        private void addContainerRemovalGracefully(long runtime) {
            containersRemovedGracefully++;
            containersRemovedGracefullyRuntimeSum += runtime;
        }

        private void addContainerRemovalForce(long runtime) {
            containersRemovedForce++;
            containersRemovedForceRuntimeSum += runtime;
        }

        private void addContainerRemovalFailed() {
            containersRemovedFailed++;
        }

        private void addNodeRemoved() {
            nodesRemoved++;
        }

        private void addNodeRemovedFailed() {
            nodesRemovedFailed++;
        }

        private void addProcessingTimeout() {
            processingTimeout++;
        }

        private void addOverallRuntime(long runtime) {
            overallRuntime += runtime;
        }
        
        private void addRetrieveContainerRuntime(long runtime) {
            retrieveContainersRuntime += runtime;
            retrieveContainersCalls++;
        }

        private String getAverageOverallRuntime() {
            if (executions == 0) {
                return "0";
            }
            return new Long(overallRuntime / executions).toString();
        }

        private String getContainerRemovalAverageDurationForce() {
            if (containersRemovedForce == 0) {
                return "0";
            }
            return new Long(containersRemovedForceRuntimeSum / containersRemovedForce).toString();
        }

        private String getContainerRemovalAverageDurationGracefully() {
            if (containersRemovedGracefully == 0) {
                return "0";
            }
            return new Long(containersRemovedGracefullyRuntimeSum / containersRemovedGracefully).toString();
        }
        
        private String getAverageRetrieveContainerRuntime() {
            if (retrieveContainersCalls == 0) {
                return "0";
            }
            return new Long(retrieveContainersRuntime / retrieveContainersCalls).toString();
        }
    }
}
