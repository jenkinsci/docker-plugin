package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerComputerJNLPLauncher extends DockerComputerLauncher {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerComputerJNLPLauncher.class);


    protected JNLPLauncher jnlpLauncher;

    @DataBoundConstructor
    public DockerComputerJNLPLauncher(JNLPLauncher jnlpLauncher) {
        this.jnlpLauncher = jnlpLauncher;
    }

    public JNLPLauncher getJnlpLauncher() {
        return jnlpLauncher;
    }

    @Override
    public boolean isLaunchSupported() {
        return true;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        final PrintStream logger = listener.getLogger();
        logger.println("Trying to launch jnlp connection");

        final DockerComputer dockerComputer = (DockerComputer) computer;
        final String containerId = dockerComputer.getContainerId();
        final String rootUrl = Jenkins.getInstance().getRootUrl();

        String cdCmd = "cd /home/jenkins/";
        String wgetSlaveCmd = "wget " + rootUrl + "jnlpJars/slave.jar -O slave.jar";
        String jnlpCmd = "java -jar slave.jar -jnlpUrl " + rootUrl + dockerComputer.getUrl() + "slave-agent.jnlp";

        final ExecCreateCmd execCreateCmd = dockerComputer.getCloud().connect().execCreateCmd(containerId);
        String[] connectCmd = {
                "bash", "-c", cdCmd + " && " + wgetSlaveCmd + " && " + jnlpCmd
        };

        LOGGER.info("Executing: {}", Arrays.toString(connectCmd));
        try {
            execCreateCmd.withTty()
                    .withAttachStdin()
                    .withAttachStderr()
                    .withAttachStdout()
                    .withCmd(connectCmd)
                    .exec();
        } catch (Exception ex) {
            listener.error("Can't execute command");
            LOGGER.error("Can't execute jnlp connection command {}", ex.getMessage());
        }

    }

    @Override
    public ComputerLauncher getPreparedLauncher(DockerTemplate template, InspectContainerResponse containerInspectResponse) {
        return new DockerComputerJNLPLauncher(getJnlpLauncher());
    }

    @Override
    void appendContainerConfig(DockerTemplateBase dockerTemplate, CreateContainerCmd createContainerCmd) {
        // jnlp command for connection with secret?
        final String rootUrl = Jenkins.getInstance().getRootUrl();

        createContainerCmd.withTty(true);
        createContainerCmd.withStdinOpen(true);
        createContainerCmd.withCmd("/bin/sh"); //nop
    }

    @Override
    public boolean waitUp(DockerTemplate dockerTemplate, InspectContainerResponse ir) {
        return super.waitUp(dockerTemplate, ir);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {

        public Class getJNLPLauncher() {
            return JNLPLauncher.class;
        }

        @Override
        public String getDisplayName() {
            return "Docker JNLP agent";
        }
    }
}
