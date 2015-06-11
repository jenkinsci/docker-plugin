package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
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

    /**
     * Configured from UI
     */
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
        final DockerComputer dockerComputer = (DockerComputer) computer;
        final String containerId = dockerComputer.getContainerId();
        final String rootUrl = Jenkins.getInstance().getRootUrl();
        final DockerClient connect = dockerComputer.getCloud().connect();

        // exec jnlp connection in running container
        // TODO implement PID 1 replacement
        String cdCmd = "cd /home/jenkins/";
        String wgetSlaveCmd = "wget " + rootUrl + "jnlpJars/slave.jar -O slave.jar";
        String jnlpConnectCmd = "java -jar slave.jar "
                + "-jnlpUrl " + rootUrl + dockerComputer.getUrl() + "slave-agent.jnlp "
                + "-secret " + dockerComputer.getJnlpMac();

        String[] connectCmd = {
                "bash", "-c", cdCmd + " && " + wgetSlaveCmd + " && " + jnlpConnectCmd
        };

        LOGGER.info("Executing jnlp connection '{}' for '{}'", Arrays.toString(connectCmd), containerId);
        logger.println("Executing jnlp connection '" + Arrays.toString(connectCmd) + "' for '" + containerId + "'");
        try {
            final ExecCreateCmdResponse response = connect.execCreateCmd(containerId)
                    .withTty()
                    .withAttachStdin()
                    .withAttachStderr()
                    .withAttachStdout()
                    .withCmd(connectCmd)
                    .exec();

            connect.execStartCmd(response.getId())
                    .exec();
        } catch (Exception ex) {
            listener.error("Can't execute command: " + ex.getMessage());
            LOGGER.error("Can't execute jnlp connection command: '{}'", ex.getMessage());
        }

        LOGGER.info("Successfully executed jnlp connection for '{}'", containerId);
        logger.println("Successfully executed jnlp connection for " + containerId);
    }

    @Override
    public ComputerLauncher getPreparedLauncher(String cloudId, DockerTemplate template, InspectContainerResponse containerInspectResponse) {
        return new DockerComputerJNLPLauncher(getJnlpLauncher());
    }

    @Override
    void appendContainerConfig(DockerTemplateBase dockerTemplate, CreateContainerCmd createContainerCmd) {
        // set nop command, real jnlp connection will be called from #launch()
        createContainerCmd.withTty(true);
        createContainerCmd.withStdinOpen(true);
        createContainerCmd.withCmd("/bin/sh"); // nop
    }

    @Override
    public boolean waitUp(String cloudId, DockerTemplate dockerTemplate, InspectContainerResponse ir) {
        return super.waitUp(cloudId, dockerTemplate, ir);
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
