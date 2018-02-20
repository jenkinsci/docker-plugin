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

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link Slave} node designed to be used only once for a build.
 * 
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerTransientNode extends Slave {

    //Keeping real containerId information, but using containerName as containerId
    private final String containerId;

    private final String containerName;

    private DockerAPI dockerAPI;

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

    public DockerAPI getDockerAPI() {
        return dockerAPI;
    }

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

    public void terminate(TaskListener listener) {
        try {
            toComputer().disconnect(new DockerOfflineCause());
            listener.getLogger().println("Disconnected computer");
        } catch (Exception e) {
            listener.error("Can't disconnect", e);
        }

        Computer.threadPoolForRemoting.submit(() -> {
            final String containerId = getContainerId();
            DockerClient client = dockerAPI.getClient();

            try {
                client.stopContainerCmd(containerId)
                        .withTimeout(10)
                        .exec();
                listener.getLogger().println("Stopped container "+ containerId);
            } catch(NotFoundException e) {
                listener.getLogger().println("Container already removed " + containerId);
            } catch (Exception ex) {
                listener.error("Failed to stop instance " + getContainerId() + " for slave " + name + " due to exception", ex.getMessage());
                listener.error("Causing exception for failure on stopping the instance was", ex);
            }

            try {
                client.removeContainerCmd(containerId)
                        .withRemoveVolumes(removeVolumes)
                        .exec();

                listener.getLogger().println("Removed container " + containerId);
            } catch (NotFoundException e) {
                listener.getLogger().println("Container already gone.");
            } catch (Exception ex) {
                listener.error("Failed to remove instance " + getContainerId() + " for slave " + name + " due to exception: " + ex.getMessage());
                listener.error("Causing exception for failre on removing instance was", ex);
            }
        });

        try {
            Jenkins.getInstance().removeNode(this);
        } catch (IOException e) {
            listener.error("Failed to remove Docker Node", e);
        }
    }

    public DockerCloud getCloud() {
        if (cloudId == null) return null;
        final Cloud cloud = Jenkins.getInstance().getCloud(cloudId);

        if (cloud == null) {
            throw new RuntimeException("Failed to retrieve Cloud " + cloudId);
        }

        if (cloud.getClass() != DockerCloud.class) {
            throw new RuntimeException(cloudId + " is not DockerCloud");
        }

        return (DockerCloud) cloud;
    }

}
