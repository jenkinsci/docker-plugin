package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.NotFoundException;
import com.github.dockerjava.api.command.*;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;

/**
 * JNLP launcher. Doesn't require open ports on docker host.
 *
 * Steps:
 * - runs container with nop command
 * - as launch action executes jnlp connection to master
 *
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

        final InputStream resourceAsStream = DockerComputerJNLPLauncher.class.getResourceAsStream("init.sh");

        String cdCmd = "cd /home/jenkins/";
        String wgetSlaveCmd = "wget " + rootUrl + "jnlpJars/slave.jar -O slave.jar";
        String jnlpConnectCmd = "java -jar slave.jar "
                + "-jnlpUrl " + rootUrl + dockerComputer.getUrl() + "slave-agent.jnlp ";
//                + "-secret " + dockerComputer.getJnlpMac();

        String[] connectCmd = {
                "bash", "-c", cdCmd + " && " + wgetSlaveCmd + " && " + jnlpConnectCmd
        };

        try {
            LOGGER.info("Creating jnlp connection command '{}' for '{}'", Arrays.toString(connectCmd), containerId);
            logger.println("Creating jnlp connection command '" + Arrays.toString(connectCmd) + "' for '" + containerId + "'");

            final ExecCreateCmdResponse response = connect.execCreateCmd(containerId)
                    .withTty()
                    .withAttachStdin()
                    .withAttachStderr()
                    .withAttachStdout()
                    .withCmd(connectCmd)
                    .exec();

            LOGGER.info("Starting connection command for {}", containerId);
            logger.println("Starting connection command for " + containerId);

            try (InputStream exec = connect.execStartCmd(response.getId()).withDetach().withTty().exec()){
                //nothing, just want to be closed
            } catch (NotFoundException ex) {
                listener.error("Can't execute command: " + ex.getMessage());
                LOGGER.error("Can't execute jnlp connection command: '{}'", ex.getMessage());
            }

        } catch (Exception ex) {
            listener.error("Can't execute command: " + ex.getMessage());
            LOGGER.error("Can't execute jnlp connection command: '{}'", ex.getMessage());
            throw ex;
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


    @Restricted(NoExternalUse.class)
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        return DESCRIPTOR;
    }

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
