package io.jenkins.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerOfflineCause;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import io.jenkins.docker.client.DockerAPI;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedItem;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link AbstractCloudSlave} node designed to be used only once for a build.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerTransientNode extends AbstractCloudSlave implements TrackedItem {
    private static final long serialVersionUID = 1349729340506926183L;
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerTransientNode.class.getName());

    private final String containerId;

    private transient DockerAPI dockerAPI;

    private boolean removeVolumes;

    private int stopTimeout = DockerTemplate.DEFAULT_STOP_TIMEOUT;

    private String cloudId;

    private ProvisioningActivity.Id provisioningId;

    private AtomicBoolean acceptingTasks = new AtomicBoolean(true);

    /**
     * @deprecated Use {@link #DockerTransientNode(String, String, String)} then
     *             {@link #setLauncher(ComputerLauncher)}.
     * @param nodeName    Passed to
     *                    {@link #DockerTransientNode(String, String, String)}.
     * @param containerId Passed to
     *                    {@link #DockerTransientNode(String, String, String)}.
     * @param workdir     Passed to
     *                    {@link #DockerTransientNode(String, String, String)}.
     * @param launcher    Passed to {@link #setLauncher(ComputerLauncher)}
     * @throws             Descriptor.FormException See
     *                     {@link #DockerTransientNode(String, String, String)}.
     * @throws IOException See {@link #DockerTransientNode(String, String, String)}.
     */
    @Deprecated
    public DockerTransientNode(
            @NonNull String nodeName, @NonNull String containerId, String workdir, ComputerLauncher launcher)
            throws Descriptor.FormException, IOException {
        this(nodeName, containerId, workdir);
        setLauncher(launcher);
    }

    /**
     * Preferred constructor. Note that, unless this is a JNLP node, callers will
     * later have to call {@link #setLauncher(ComputerLauncher)}.
     *
     * @param nodeName    Name of the node; passed to
     *                    {@link AbstractCloudSlave#AbstractCloudSlave(String, String, ComputerLauncher)}.
     * @param containerId Docker container id.
     * @param workdir     remoteFs home dir; passed to
     *                    {@link AbstractCloudSlave#AbstractCloudSlave(String, String, ComputerLauncher)}.
     * @throws            Descriptor.FormException See
     *                     {@link AbstractCloudSlave#AbstractCloudSlave(String, String, ComputerLauncher)}.
     * @throws            IOException See
     *                     {@link AbstractCloudSlave#AbstractCloudSlave(String, String, ComputerLauncher)}.
     */
    public DockerTransientNode(@NonNull String nodeName, @NonNull String containerId, String workdir)
            throws Descriptor.FormException, IOException {
        super(nodeName, workdir, null);
        this.containerId = containerId;
        setNumExecutors(1);
        setMode(Mode.EXCLUSIVE);
        setRetentionStrategy(new DockerOnceRetentionStrategy(10));
    }

    @Override
    public boolean isAcceptingTasks() {
        return acceptingTasks == null || acceptingTasks.get();
    }

    public void setAcceptingTasks(boolean acceptingTasks) {
        this.acceptingTasks.set(acceptingTasks);
    }

    @NonNull
    public String getContainerId() {
        return containerId;
    }

    public void setDockerAPI(DockerAPI dockerAPI) {
        this.dockerAPI = dockerAPI;
    }

    /** @return The {@link DockerAPI} for our cloud. */
    public DockerAPI getDockerAPI() {
        if (dockerAPI == null) {
            final DockerCloud cloud = getCloud();
            if (cloud != null) {
                dockerAPI = cloud.getDockerApi();
            }
        }
        return dockerAPI;
    }

    @Override
    public String getDisplayName() {
        if (cloudId != null) {
            return getNodeName() + " on " + cloudId;
        }
        return getNodeName();
    }

    public boolean isRemoveVolumes() {
        return removeVolumes;
    }

    public void setRemoveVolumes(boolean removeVolumes) {
        this.removeVolumes = removeVolumes;
    }

    public int getStopTimeout() {
        return this.stopTimeout;
    }

    public void setStopTimeout(int timeout) {
        this.stopTimeout = timeout;
    }

    public String getCloudId() {
        return cloudId;
    }

    public void setCloudId(String cloudId) {
        this.cloudId = cloudId;
    }

    public ProvisioningActivity.Id getProvisioningId() {
        return provisioningId;
    }

    public void setProvisioningId(ProvisioningActivity.Id provisioningId) {
        this.provisioningId = provisioningId.named(getNodeName());
    }

    @Override
    public DockerComputer createComputer() {
        return new DockerComputer(this);
    }

    @Nullable
    @Override
    public ProvisioningActivity.Id getId() {
        return provisioningId;
    }

    private interface ILogger {
        void println(String msg);

        void error(String msg, Throwable ex);
    }

    @Override
    @Restricted(NoExternalUse.class)
    public void _terminate(final TaskListener listener) {
        final ILogger tl = createILoggerForTaskListener(listener);
        try {
            terminate(tl);
        } catch (Throwable ex) {
            tl.error("Failure while terminating '" + name + "':", ex);
        }
    }

    private static ILogger createILoggerForTaskListener(final TaskListener listener) {
        final ILogger tl = new ILogger() {
            @Override
            public void println(String msg) {
                listener.getLogger().println(msg);
                LOGGER.info(msg);
            }

            @Override
            public void error(String msg, Throwable ex) {
                listener.error(msg, ex);
                LOGGER.error(msg, ex);
            }
        };
        return tl;
    }

    /**
     * Tries to remove all trace of this node, logging anything that goes wrong.
     * <p>
     * Note: This is not intended for use outside the plugin.
     *
     * @param logger Where to log any progress messages, exceptions etc.
     */
    @Restricted(NoExternalUse.class)
    public void terminate(final Logger logger) {
        final ILogger tl = createILoggerForSLF4JLogger(logger);
        try {
            terminate(tl);
        } catch (Throwable ex) {
            tl.error("Failure while terminating '" + name + "':", ex);
        }
    }

    private static ILogger createILoggerForSLF4JLogger(final Logger logger) {
        final ILogger tl = new ILogger() {
            @Override
            public void println(String msg) {
                logger.info(msg);
            }

            @Override
            public void error(String msg, Throwable ex) {
                logger.error(msg, ex);
            }
        };
        return tl;
    }

    // terminate gets called multiple times, and the docker client logs noisy
    // exceptions if we try to stop or remove a container twice.
    private transient boolean containerStopped;
    private transient boolean containerRemoved;

    private void terminate(ILogger logger) {
        try {
            final Computer computer = toComputer();
            if (computer != null && !(computer.getOfflineCause() instanceof DockerOfflineCause)) {
                computer.disconnect(new DockerOfflineCause());
                logger.println("Disconnected computer for node '" + name + "'.");
            }
        } catch (Exception ex) {
            logger.error("Can't disconnect computer for node '" + name + "' due to exception:", ex);
        }

        final String ourContainerId = getContainerId();
        Computer.threadPoolForRemoting.submit(() -> {
            synchronized (DockerTransientNode.this) {
                if (containerRemoved) {
                    return; // nothing left to do here
                }
                final DockerAPI api;
                try {
                    api = getDockerAPI();
                } catch (RuntimeException ex) {
                    logger.error(
                            "Unable to stop and remove container '" + ourContainerId + "' for node '" + name
                                    + "' due to exception:",
                            ex);
                    return;
                }
                final boolean[] newValues = stopAndRemoveContainer(
                        api,
                        logger,
                        "for node '" + name + "'",
                        removeVolumes,
                        stopTimeout,
                        ourContainerId,
                        containerStopped);
                containerStopped = newValues[0];
                containerRemoved = newValues[1];
            }
        });

        try {
            Jenkins.get().removeNode(this);
            logger.println("Removed Node for node '" + name + "'.");
        } catch (IOException ex) {
            logger.error("Failed to remove Node for node '" + name + "' due to exception:", ex);
        }
    }

    /**
     * Removes a container, optionally stopping it first.
     *
     * @return pair of booleans, first is true if the container is not running,
     *         second is true if the container no longer exists.
     */
    private static boolean[] stopAndRemoveContainer(
            final DockerAPI api,
            final ILogger logger,
            final String containerDescription,
            final boolean removeVolumes,
            final int stopTimeout,
            final String containerId,
            final boolean containerAlreadyStopped) {
        boolean containerNowStopped = containerAlreadyStopped;
        boolean containerNowRemoved = false;

        try (final DockerClient client = api.getClient()) {
            if (!containerNowStopped) {
                client.stopContainerCmd(containerId)
                        .withTimeout(stopTimeout > 0 ? stopTimeout : DockerTemplate.DEFAULT_STOP_TIMEOUT)
                        .exec();
                containerNowStopped = true;
                logger.println("Stopped container '" + containerId + "' " + containerDescription + ".");
            }
        } catch (NotFoundException handledByCode) {
            logger.println(
                    "Can't stop container '" + containerId + "' " + containerDescription + " as it does not exist.");
            containerNowStopped = true;
            containerNowRemoved = true; // no point trying to remove the container if it's already gone.
        } catch (NotModifiedException handledByCode) {
            logger.println("Container '" + containerId + "' already stopped" + containerDescription + ".");
            containerNowStopped = true;
        } catch (Exception ex) {
            logger.error(
                    "Failed to stop container '" + containerId + "' " + containerDescription + " due to exception:",
                    ex);
        }

        try (final DockerClient client = api.getClient()) {
            if (!containerNowRemoved) {
                client.removeContainerCmd(containerId)
                        .withRemoveVolumes(removeVolumes)
                        .exec();
                containerNowRemoved = true;
                logger.println("Removed container '" + containerId + "' " + containerDescription + ".");
            }
        } catch (NotFoundException handledByCode) {
            logger.println("Container '" + containerId + "' already gone " + containerDescription + ".");
            containerNowRemoved = true;
        } catch (ConflictException handledByCode) {
            logger.println("Container '" + containerId + "' removal already in progress.");
            containerNowRemoved = true;
        } catch (Exception ex) {
            logger.error(
                    "Failed to remove container '" + containerId + "' " + containerDescription + " due to exception:",
                    ex);
        }
        return new boolean[] {containerNowStopped, containerNowRemoved};
    }

    /**
     * Utility method that gracefully terminates a docker container (preferably
     * one that we started). Intended to only be used when we do not have a
     * corresponding {@link DockerTransientNode} - if we have a
     * {@link DockerTransientNode} then call
     * {@link DockerTransientNode#terminate(Logger)} instead.
     *
     * @param api
     *            The {@link DockerAPI} which we are to use.
     * @param logger
     *            Where to log progress/results to.
     * @param containerDescription
     *            What the container was, e.g. "for node 'docker-1234'" or
     *            "for non-existent node". Used in logs.
     * @param removeVolumes
     *            If true then we'll ask docker to remove the container's
     *            volumes as well.
     * @param containerId
     *            The ID of the container to be terminated.
     * @param containerAlreadyStopped
     *            If true then we will assume that the container is already
     *            stopped and not try to stop it again (which helps prevent
     *            verbose warnings from appearing in the Jenkins log outside our
     *            control). If you're not sure, pass in false.
     * @return true if the container is now stopped and removed. false if we
     *         could not remove it (in which case we will have logged the reason
     *         why).
     */
    @Restricted(NoExternalUse.class)
    public static boolean stopAndRemoveContainer(
            final DockerAPI api,
            final Logger logger,
            final String containerDescription,
            final boolean removeVolumes,
            final String containerId,
            final boolean containerAlreadyStopped) {
        final ILogger tl = createILoggerForSLF4JLogger(logger);
        final boolean[] containerState = stopAndRemoveContainer(
                api,
                tl,
                containerDescription,
                removeVolumes,
                DockerTemplate.DEFAULT_STOP_TIMEOUT,
                containerId,
                containerAlreadyStopped);
        return containerState[1];
    }

    public DockerCloud getCloud() {
        if (cloudId == null) {
            return null;
        }
        final Cloud cloud = Jenkins.get().getCloud(cloudId);

        if (cloud == null) {
            throw new RuntimeException("Failed to retrieve Cloud " + cloudId);
        }

        if (!(cloud instanceof DockerCloud)) {
            throw new RuntimeException(cloudId + " is not a DockerCloud, it's a "
                    + cloud.getClass().toString());
        }

        return (DockerCloud) cloud;
    }

    /**
     * <i>Robustly</i> adds this node to Jenkins.
     * This works around for Jenkins core issue in Nodes.java's addNode method..
     * There's a line there saying
     * "<i>TODO there is a theoretical race whereby the node instance is
     * updated/removed after lock release</i>". When we're busy adding nodes
     * this is not merely "theoretical"!
     *
     * @see <a href=
     *      "https://github.com/jenkinsci/jenkins/blob/a0faeaee3cbcd2709b6ffb4491e31928b1d234d5/core/src/main/java/jenkins/model/Nodes.java#L148">
     *      Nodes.java addNode method</a>
     *
     * @throws IOException
     *             if it all failed horribly every time we tried.
     */
    @Restricted(NoExternalUse.class)
    public void robustlyAddToJenkins() throws IOException {
        // don't retry getInstance - fail immediately if that fails.
        final Jenkins jenkins = Jenkins.get();
        final int maxAttempts = 10;
        for (int attempt = 1; ; attempt++) {
            try {
                // addNode can fail at random due to a race condition.
                jenkins.addNode(this);
                return;
            } catch (IOException | RuntimeException ex) {
                if (attempt > maxAttempts) {
                    throw ex;
                }
                final long delayInMilliseconds = 10L * attempt;
                try {
                    Thread.sleep(delayInMilliseconds);
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
        }
    }

    /**
     * Reverse of {@link #robustlyAddToJenkins()}.
     *
     * @throws IOException
     *             if it all failed horribly.
     */
    @Restricted(NoExternalUse.class)
    public void robustlyRemoveFromJenkins() throws IOException {
        final Jenkins jenkins = Jenkins.get();
        // We think removeNode is reliable so no retries needed
        jenkins.removeNode(this);
    }

    @Extension
    public static final class DockerTransientNodeDescriptor extends SlaveDescriptor {

        @Override
        public String getDisplayName() {
            return "Docker Agent";
        }

        @Override
        public boolean isInstantiable() {
            return false;
        }
    }
}
