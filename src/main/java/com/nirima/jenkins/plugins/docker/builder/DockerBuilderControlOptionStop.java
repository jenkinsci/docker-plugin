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

    public final boolean remove;

    @DataBoundConstructor
    public DockerBuilderControlOptionStop(String cloudId, String containerId, boolean remove) {
        super(cloudId, containerId);
        this.remove = remove;
    }

    @Override
    public void execute(AbstractBuild<?, ?> build) throws DockerException {
        LOGGER.info("Stopping container " + containerId);
        DockerClient client = getClient(build);
        client.container(containerId).stop();
        getLaunchAction(build).stopped(client, containerId);
        if( remove )
            client.container(containerId).remove();
    }


    @Extension
    public static final class DescriptorImpl extends DockerBuilderControlOptionDescriptor {
        @Override
        public String getDisplayName() {
            return "Stop Container";
        }

    }
}
