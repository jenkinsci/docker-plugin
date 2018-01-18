package com.nirima.jenkins.plugins.docker.builder;

import com.github.dockerjava.api.exception.DockerException;
import com.nirima.jenkins.plugins.docker.action.DockerLaunchAction;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

/**
 * Stop all containers that ???
 *
 * @author magnayn
 */
public class DockerBuilderControlOptionStopAll extends DockerBuilderControlOption {
    private static final Logger LOG = LoggerFactory.getLogger(DockerBuilderControlOptionStopAll.class);

    public final boolean remove;

    @DataBoundConstructor
    public DockerBuilderControlOptionStopAll(boolean remove) {
        this.remove = remove;
    }

    @Override
    public void execute(Run<?, ?> build, Launcher launcher, TaskListener listener)
            throws DockerException {
        final PrintStream llog = listener.getLogger();

        LOG.info("Stopping all containers");
        llog.println("Stopping all containers");

        for (DockerLaunchAction.Item containerItem : getLaunchAction(build).getRunning()) {
            LOG.info("Stopping container {}", containerItem.id);
            llog.println("Stopping container " + containerItem.id);

            containerItem.client.stopContainerCmd(containerItem.id).exec();

            if (remove) {
                LOG.info("Removing container {}", containerItem.id);
                llog.println("Removing container " + containerItem.id);

                containerItem.client.removeContainerCmd(containerItem.id).exec();
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
