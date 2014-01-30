package com.nirima.jenkins.plugins.docker.builder;

import com.kpelykh.docker.client.DockerClient;
import com.kpelykh.docker.client.DockerException;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by magnayn on 30/01/2014.
 */
public class DockerBuilderControlOptionProvisionAndStart extends DockerBuilderControlCloudOption {
    private final String templateId;

    @DataBoundConstructor
    public DockerBuilderControlOptionProvisionAndStart(String cloudId, String templateId) {
        super(cloudId);
        this.templateId = templateId;
    }

    @Override
    public void execute(AbstractBuild<?, ?> build) throws DockerException {

        DockerTemplate template = getCloud(build).getTemplate(templateId);

        String containerId = template.provisionNew().getId();

        LOGGER.info("Starting container " + containerId);
        DockerClient client = getClient(build);
        client.startContainer(containerId);
        getLaunchAction(build).started(client, containerId);
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends DockerBuilderControlOptionDescriptor {
        @Override
        public String getDisplayName() {
            return "Provision & Start Container";
        }

    }
}
