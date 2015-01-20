package com.nirima.jenkins.plugins.docker.publisher;

import com.github.dockerjava.api.DockerException;
import com.nirima.jenkins.plugins.docker.builder.DockerBuilderControlOptionStopAll;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Logger;

/**
 * Created by magnayn on 30/01/2014.
 */
public class DockerPublisherControl extends Recorder implements Serializable {
    private static final Logger LOGGER = Logger.getLogger(DockerPublisherControl.class.getName());

    public final boolean remove;

    @DataBoundConstructor
    public DockerPublisherControl(boolean remove)
    {
        this.remove = remove;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        try {
            new DockerBuilderControlOptionStopAll(remove).execute(build);
        } catch (DockerException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Stop Docker Containers";
        }
    }
}
