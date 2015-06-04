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
        return false;
    }

    @Override
    public ComputerLauncher getPreparedLauncher(DockerTemplate template, InspectContainerResponse containerInspectResponse) {
        return jnlpLauncher;
    }

    @Override
    void appendContainerConfig(DockerTemplateBase dockerTemplate, CreateContainerCmd createContainerCmd) {
        // jnlp command for connection with secret?
        createContainerCmd.withCmd("wget http://10.6.60.60:8090/jenkins/jnlpJars/slave.jar",
                "java -jar slave.jar -jnlpUrl http://10.6.60.60:8090/jenkins/computer/jnlp/slave-agent.jnlp");

    }

    @Override
    public boolean waitUp(DockerTemplate dockerTemplate, InspectContainerResponse ir) {
        // wait until container started?
        return true;
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
