package com.nirima.jenkins.plugins.docker.launcher;

import hudson.Extension;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class AttachedDockerComputerLauncher extends DockerComputerLauncher {

    @DataBoundConstructor
    public AttachedDockerComputerLauncher() {
    }

    @Extension()
    public static class DescriptorImpl extends Descriptor<DockerComputerLauncher> {

        @Override
        public String getDisplayName() {
            return "Attach Docker container";
        }
    }
}
