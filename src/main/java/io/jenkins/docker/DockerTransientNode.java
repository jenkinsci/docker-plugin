package io.jenkins.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerOfflineCause;
import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import io.jenkins.docker.client.DockerAPI;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link Slave} node designed to be used only once for a build.
 * 
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerTransientNode extends Slave {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerTransientNode.class.getName());

    //Keeping real containerId information, but using containerName as containerId
    private final String containerId;

    private final String containerName;

    private transient DockerAPI dockerAPI;

    private boolean removeVolumes;

    private String cloudId;

    private AtomicBoolean acceptingTasks = new AtomicBoolean(true);

    public DockerTransientNode(@Nonnull String uid, String containerId, String workdir, ComputerLauncher launcher) throws Descriptor.FormException, IOException {
        super(nodeName(uid), workdir, launcher);
        this.containerName = uid;
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

    public static String nodeName(@Nonnull String containerName) {
        return "docker-" + containerName;
    }

    public String getContainerId(){
        return containerId;
    }

    public String getContainerName() {
        return containerName;
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

    public String getCloudId() {
        return cloudId;
    }

    public void setCloudId(String cloudId) {
        this.cloudId = cloudId;
    }

    @Override
    public DockerComputer createComputer() {
        return new DockerComputer(this);
    }

    private interface ILogger {
        void println(String msg);

        void error(String msg, Throwable ex);
    }

    public void terminate(final TaskListener listener) {
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
        try {
            terminate(tl);
        } catch (Throwable ex) {
            tl.error("Failure while terminating '" + name + "':", ex);
        }
    }

    /**
     * Tries to remove all trace of this node, logging anything that goes wrong.
     * <p>
     * Note: This is not intended for use outside the plugin.
     */
    @Restricted(NoExternalUse.class)
    public void terminate(final Logger logger) {
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
        try {
            terminate(tl);
        } catch (Throwable ex) {
            tl.error("Failure while terminating '" + name + "':", ex);
        }
    }

    private void terminate(ILogger logger) {
        try {
            final Computer computer = toComputer();
            if (computer != null) {
                computer.disconnect(new DockerOfflineCause());
                logger.println("Disconnected computer for slave '" + name + "'.");
            }
        } catch (Exception ex) {
            logger.error("Can't disconnect computer for slave '" + name + "' due to exception:", ex);
        }

        final String containerId = getContainerId();
        Computer.threadPoolForRemoting.submit(() -> {
            final DockerAPI api;
            try {
                api = getDockerAPI();
            } catch (RuntimeException ex) {
                logger.error("Unable to stop and remove container '" + containerId + "' for slave '" + name + "' due to exception:", ex);
                return;
            }

            try(final DockerClient client = api.getClient()) {
                client.stopContainerCmd(containerId)
                        .withTimeout(10)
                        .exec();
                logger.println("Stopped container '"+ containerId + "' for slave '" + name + "'.");
            } catch(NotFoundException e) {
                logger.println("Can't stop container '" + containerId + "' for slave '" + name + "' as it does not exist.");
                return; // no point trying to remove the container if it's already gone.
            } catch (Exception ex) {
                logger.error("Failed to stop container '" + containerId + "' for slave '" + name + "' due to exception:", ex);
            }

            try(final DockerClient client = api.getClient()) {
                client.removeContainerCmd(containerId)
                        .withRemoveVolumes(removeVolumes)
                        .exec();
                logger.println("Removed container '" + containerId + "' for slave '" + name + "'.");
            } catch (NotFoundException e) {
                logger.println("Container '" + containerId + "' already gone for slave '" + name + "'.");
            } catch (Exception ex) {
                logger.error("Failed to remove container '" + containerId + "' for slave '" + name + "' due to exception:", ex);
            }
        });

        try {
            Jenkins.getInstance().removeNode(this);
            logger.println("Removed Node for slave '" + name + "'.");
        } catch (IOException ex) {
            logger.error("Failed to remove Node for slave '" + name + "' due to exception:", ex);
        }
    }

    public DockerCloud getCloud() {
        if (cloudId == null) return null;
        final Cloud cloud = Jenkins.getInstance().getCloud(cloudId);

        if (cloud == null) {
            throw new RuntimeException("Failed to retrieve Cloud " + cloudId);
        }

        if (!(cloud instanceof DockerCloud)) {
            throw new RuntimeException(cloudId + " is not a DockerCloud, it's a " + cloud.getClass().toString());
        }

        return (DockerCloud) cloud;
    }

}
