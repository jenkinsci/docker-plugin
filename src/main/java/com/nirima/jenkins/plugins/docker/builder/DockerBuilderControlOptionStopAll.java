package com.nirima.jenkins.plugins.docker.builder;

import com.kpelykh.docker.client.DockerException;
import com.nirima.jenkins.plugins.docker.action.DockerLaunchAction;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by magnayn on 30/01/2014.
 */
public class DockerBuilderControlOptionStopAll extends DockerBuilderControlOption {

    @DataBoundConstructor
    public DockerBuilderControlOptionStopAll() {

    }

    @Override
    public void execute(AbstractBuild<?, ?> build) throws DockerException {
        LOGGER.info("Stopping all containers");
        for(DockerLaunchAction.Item containerItem : getLaunchAction(build).getRunning()) {
            try {
                LOGGER.info("Stopping container " + containerItem.id);
                containerItem.client.stopContainer(containerItem.id);
            } catch (DockerException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends DockerBuilderControlOptionDescriptor {
        @Override
        public String getDisplayName() {
            return "Stop All Containers";
        }

    }
}
