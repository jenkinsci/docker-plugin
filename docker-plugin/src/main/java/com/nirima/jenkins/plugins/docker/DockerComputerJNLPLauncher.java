package com.nirima.jenkins.plugins.docker;

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
        return true;
    }

    @Override
    public ComputerLauncher makeLauncher(DockerTemplate template, InspectContainerResponse containerInspectResponse) {
        return null;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) {

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
