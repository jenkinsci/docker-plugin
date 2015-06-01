package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerComputerJNLPLauncher extends DockerComputerLauncher {

    public JNLPLauncher jnlpLauncher;

    @DataBoundConstructor
    public DockerComputerJNLPLauncher(JNLPLauncher jnlpLauncher) {
        this.jnlpLauncher = jnlpLauncher;
    }

    @Override
    public boolean isLaunchSupported() {
        return false;
    }

    @Override
    public ComputerLauncher makeLauncher(DockerTemplate template, InspectContainerResponse containerInspectResponse) {
        return null;
    }

    @Override
    void appendContainerConfig(CreateContainerCmd createContainerCmd) {
        // jnlp command for connection?
        createContainerCmd.withCmd("wget http://10.6.60.60:8090/jenkins/jnlpJars/slave.jar",
                "java -jar slave.jar -jnlpUrl http://10.6.60.60:8090/jenkins/computer/jnlp/slave-agent.jnlp");
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) {
        // launch container


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
