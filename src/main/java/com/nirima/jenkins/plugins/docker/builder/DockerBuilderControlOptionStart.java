package com.nirima.jenkins.plugins.docker.builder;

import com.nirima.docker.client.DockerClient;
import com.nirima.docker.client.DockerException;
import hudson.Extension;
import hudson.model.AbstractBuild;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by magnayn on 30/01/2014.
 */
public class DockerBuilderControlOptionStart extends DockerBuilderControlOptionStopStart {

    @DataBoundConstructor
    public DockerBuilderControlOptionStart(String cloudId, String containerId) {
        super(cloudId, containerId);
    }

    @Override
    public void execute(AbstractBuild<?, ?> build) throws DockerException {

        LOGGER.info("Starting container " + containerId);
        DockerClient client = getClient(build);
        client.container(containerId).start();
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
