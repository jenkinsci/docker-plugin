package com.nirima.jenkins.plugins.docker.builder;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.DockerException;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.docker.client.DockerAPI;
import java.io.IOException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts container in DockerCloud
 *
 * @author by magnayn
 */
public class DockerBuilderControlOptionStart extends DockerBuilderControlOptionStopStart {
    private static final Logger LOG = LoggerFactory.getLogger(DockerBuilderControlOptionStart.class);

    @DataBoundConstructor
    public DockerBuilderControlOptionStart(String cloudName, String containerId) {
        super(cloudName, containerId);
    }

    @Override
    public void execute(Run<?, ?> build, Launcher launcher, TaskListener listener)
            throws DockerException {
        LOG.info("Starting container {}", containerId);
        listener.getLogger().println("Starting container " + containerId);

        final DockerCloud cloud = getCloud(build,launcher);
        final DockerAPI dockerApi = cloud.getDockerApi();
        try(final DockerClient client = dockerApi.getClient()) {
            executeOnDocker(build, client);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void executeOnDocker(Run<?, ?> build, DockerClient client) {
        client.startContainerCmd(containerId).exec();

        getLaunchAction(build).started(client, containerId);
    }

    @Extension
    public static final class DescriptorImpl extends DockerBuilderControlOptionDescriptor {
        @Override
        public String getDisplayName() {
            return "Start Container";
        }
    }
}
