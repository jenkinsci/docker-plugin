package io.jenkins.docker.connector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.command.AttachContainerResultCallback;
import com.google.common.base.Throwables;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerSlave;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerComputerAttachConnector extends DockerComputerConnector {

    private String user;

    @DataBoundConstructor
    public DockerComputerAttachConnector() {
    }

    public String getUser() {
        return user;
    }

    @DataBoundSetter
    public void setUser(String user) {
        this.user = user;
    }

    @Override
    public void beforeContainerCreated(DockerCloud cloud, DockerTemplate template, CreateContainerCmd cmd) throws IOException, InterruptedException {
        ensureWaiting(cmd);
    }

    @Override
    public void afterContainerStarted(DockerCloud cloud, DockerTemplate template, String containerId) throws IOException, InterruptedException {
        final DockerClient client = cloud.getClient();
        injectRemotingJar(containerId, template.remoteFs, client);
    }

    @Override
    protected ComputerLauncher launch(DockerCloud cloud, DockerTemplate template, InspectContainerResponse inspect, TaskListener listener) throws IOException, InterruptedException {
        return new DockerAttachLauncher(cloud, inspect.getId(), user, template.remoteFs);
    }

    @Extension(ordinal = -1)
    public static class DescriptorImpl extends Descriptor<DockerComputerConnector> {

        @Override
        public String getDisplayName() {
            return "(Experimental) Attach Docker container";
        }
    }

    private static class DockerAttachLauncher extends ComputerLauncher {

        private final String cloudName;
        private final String containerId;
        private final String user;
        private final String remoteFs;

        private DockerAttachLauncher(DockerCloud cloud, String containerId, String user, String remoteFs) {
            this.cloudName = cloud.name;
            this.containerId = containerId;
            this.user = user;
            this.remoteFs = remoteFs;
        }

        public void launch(final SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
            final DockerCloud cloud = DockerCloud.getCloudByName(cloudName);

            final DockerClient client = cloud.getClient();

            computer.getListener().getLogger().println("Connecting to docker container "+containerId);

            final ExecCreateCmd cmd = client.execCreateCmd(containerId)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withTty(false)
                    .withCmd("java", "-jar", remoteFs + '/' + remoting.getName());

            if (StringUtils.isNotBlank(user)) {
                cmd.withUser(user);
            }

            final ExecCreateCmdResponse exec = cmd.exec();

            final PipedInputStream containerStdin = new PipedInputStream();
            final OutputStream out = new PipedOutputStream(containerStdin);

            final PipedOutputStream containerStdout = new PipedOutputStream();
            final InputStream in = new PipedInputStream(containerStdout);

            final AttachContainerResultCallback callback = new AttachContainerResultCallback() {
                public void onNext(Frame item) {
                    switch (item.getStreamType()) {
                        case STDERR:
                            computer.getListener().error(new String(item.getPayload(), StandardCharsets.UTF_8));
                            break;
                        case STDOUT:
                        case RAW:
                            try {
                                containerStdout.write(item.getPayload());
                            } catch (IOException e) {
                                throw new DockerException("Failed to collect stdout", 0, e);
                            }
                    }
                }
            };

            client.execStartCmd(exec.getId())
                    .withStdIn(containerStdin)
                    .exec(callback);

            computer.setChannel(in, out, computer.getListener(), new Channel.Listener() {
                @Override
                public void onClosed(Channel channel, IOException cause) {
                    try {
                        callback.close();
                    } catch (IOException e) {
                        Throwables.propagate(e);
                    }
                }
            });

        }

    }
}
