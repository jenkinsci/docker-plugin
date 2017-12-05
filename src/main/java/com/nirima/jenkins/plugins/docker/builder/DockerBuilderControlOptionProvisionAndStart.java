package com.nirima.jenkins.plugins.docker.builder;


import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.DockerException;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

/**
 * Build step that provision? container in Docker Cloud
 *
 * @author  magnayn
 */
public class DockerBuilderControlOptionProvisionAndStart extends DockerBuilderControlCloudOption {
    private static final Logger LOG = LoggerFactory.getLogger(DockerBuilderControlOptionProvisionAndStart.class);

    private final String templateId;

    @DataBoundConstructor
    public DockerBuilderControlOptionProvisionAndStart(String cloudName, String templateId) {
        super(cloudName);
        this.templateId = templateId;
    }

    public String getTemplateId() {
        return templateId;
    }

    @Override
    public void execute(Run<?, ?> build, Launcher launcher, TaskListener listener)
            throws DockerException {
        final PrintStream llog = listener.getLogger();

        final DockerCloud cloud = getCloud(build, launcher);
        DockerTemplate template = cloud.getTemplate(templateId);
        DockerClient client = cloud.getClient();
        String containerId = DockerCloud.runContainer(template.getDockerTemplateBase(), client);

        LOG.info("Starting container {}, cloud {}", containerId, cloud.getDisplayName());
        llog.println("Starting container " + containerId + ", cloud " + cloud.getDisplayName());

        getLaunchAction(build).started(client, containerId);
    }

    @Extension
    public static final class DescriptorImpl extends DockerBuilderControlOptionDescriptor {
        @Override
        public String getDisplayName() {
            return "Provision & Start Container";
        }
    }
}
