package io.jenkins.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.command.AttachContainerResultCallback;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerSlave;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.Which;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * Create a container in interactive mode and use attached stdin/stdout as transport for remoting.
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class AttachedDockerSlaveProvisioner extends DockerSlaveProvisioner {


    private DockerSlave slave;

    private File remoting;

    public AttachedDockerSlaveProvisioner(DockerCloud cloud, DockerTemplate template, DockerClient client) {
        super(cloud, template, client);
    }

    @Override
    public DockerSlave provision() throws IOException, Descriptor.FormException, InterruptedException {

        this.remoting = Which.jarFile(Channel.class);
        this.slave = new DockerSlave(cloud, template, new NoOpLauncher());
        Jenkins.getInstance().addNode(slave);
        runContainer();
        return slave;
    }

    @Override
    protected void prepareCreateContainerCommand(CreateContainerCmd cmd) throws DockerException, IOException {
        cmd.withAttachStdin(true)
            .withAttachStdout(true)
            .withCmd("java", "-jar", template.remoteFs + '/' + remoting.getName());
    }

    @Override
    protected void setupContainer() throws IOException, InterruptedException {

        // Copy slave.jar into container
        client.copyArchiveToContainerCmd(container)
                .withHostResource(remoting.getAbsolutePath())
                .withRemotePath(template.remoteFs)
                .exec();

        final PipedInputStream containerStdin = new PipedInputStream();

        final PipedOutputStream containerStdout = new PipedOutputStream();

        client.attachContainerCmd(container)
                .withStdOut(true)
                .withStdErr(true)
                .withStdIn(containerStdin)
                .withFollowStream(true)
                .withLogs(true)
                .exec(new AttachContainerResultCallback() {
                    public void onNext(Frame item) {
                        switch (item.getStreamType()) {
                            case STDOUT:
                            case RAW:
                                try {
                                    containerStdout.write(item.getPayload());
                                } catch (IOException e) {
                                    throw new DockerException("Failed to collect stdout", 0, e);
                                }
                        }
                    }
                });

        slave.getComputer().setChannel(new PipedInputStream(containerStdout), new PipedOutputStream(containerStdin), TaskListener.NULL, new Channel.Listener() {
            @Override
            public void onClosed(Channel channel, IOException cause) {
                System.out.println("Closed");
            }
        });
    }

    private static class NoOpLauncher extends ComputerLauncher {

        public void launch(SlaveComputer computer, TaskListener listener) {
            // Nop
        }

    }
}
