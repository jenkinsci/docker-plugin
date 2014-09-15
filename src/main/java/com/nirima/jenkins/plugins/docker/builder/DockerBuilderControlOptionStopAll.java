package com.nirima.jenkins.plugins.docker.builder;

import com.nirima.docker.client.DockerException;
import com.nirima.jenkins.plugins.docker.action.DockerLaunchAction;
import hudson.Extension;
import hudson.model.AbstractBuild;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by magnayn on 30/01/2014.
 */
public class DockerBuilderControlOptionStopAll extends DockerBuilderControlOption {

    public final boolean remove;

    @DataBoundConstructor
    public DockerBuilderControlOptionStopAll(boolean remove) {

        this.remove = remove;
    }

    @Override
    public void execute(AbstractBuild<?, ?> build) throws DockerException {
        LOGGER.info("Stopping all containers");
        for(DockerLaunchAction.Item containerItem : getLaunchAction(build).getRunning()) {
            try {
                LOGGER.info("Stopping container " + containerItem.id);
                containerItem.client.container(containerItem.id).stop();

                if( remove )
                    containerItem.client.container(containerItem.id).remove();

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends DockerBuilderControlOptionDescriptor {
        @Override
        public String getDisplayName() {
            return "Stop All Containers";
        }

    }
}
