package com.nirima.jenkins.plugins.docker.builder;

import com.nirima.docker.client.DockerClient;
import com.nirima.docker.client.DockerException;
import hudson.Extension;
import hudson.model.AbstractBuild;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by magnayn on 30/01/2014.
 */
public class DockerBuilderControlOptionStop extends DockerBuilderControlOptionStopStart {

    @DataBoundConstructor
    public DockerBuilderControlOptionStop(String cloudId, String containerId) {
        super(cloudId, containerId);
    }

    @Override
    public void execute(AbstractBuild<?, ?> build) throws DockerException {
        LOGGER.info("Stopping container " + containerId);
        DockerClient client = getClient(build);
        client.container(containerId).stop();
        getLaunchAction(build).stopped(client, containerId);
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends DockerBuilderControlOptionDescriptor {
        @Override
        public String getDisplayName() {
            return "Stop Container";
        }

    }
}
