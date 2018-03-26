package com.nirima.jenkins.plugins.docker.builder;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.DockerException;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.docker.client.DockerAPI;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
        final DockerTemplate template = cloud.getTemplate(templateId);
        final DockerAPI dockerApi = cloud.getDockerApi();
        try(final DockerClient client = dockerApi.getClient()) {
            executeOnDocker(build, llog, cloud, template, client);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void executeOnDocker(Run<?, ?> build, PrintStream llog, DockerCloud cloud, DockerTemplate template, DockerClient client)
            throws DockerException {
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
