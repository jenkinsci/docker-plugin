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
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.Descriptor.FormException;
import hudson.slaves.Cloud;
import hudson.slaves.SlaveComputer;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;
import jenkins.model.Jenkins;

/**
 * Periodic job, which gets executed by Jenkins automatically, to ensure the
 * consistency of the containers currently running on the docker and the nodes
 * which are attached to this Jenkins instance.
 * 
 * @author eaglerainbow
 *
 */

@Extension
public class DockerContainerWatchdog extends AsyncPeriodicWork {
    private static int terminateNodeNameCounter = 0;
    
    private Clock clock;
    
    public DockerContainerWatchdog() {
        super(String.format("%s Asynchronous Periodic Work", DockerContainerWatchdog.class.getSimpleName()));
        this.clock = Clock.systemUTC();
    }

    /**
     * sets the internal clock of this class - only to be used in unit testing environment!
     * @param clock the clock which shall be used from now on
     */
    @Restricted(NoExternalUse.class)
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * The recurrence period how often this task shall be run
     */
    private static final long RECURRENCE_PERIOD_IN_MS = 5 * 60 * 1000;
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerContainerWatchdog.class);

    /**
     * The duration, which is permitted containers to start/run without having a node attached.
     * This is to prevent that the watchdog may be undesirably kill containers, which are just
     * on the way to the be started.
     * It automatically also is a "minimal lifetime" value for containers, before this watchdog
     * is allowed to kill any container. 
     */
    private static final Duration GRACE_DURATION_FOR_CONTAINERS_TO_START_WITHOUT_NODE_ATTACHED = Duration.ofSeconds(60);
    
    @Override
    public long getRecurrencePeriod() {
        // value is in ms.
        return RECURRENCE_PERIOD_IN_MS;
    }
    
    /*
     * Methods used for decloupling on unit testing
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
    
    protected DockerTransientNode createDockerTransientNode(String nodeName, String containerId, String remoteFs) throws FormException, IOException {
        return new DockerTransientNode(nodeName, containerId, remoteFs, null /* a little ugly, but we just want to terminate it */);
    }

    protected void removeNode(DockerTransientNode dtn) throws IOException {
        Jenkins.getInstance().removeNode(dtn);
    }

    /*
     * Implementation of business logic
     */
    
    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        LOGGER.info("Docker Container Watchdog has been triggered");
        
        ContainerNodeNameMapping csmMerged = new ContainerNodeNameMapping();
        Map<String, Node> nodeMap = loadNodeMap();
        
        for (Cloud c : getAllClouds()) {
            if (!(c instanceof DockerCloud)) {
                continue;
            }
            DockerCloud dc = (DockerCloud) c;
            LOGGER.info("Checking Docker Cloud '{}'", dc.getDisplayName());
            listener.getLogger().println(String.format("Checking Docker Cloud %s", dc.getDisplayName()));
            
            csmMerged = processCloud(dc, nodeMap, csmMerged);
        }

        checkForSuperfluousComputer(nodeMap, csmMerged);
        
        LOGGER.info("Docker Container Watchdog check has been completed");
    }
    
    private Map<String, Node> loadNodeMap() {
        Map<String, Node> nodeMap = new HashMap<>();
        
        for (Node n : getAllNodes()) {
            nodeMap.put(n.getNodeName(), n);
        }
        
        LOGGER.info("We currently have {} nodes assigned to this Jenkins instance, which we will check", nodeMap.size());
        
        return nodeMap;
    }

    private ContainerNodeNameMapping processCloud(DockerCloud dc, Map<String, Node> nodeMap, ContainerNodeNameMapping csmMerged) {
        DockerAPI dockerApi = dc.getDockerApi();
        
        try (final DockerClient client = dockerApi.getClient()) {
            ContainerNodeNameMapping csm = retrieveContainers(client);
            
            cleanupSuperfluousContainers(client, nodeMap, csm, dc);
            
            csmMerged = csmMerged.merge(csm);
        } catch (IOException e) {
            LOGGER.warn("Failed to properly close a DockerClient instance; ignoring", e);
        }
        
        return csmMerged;
    }

    private ContainerNodeNameMapping retrieveContainers(DockerClient client) {
        /*
         * Warning!
         * We have a DockerClient which is based on an associated DockerCloud.
         * However, it is not said that each DockerCloud is also referring to a different
         * Docker instance! They may point to the same one, but - for example - just may
         * have different credentials for logging on (or so).
         * 
         * For example, Triton (which has an own implementation of the Docker API, and 
         * we are supporting this here with this plugin) will isolate the containers 
         * then between users.
         * The common community edition of Docker does NOT isolate the containers. 
         * 
         * This means that we have to correlate each Container uniquely also the 
         * instance of DockerCloud manually.
         */
        Map<String, String> labelFilter = new HashMap<>();
        
        labelFilter.put(DockerTemplateBase.CONTAINER_LABEL_JENKINS_INSTANCE_ID, getJenkinsInstanceId());
        
        List<Container> containerList = client.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(labelFilter)
                .exec();
        
        ContainerNodeNameMapping result = new ContainerNodeNameMapping();

        for (Container container : containerList) {
            String containerId = container.getId();
            String status = container.getStatus();
            
            if (status == null) {
                LOGGER.warn("Container {} has a null-status and thus cannot be checked; ignoring container", containerId);
                continue;
            }
            
            
            Map<String, String> containerLabels = getLabelsOfContainer(client, containerId);
            
            String containerNodeName = containerLabels.get(DockerTemplate.CONTAINER_LABEL_NODE_NAME);
            if (containerNodeName == null) {
                LOGGER.warn("Container {} is said to be created by this Jenkins instance, but does not have any node name label assigned; manual cleanup is required for this", containerId);
                continue;
            }
            
            result.registerMapping(container, containerNodeName);
        }
        
        return result;
    }

    private static Map<String, String> getLabelsOfContainer(DockerClient client, String containerId) {
        // TODO may be called multiple times => may be cached to save performance
        InspectContainerResponse icr = client.inspectContainerCmd(containerId).exec();
        return icr.getConfig().getLabels();
    }

    private void cleanupSuperfluousContainers(DockerClient client, Map<String, Node> nodeMap, ContainerNodeNameMapping csm, DockerCloud dc) {
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
            if (isStillTooYoung(container.getCreated()))
                continue;

            // this is a container, which is missing a corresponding node with us
            LOGGER.info("Container {}, which is reported to be assigned to node {}, is no longer associated (node might be gone already?)", container.getId(), nodeName);
            LOGGER.info("Container {}'s last status is {}; it was created on {}", container.getId(), container.getStatus(), container.getCreated());
            
            try {
                terminateContainer(dc, client, container);
            } catch (Exception e) {
                // Graceful termination failed; we need to use some force
                LOGGER.warn("Graceful termination of Container {} failed; terminating directly via API - this may cause remnants to be left behind", container.getId(), e);
            }
        }
    }
    
    private boolean isStillTooYoung(Long created) {
        Instant createdInstant = Instant.ofEpochSecond(created.longValue());
        
        Duration containerLifetime = Duration.between(createdInstant, clock.instant());
        Duration untilMayBeCleanedUp = containerLifetime.minus(GRACE_DURATION_FOR_CONTAINERS_TO_START_WITHOUT_NODE_ATTACHED);
        
        return untilMayBeCleanedUp.isNegative();
    }

    private void terminateContainer(DockerCloud dc, DockerClient client, Container container) {
        boolean gracefulFailed = false;
        try {
            terminateContainerGracefully(dc, container);
        } catch (TerminationException e) {
            LOGGER.warn("Graceful termination of container {} failed with TerminationException", container.getId(), e);
            gracefulFailed = true;
        }
        
        if (gracefulFailed) {
            try {
                client.removeContainerCmd(container.getId()).withForce(true).exec();
            } catch (RuntimeException e) {
                LOGGER.warn("Forced termination of container {} failed with RuntimeException", container.getId(), e);
            }
        }
    }
    
    private static class TerminationException extends Exception {

        /**
         * 
         */
        private static final long serialVersionUID = -7259431101547222511L;

        public TerminationException(String message, Throwable cause) {
            super(message, cause);
        }

        public TerminationException(String message) {
            super(message);
        }
        
    }

    private void terminateContainerGracefully(DockerCloud dc, Container container) throws TerminationException {
        String nodeName = String.format("terminateNode-%d", terminateNodeNameCounter++);
        String containerId = container.getId();
        
        Map<String, String> containerLabels = null;
        DockerAPI dockerApi = dc.getDockerApi();
        
        try (final DockerClient client = dockerApi.getClient()) {
            containerLabels = getLabelsOfContainer(client, containerId);
        } catch (IOException e) {
            LOGGER.info("Unable to close Docker client while trying to gracefully terminate container", e);
        }
        String templateName = containerLabels.get(DockerTemplate.CONTAINER_LABEL_TEMPLATE_NAME);
        
        // NB: dc.getTemplate(String) does not work here, as it compares images!
        DockerTemplate template = null;
        for (DockerTemplate dockerTemplate : dc.getTemplates()) {
            if (dockerTemplate.getName().equals(templateName)) {
                template = dockerTemplate;
                break;
            }
        }
        
        if (template == null) {
            throw new TerminationException(String.format("Template %s in DockerCloud %s does not exist", templateName, dc.toString()));
        }
        
        DockerTransientNode dtn;
        try {
            dtn = createDockerTransientNode(nodeName, containerId, template.getRemoteFs());
        } catch (FormException | IOException e) {
            throw new TerminationException("Creating DockerTransientNode failed due to exception", e);
        }
        dtn.setNodeDescription(String.format("%s is terminating detached Container %s", DockerContainerWatchdog.class.getSimpleName(), containerId));
        dtn.setAcceptingTasks(false);
        dtn.setDockerAPI(dockerApi);
        dtn.setRemoveVolumes(template.isRemoveVolumes());
        dtn.setMode(Node.Mode.EXCLUSIVE); // restrict usage as much as possible
        dtn.setNumExecutors(0);
        
        try {
            dtn.terminate(LOGGER);
        } catch (RuntimeException e) {
            throw new TerminationException("Termining via DockerTransientNode failed due to exception", e);
        }
        
        LOGGER.info("Successfully terminated container {} consistently", containerId);
    }

    private void checkForSuperfluousComputer(Map<String, Node> nodeMap, ContainerNodeNameMapping csmMerged) {
        for (Node node : nodeMap.values()) {
            if (! (node instanceof DockerTransientNode)) {
                // this node does not belong to us
                continue;
            }
            
            DockerTransientNode dtn = (DockerTransientNode) node;
            
            Container container = csmMerged.getContainerById(dtn.getContainerId());
            if (container != null) {
                // the node and the container still have a proper mapping => ok
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
            LOGGER.info("Node {} reports to have container {} assigned to it, but the container does not exist on docker; cleaning it up", dtn.getNodeName(), dtn.getContainerId());

            try {
                removeNode(dtn);
            } catch (IOException e) {
                LOGGER.warn("Unable to remove orphaned DockerNode due to exception", e);
            }
        }
    }

}
