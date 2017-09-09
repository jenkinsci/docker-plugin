package io.jenkins.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.command.AttachContainerResultCallback;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerSlave;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.launcher.DockerComputerJNLPLauncher;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.Which;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class JNLPDockerSlaveProvisioner extends DockerSlaveProvisioner {

    private static final Logger LOGGER = LoggerFactory.getLogger(JNLPDockerSlaveProvisioner.class);


    private final DockerComputerJNLPLauncher launcher;
    private File remoting;
    private DockerSlave slave;

    public JNLPDockerSlaveProvisioner(DockerCloud cloud, DockerTemplate template, DockerClient client, DockerComputerJNLPLauncher launcher) {
        super(cloud, template, client);
        this.launcher = launcher;

    }

    @Override
    public DockerSlave provision() throws IOException, Descriptor.FormException, InterruptedException {

        this.slave = new DockerSlave(cloud, template, launcher.getJnlpLauncher());
        // register the slave so it get an active computer and we can inject jnlp connexion parameters as ENV
        Jenkins.getActiveInstance().addNode(slave);

        this.remoting = Which.jarFile(Channel.class);
        String id = runContainer();
        this.slave.getComputer().setContainerId(id);

        final InspectContainerResponse inspect = client.inspectContainerCmd(container).exec();
        if ("exited".equals(inspect.getState().getStatus())) {
            // Something went wrong

            // FIXME report error "somewhere" visible to end user.
            LOGGER.error("Failed to launch docker JNLP agent :" + inspect.getState().getExitCode());
        }

        return slave;
    }

    @Override
    protected void prepareCreateContainerCommand(CreateContainerCmd cmd) throws DockerException, IOException {
        final SlaveComputer computer = slave.getComputer();
        if (computer == null) {
            throw new IllegalStateException("DockerSlave hasn't been registered as an active Node");
        }

        List<String> args = new ArrayList<>();
        args.add("java");

        String vmargs = launcher.getJnlpLauncher().vmargs;
        if (StringUtils.isNotBlank(vmargs)) {
            args.addAll(Arrays.asList(vmargs.split(" ")));
        }

        args.addAll(Arrays.asList(
                "-jar", template.remoteFs + "/" + remoting.getName(),
                "-jnlpUrl", Jenkins.getInstance().getRootUrl() + '/' + computer.getUrl() + "/slave-agent.jnlp"));

        cmd.withCmd(args);
    }

    @Override
    protected void setupContainer() throws DockerException,  IOException {

        // Copy slave.jar into container
        client.copyArchiveToContainerCmd(container)
            .withHostResource(remoting.getAbsolutePath())
            .withRemotePath(template.remoteFs)
            .exec();


        // POST 2.9 we will be able to pipe container stdout/stderr to agent's log
        final TaskListener listener = TaskListener.NULL; // slave.getComputer().getListener();

        client.attachContainerCmd(container)
                .withStdErr(true)
                .withStdOut(true)
                .withLogs(true)
                .withFollowStream(true)
                .exec(new AttachContainerResultCallback() {
                    @Override
                    public void onNext(Frame item) {
                        switch (item.getStreamType()) {
                            case STDOUT:
                            case STDERR:
                                final String log = new String(item.getPayload(), StandardCharsets.UTF_8);
                                listener.getLogger().append(log);
                                System.out.printf(log);
                        }
                    }
                });
    }
}
