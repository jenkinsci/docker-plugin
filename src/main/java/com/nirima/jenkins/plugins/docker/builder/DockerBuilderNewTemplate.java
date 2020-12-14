package com.nirima.jenkins.plugins.docker.builder;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.slaves.Cloud;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;

/**
 * Builder that adds template to all clouds.
 *
 * @author Jocelyn De La Rosa
 */
public class DockerBuilderNewTemplate extends Builder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerBuilderNewTemplate.class);

    private DockerTemplate dockerTemplate;
    @SuppressWarnings("unused")
    private int version = 1;

    @DataBoundConstructor
    public DockerBuilderNewTemplate(DockerTemplate dockerTemplate) {
        this.dockerTemplate = dockerTemplate;
    }

    public DockerTemplate getDockerTemplate() {
        return dockerTemplate;
    }

    public void setDockerTemplate(DockerTemplate dockerTemplate) {
        this.dockerTemplate = dockerTemplate;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        final PrintStream llogger = listener.getLogger();
        final String dockerImage = dockerTemplate.getDockerTemplateBase().getImage();
        // Job must run as Admin as we are changing global cloud configuration here.
        build.getACL().checkPermission(Jenkins.ADMINISTER);
        for (Cloud c : Jenkins.getInstance().clouds) {
            if (c instanceof DockerCloud && dockerImage != null) {
                DockerCloud dockerCloud = (DockerCloud) c;
                if (dockerCloud.getTemplate(dockerImage) == null) {
                    LOGGER.info("Adding new template: '{}', to cloud: '{}'", dockerImage, dockerCloud.name);
                    llogger.println("Adding new template: '" + dockerImage + "', to cloud: '" + dockerCloud.name + "'");
                    dockerCloud.addTemplate(dockerTemplate);
                }
            }
        }
        return true;
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
            return "Add a new template to all docker clouds";
        }
    }
}
