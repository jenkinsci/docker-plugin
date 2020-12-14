package com.nirima.jenkins.plugins.docker.builder;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.docker.client.DockerAPI;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

        final DockerCloud cloud = getCloud(build, launcher);
        final DockerAPI dockerApi = cloud.getDockerApi();
        try(final DockerClient client = dockerApi.getClient()) {
            executeOnDocker(build, llog, client);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings("unused")
    private void executeOnDocker(Run<?, ?> build, PrintStream llog, DockerClient client)
            throws DockerException {
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
            try {
                client.removeContainerCmd(containerId).exec();
            } catch (NotFoundException e) {
                llog.println("Container '" + containerId + "' already gone.");
            } catch (ConflictException e) {
                llog.println("Container '" + containerId + "' removal already in progress.");
            }
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
