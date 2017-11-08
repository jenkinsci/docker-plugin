package io.jenkins.docker.connector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerSlave;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.ComputerLauncherFilter;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerComputerJNLPConnector extends DockerComputerConnector {

    private String user;
    private final JNLPLauncher jnlpLauncher;

    @DataBoundConstructor
    public DockerComputerJNLPConnector(JNLPLauncher jnlpLauncher) {
        this.jnlpLauncher = jnlpLauncher;
    }


    public String getUser() {
        return user;
    }

    @DataBoundSetter
    public void setUser(String user) {
        this.user = user;
    }

    public JNLPLauncher getJnlpLauncher() {
        return jnlpLauncher;
    }


    @Override
    protected ComputerLauncher launch(final DockerCloud cloud, final DockerTemplate template, final InspectContainerResponse inspect, TaskListener listener) throws IOException, InterruptedException {
        return new DelegatingComputerLauncher(new JNLPLauncher()) {

            @Override
            public boolean isLaunchSupported() {
                return true;
            }

            @Override
            public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
                final DockerClient client = cloud.getClient();

                List<String> args = buildCommand(template, computer);

                final String containerId = inspect.getId();
                final ExecCreateCmd cmd = client.execCreateCmd(containerId)
                        .withAttachStdout(true)
                        .withTty(true)
                        .withCmd(args.toArray(new String[args.size()]));

                if (StringUtils.isNotBlank(user)) {
                    cmd.withUser(user);
                }

                final ExecCreateCmdResponse exec = cmd.exec();

                final PrintStream logger = computer.getListener().getLogger();
                final ExecStartResultCallback start = client.execStartCmd(exec.getId())
                        .withDetach(true)
                        .withTty(true)
                        .exec(new ExecStartResultCallback(logger, logger));

                start.awaitCompletion();
            }
        };

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

    private List<String> buildCommand(DockerTemplate template, SlaveComputer computer) {
        List<String> args = new ArrayList<>();
        args.add("java");

        String vmargs = jnlpLauncher.vmargs;
        if (StringUtils.isNotBlank(vmargs)) {
            args.addAll(Arrays.asList(vmargs.split(" ")));
        }

        args.addAll(Arrays.asList(
                "-cp", template.remoteFs + "/" + remoting.getName(),
                "hudson.remoting.jnlp.Main", "-headless"));
        if (StringUtils.isNotBlank(jnlpLauncher.tunnel)) {
            args.addAll(Arrays.asList("-tunnel", jnlpLauncher.tunnel));
        }

        args.addAll(Arrays.asList(
                "-url", Jenkins.getInstance().getRootUrl(),
                computer.getJnlpMac(),
                computer.getName()));
        return args;
    }


    @Extension
    public static final class DescriptorImpl extends Descriptor<DockerComputerConnector> {

        @Override
        public String getDisplayName() {
            return "Connect with JNLP";
        }

    }
}
