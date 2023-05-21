package io.jenkins.docker.connector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.thoughtworks.xstream.InitializationException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.Which;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.util.LogTaskListener;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Create a {@link DockerTransientNode} based on a template. Container is created in detached mode so it can survive
 * a jenkins restart (typically when Pipelines are used) then a launcher can re-connect. In many cases this
 * means container is running a dummy command as main process, then launcher is establish with `docker exec`.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class DockerComputerConnector extends AbstractDescribableImpl<DockerComputerConnector> {
    private static final Logger LOGGER = Logger.getLogger(DockerComputerConnector.class.getName());
    private static final TaskListener LOGGER_LISTENER = new LogTaskListener(LOGGER, Level.FINER);
    /** Name of the remoting jar file */
    protected static final File remoting;

    static {
        try {
            remoting = Which.jarFile(Channel.class);
        } catch (IOException e) {
            throw new InitializationException("Failed to resolve path to remoting.jar", e);
        }
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        return true;
    }

    /**
     * Called just before the container is created. Can provide some customization
     * to the container creation command.
     *
     * @param api     The {@link DockerAPI} that this container belongs to.
     * @param workdir The filesystem path to the Jenkins agent working directory.
     * @param cmd     The {@link CreateContainerCmd} that's about to be used.
     * @throws IOException          If anything goes wrong.
     * @throws InterruptedException If interrupted while doing things.
     */
    public void beforeContainerCreated(@NonNull DockerAPI api, @NonNull String workdir, @NonNull CreateContainerCmd cmd)
            throws IOException, InterruptedException {}

    /**
     * Called once the container has been created but not started yet, that's a good
     * opportunity to inject <code>remoting.jar</code> using
     * {@link #injectRemotingJar(String, String, DockerClient)}
     *
     * @param api     The {@link DockerAPI} that this container belongs to.
     * @param workdir The filesystem path to the Jenkins agent working directory.
     * @param node    The Jenkins node.
     * @throws IOException          If anything goes wrong.
     * @throws InterruptedException If interrupted while doing things.
     */
    public void beforeContainerStarted(
            @NonNull DockerAPI api, @NonNull String workdir, @NonNull DockerTransientNode node)
            throws IOException, InterruptedException {}

    /**
     * Called once the container has started. For some connection methods this can
     * be a good place to check it's healthy before considering agent is ready to
     * accept connections.
     *
     * @param api     The {@link DockerAPI} that this container belongs to.
     * @param workdir The filesystem path to the Jenkins agent working directory.
     * @param node    The Jenkins node.
     * @throws IOException          If anything goes wrong.
     * @throws InterruptedException If interrupted while doing things.
     */
    public void afterContainerStarted(
            @NonNull DockerAPI api, @NonNull String workdir, @NonNull DockerTransientNode node)
            throws IOException, InterruptedException {}

    /**
     * Ensure container is already set with a command, or set one to make it wait
     * indefinitely
     *
     * @param cmd The {@link CreateContainerCmd} to be adjusted.
     */
    protected void ensureWaiting(@NonNull CreateContainerCmd cmd) {
        final String[] cmdAlreadySet = cmd.getCmd();
        if (cmdAlreadySet == null || cmdAlreadySet.length == 0) {
            // no command has been set, we need one that will just hang. Typically "sh" waiting for stdin
            cmd.withCmd("/bin/sh").withTty(true).withAttachStdin(false);
        }
    }

    /**
     * Ensure that a DockerNode is known to Jenkins so that Jenkins will accept an
     * incoming JNLP connection etc.
     *
     * @param node The {@link DockerTransientNode} that's about to try connecting
     *             via JNLP.
     * @throws IOException if Jenkins is unable to persist the details.
     */
    protected void ensureNodeIsKnown(DockerTransientNode node) throws IOException {
        node.robustlyAddToJenkins();
    }

    /**
     * Utility method to copy remoting runtime into container on specified working
     * directory
     *
     * @param containerId The docker container ID
     * @param workdir     The filesystem path to the Jenkins agent working
     *                    directory.
     * @param client      The {@link DockerClient} for the cloud this container
     *                    belongs to.
     * @return The filesystem path to the remoting jar file.
     */
    protected String injectRemotingJar(
            @NonNull String containerId, @NonNull String workdir, @NonNull DockerClient client) {
        // Copy agent.jar into container
        client.copyArchiveToContainerCmd(containerId)
                .withHostResource(remoting.getAbsolutePath())
                .withRemotePath(workdir)
                .exec();
        return workdir + '/' + remoting.getName();
    }

    @Restricted(NoExternalUse.class)
    protected static void addEnvVars(
            @NonNull final EnvVars vars, @Nullable final Iterable<? extends NodeProperty<?>> nodeProperties)
            throws IOException, InterruptedException {
        if (nodeProperties != null) {
            for (final NodeProperty<?> nodeProperty : nodeProperties) {
                nodeProperty.buildEnvVars(vars, LOGGER_LISTENER);
            }
        }
    }

    @Restricted(NoExternalUse.class)
    protected static void addEnvVar(
            @NonNull final EnvVars vars, @NonNull final String name, @Nullable final Object valueOrNull) {
        vars.put(name, valueOrNull == null ? "" : valueOrNull.toString());
    }

    @NonNull
    public final ComputerLauncher createLauncher(
            @NonNull final DockerAPI api,
            @NonNull final String containerId,
            @NonNull String workdir,
            @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        final InspectContainerResponse inspect;
        try (final DockerClient client = api.getClient()) {
            inspect = client.inspectContainerCmd(containerId).exec();
        }
        final ComputerLauncher launcher = createLauncher(api, workdir, inspect, listener);

        final Boolean running = inspect.getState().getRunning();
        if (Boolean.FALSE.equals(running)) {
            listener.error(
                    "Container {} is not running. {}",
                    containerId,
                    inspect.getState().getStatus());
            throw new IOException("Container is not running.");
        }

        return new DockerDelegatingComputerLauncher(launcher, api, containerId);
    }

    /**
     * Create a Launcher to create an Agent with this container. Can assume
     * container has been created by this DockerAgentConnector so adequate setup did
     * take place.
     *
     * @param api      The {@link DockerAPI} for the cloud this agent is running on.
     * @param workdir  The filesystem path to the Jenkins agent working directory.
     * @param inspect  Information from the docker daemon about our container.
     * @param listener Where to output any issues.
     * @return A configured {@link ComputerLauncher}.
     * @throws IOException          If anything goes wrong, e.g. talking to docker.
     * @throws InterruptedException If we're interrupted while waiting.
     */
    @NonNull
    protected abstract ComputerLauncher createLauncher(
            @NonNull DockerAPI api,
            @NonNull String workdir,
            @NonNull InspectContainerResponse inspect,
            @NonNull TaskListener listener)
            throws IOException, InterruptedException;

    /**
     * @return all the registered {@link DockerComputerConnector} descriptors.
     */
    public static DescriptorExtensionList<DockerComputerConnector, Descriptor<DockerComputerConnector>> all() {
        final Jenkins j = Jenkins.get();
        return j.getDescriptorList(DockerComputerConnector.class);
    }
}
