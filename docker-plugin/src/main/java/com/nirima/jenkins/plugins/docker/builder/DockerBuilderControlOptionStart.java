package com.nirima.jenkins.plugins.docker.builder;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

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
    public void execute(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws DockerException, IOException {
        LOG.info("Starting container {}", containerId);
        listener.getLogger().println("Starting container " + containerId);

        DockerClient client = getClient(build);
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
