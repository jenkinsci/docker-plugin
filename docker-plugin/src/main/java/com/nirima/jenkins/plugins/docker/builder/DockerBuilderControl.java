package com.nirima.jenkins.plugins.docker.builder;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.Serializable;

/**
 * Builder that contains one of the possible "option" control step.
 * @author magnayn
 */
public class DockerBuilderControl extends Builder implements Serializable, SimpleBuildStep {
    public final DockerBuilderControlOption option;

    @DataBoundConstructor
    public DockerBuilderControl(DockerBuilderControlOption option) {
        this.option = option;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        option.execute(run, launcher, listener);
        run.save();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Start/Stop Docker Containers";
        }

        public static DescriptorExtensionList<DockerBuilderControlOption,DockerBuilderControlOptionDescriptor> getOptionList() {
            return DockerBuilderControlOptionDescriptor.all();
        }
    }
}
