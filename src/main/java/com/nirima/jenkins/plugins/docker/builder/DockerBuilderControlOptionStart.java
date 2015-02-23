package com.nirima.jenkins.plugins.docker.builder;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerException;
import hudson.Extension;
import hudson.model.AbstractBuild;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by magnayn on 30/01/2014.
 */
public class DockerBuilderControlOptionStart extends DockerBuilderControlOptionStopStart {

    @DataBoundConstructor
    public DockerBuilderControlOptionStart(String cloudName, String containerId) {
        super(cloudName, containerId);
    }

    @Override
    public void execute(AbstractBuild<?, ?> build) throws DockerException {

        LOGGER.info("Starting container " + containerId);
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
