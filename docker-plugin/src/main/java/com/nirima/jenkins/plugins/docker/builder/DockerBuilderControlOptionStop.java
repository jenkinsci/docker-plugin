package com.nirima.jenkins.plugins.docker.builder;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotModifiedException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

/**
 * Build step that stops container in DockerCloud
 *
 * @author magnayn
 */
public class DockerBuilderControlOptionStop extends DockerBuilderControlOptionStopStart {
    private static final Logger LOG = LoggerFactory.getLogger(DockerBuilderControlOptionStop.class);

    public final boolean remove;

    @DataBoundConstructor
    public DockerBuilderControlOptionStop(String cloudName, String containerId, boolean remove) {
        super(cloudName, containerId);
        this.remove = remove;
    }

    @Override
    public void execute(Run<?, ?> build, Launcher launcher, TaskListener listener)
            throws DockerException {
        final PrintStream llog = listener.getLogger();
        LOG.info("Stopping container " + containerId);
        llog.println("Stopping container " + containerId);

        DockerClient client = getCloud(build, launcher).getClient();
        try {
            client.stopContainerCmd(containerId).exec();
        } catch (NotModifiedException ex) {
            LOG.info("Already stopped.");
            llog.println("Already stopped.");
        }

        getLaunchAction(build).stopped(client, containerId);

        if (remove) {
            LOG.info("Removing container {}...", containerId);
            llog.println("Removing container " + containerId + "...");
            client.removeContainerCmd(containerId);
        }
    }

    @Extension
    public static final class DescriptorImpl extends DockerBuilderControlOptionDescriptor {
        @Override
        public String getDisplayName() {
            return "Stop Container";
        }

    }
}
