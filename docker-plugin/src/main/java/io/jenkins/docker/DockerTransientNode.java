package io.jenkins.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerOfflineCause;
import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Queue;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import io.jenkins.docker.client.DockerAPI;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.io.Serializable;

/**
 * A {@link Slave} node designed to be used only once for a build.
 * 
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerTransientNode extends Slave {
    
    private final String containerId;
    
    private DockerAPI dockerAPI;

    private boolean removeVolumes;

    private String cloudId;

    private Callback callback;

    public DockerTransientNode(String containerId, String remoteFS, ComputerLauncher launcher) throws Descriptor.FormException, IOException {
        super("docker-" + containerId.substring(0,12), remoteFS, launcher);
        setNumExecutors(1);
        setMode(Mode.EXCLUSIVE);
        setRetentionStrategy(new DockerOnceRetentionStrategy(10));
        this.containerId = containerId;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setDockerAPI(DockerAPI dockerAPI) {
        this.dockerAPI = dockerAPI;
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

    public void setCallback(Callback callback) {
        this.callback = callback;
    }



    @Override
    public DockerComputer createComputer() {
        return new DockerComputer(this);
    }

    public void terminate(TaskListener listener, Queue.Executable exec) {
        try {
            toComputer().disconnect(new DockerOfflineCause());
            listener.getLogger().println("Disconnected computer");
        } catch (Exception e) {
            listener.error("Can't disconnect", e);
        }

        if (containerId != null) {
            Computer.threadPoolForRemoting.submit(() -> {

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

                callback.onCompletion(this, exec);

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

        } else {
            listener.error("ContainerId is absent, no way to remove/stop container");
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

    public static interface Callback extends Serializable {

        /**
         * Callback to get notified as container has been stopped and will be removed.
         * The {@link Queue.Executable} assigned to this {@link DockerTransientNode} si also passed, but may be
         * <code>null</code> as something went wrong during provisioning.
         */
        void onCompletion(DockerTransientNode dockerTransientNode, @CheckForNull Queue.Executable exec);
    }


}
