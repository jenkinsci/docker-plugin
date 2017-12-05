package io.jenkins.docker.connector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.nirima.jenkins.plugins.docker.DockerSlave;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.thoughtworks.xstream.InitializationException;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.Which;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

/**
 * Create a {@link DockerSlave} based on a template. Container is created in detached mode so it can survive
 * a jenkins restart (typically when Pipelines are used) then a launcher can re-connect. In many cases this
 * means container is running a dummy command as main process, then launcher is establish with `docker exec`.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class DockerComputerConnector extends AbstractDescribableImpl<DockerComputerConnector> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerComputerConnector.class);

    protected static final File remoting;

    static {
        try {
            remoting = Which.jarFile(Channel.class);
        } catch (IOException e) {
            throw new InitializationException("Failed to resolve path to remoting.jar");
        }
    }

    /**
     * Can be overridden by concrete implementations to provide some customization to the container creation command
     */
    public void beforeContainerCreated(DockerAPI api, String workdir, CreateContainerCmd cmd) throws IOException, InterruptedException {}

    /**
     * Container has been created but not started yet, that's a good opportunity to inject <code>remoting.jar</code>
     * using {@link #injectRemotingJar(String, String, DockerClient)}
     */
    public void beforeContainerStarted(DockerAPI api, String workdir, String containerId) throws IOException, InterruptedException {}

    /**
     * Container has started. Good place to check it's healthy before considering agent is ready to accept connexions
     */
    public void afterContainerStarted(DockerAPI api, String workdir, String containerId) throws IOException, InterruptedException {}


    /**
     * Ensure container is already set with a command, or set one to make it wait indefinitely
     */
    protected void ensureWaiting(CreateContainerCmd cmd) {
        if (cmd.getCmd() == null || cmd.getCmd().length == 0) {
            // no command has been set, we need one that will just hang. Typically "sh" waiting for stdin
            cmd.withCmd("/bin/sh")
               .withTty(true)
               .withAttachStdin(false);

        }
    }

    /**
     * Utility method to copy remoting runtime into container on specified working directory
     */
    protected String injectRemotingJar(String containerId, String workdir, DockerClient client) throws IOException {

        // Copy slave.jar into container
        client.copyArchiveToContainerCmd(containerId)
                .withHostResource(remoting.getAbsolutePath())
                .withRemotePath(workdir)
                .exec();

        return workdir + '/' + remoting.getName();
    }

    public final ComputerLauncher createLauncher(final DockerAPI api, @Nonnull final String containerId, String workdir, TaskListener listener) throws IOException, InterruptedException {

        final InspectContainerResponse inspect = api.getClient().inspectContainerCmd(containerId).exec();
        final Boolean running = inspect.getState().getRunning();
        if (Boolean.FALSE.equals(running)) {
            listener.error("Container {} is not running. {}", containerId, inspect.getState().getStatus());
            throw new IOException("Container is not running.");
        }

        final ComputerLauncher launcher = createLauncher(api, workdir, inspect, listener);
        return new DelegatingComputerLauncher(launcher) {

            @Override
            public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
                try {
                    api.getClient().inspectContainerCmd(containerId).exec();
                } catch (NotFoundException e) {
                    // Container has been removed
                    Queue.withLock( () -> {
                        DockerTransientNode node = (DockerTransientNode) computer.getNode();
                        node.terminate(listener);
                    });
                    return;
                }
                super.launch(computer, listener);
            }
        };
    }

    /**
     * Create a Launcher to create an Agent with this container. Can assume container has been created by this
     * DockerAgentConnector so adequate setup did take place.
     */
    protected abstract ComputerLauncher createLauncher(DockerAPI api, String workdir, InspectContainerResponse inspect, TaskListener listener) throws IOException, InterruptedException;

}
