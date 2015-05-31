package com.nirima.jenkins.plugins.docker.builder;


import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerException;
import com.nirima.jenkins.plugins.docker.DockerSlave;
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
        DockerClient client = getClient(build);
        String containerId = DockerSlave.runContainer(template, client);

        LOGGER.info("Starting container " + containerId);

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
