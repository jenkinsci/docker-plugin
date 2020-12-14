package com.nirima.jenkins.plugins.docker.builder;

import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
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
            final String containerId = containerItem.id;
            LOG.info("Stopping container {}", containerId);
            llog.println("Stopping container " + containerId);

            containerItem.client.stopContainerCmd(containerId).exec();

            if (remove) {
                LOG.info("Removing container {}", containerId);
                llog.println("Removing container " + containerId);

                try {
                    containerItem.client.removeContainerCmd(containerId).exec();
                } catch (NotFoundException e) {
                    llog.println("Container '" + containerId + "' already gone.");
                } catch (ConflictException e) {
                    llog.println("Container '" + containerId + "' removal already in progress.");
                }
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
