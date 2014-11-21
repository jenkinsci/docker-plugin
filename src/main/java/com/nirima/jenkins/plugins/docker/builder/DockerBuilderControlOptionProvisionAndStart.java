package com.nirima.jenkins.plugins.docker.builder;

import com.nirima.docker.client.DockerClient;
import com.nirima.docker.client.DockerException;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import hudson.Extension;
import hudson.model.AbstractBuild;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by magnayn on 30/01/2014.
 */
public class DockerBuilderControlOptionProvisionAndStart extends DockerBuilderControlCloudOption {
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
    public void execute(AbstractBuild<?, ?> build) throws DockerException {

        DockerTemplate template = getCloud(build).getTemplate(templateId);

        String containerId = template.provisionNew().getId();

        LOGGER.info("Starting container " + containerId);
        DockerClient client = getClient(build);
        
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
