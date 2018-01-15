package io.jenkins.docker.connector;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.core.command.WaitContainerResultCallback;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import io.jenkins.docker.client.DockerAPI;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerComputerJNLPConnector extends DockerComputerConnector {

    private String user;
    private final JNLPLauncher jnlpLauncher;
    private String jenkinsUrl;

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

    public String getJenkinsUrl(){ return jenkinsUrl; }

    @DataBoundSetter
    public void setJenkinsUrl(String jenkinsUrl){ this.jenkinsUrl = jenkinsUrl; }

    public DockerComputerJNLPConnector withUser(String user) {
        this.user = user;
        return this;
    }

    public DockerComputerJNLPConnector withJenkinsUrl(String jenkinsUrl) {
        this.jenkinsUrl = jenkinsUrl;
        return this;
    }

    public JNLPLauncher getJnlpLauncher() {
        return jnlpLauncher;
    }


    @Override
    protected ComputerLauncher createLauncher(final DockerAPI api, final String workdir, final DockerTemplate.ContainerCommandCreator containerCommandCreator, TaskListener listener) throws IOException, InterruptedException {
        return new DelegatingComputerLauncher(new JNLPLauncher()) {

            @Override
            public boolean isLaunchSupported() {
                return true;
            }

            @Override
            public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
                final CreateContainerCmd cmd = containerCommandCreator.createContainerCmd(api);
                List<String> args = buildCommand(computer);
                cmd.withCmd(args.toArray(new String[args.size()]));
                String vmargs = jnlpLauncher.vmargs;
                if (StringUtils.isNotBlank(vmargs)) {
                    cmd.withEnv("JAVA_OPT=" + vmargs.trim());
                }
                if (StringUtils.isNotBlank(user)) {
                    cmd.withUser(user);
                }

                final InspectContainerResponse inspect = executeContainer(api, listener, cmd, workdir);
                final String containerId = inspect.getId();
                api.getClient().waitContainerCmd(containerId)
                        .exec(new WaitContainerResultCallback());
            }
        };

    }

    @Override
    public void beforeContainerCreated(DockerAPI api, String workdir, CreateContainerCmd cmd) throws IOException, InterruptedException {
    }

    @Override
    public void afterContainerStarted(DockerAPI api, String workdir, String containerId) throws IOException, InterruptedException {
    }

    private List<String> buildCommand(SlaveComputer computer) {
        List<String> args = new ArrayList<>();
        if (StringUtils.isNotBlank(jnlpLauncher.tunnel)) {
            args.addAll(Arrays.asList("-tunnel", jnlpLauncher.tunnel));
        }

        args.addAll(Arrays.asList(
                "-url", StringUtils.isEmpty(jenkinsUrl) ? Jenkins.getInstance().getRootUrl() : jenkinsUrl,
                computer.getJnlpMac(),
                computer.getName()));
        return args;
    }

    @Extension @Symbol("jnlp")
    public static final class DescriptorImpl extends Descriptor<DockerComputerConnector> {

        @Override
        public String getDisplayName() {
            return "Connect with JNLP";
        }

    }
}
