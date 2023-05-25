package io.jenkins.docker.connector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;
import java.io.Closeable;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

class DockerDelegatingComputerLauncher extends DelegatingComputerLauncher {
    private static final Logger LOGGER = Logger.getLogger(DockerDelegatingComputerLauncher.class.getName());
    private static final Level LOG_LEVEL = Level.FINE;
    private final DockerAPI api;
    private final String containerId;
    private transient boolean haveLoggedOnDisconnectAlready;

    public DockerDelegatingComputerLauncher(ComputerLauncher launcher, DockerAPI api, String containerId) {
        super(launcher);
        this.api = api;
        this.containerId = containerId;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        try (final DockerClient client = api.getClient()) {
            client.inspectContainerCmd(containerId).exec();
        } catch (NotFoundException handledByCode) {
            LOGGER.log(LOG_LEVEL, "Container " + containerId + " no longer exists - NOT launching agent.");
            // Container has been removed
            Queue.withLock(() -> {
                DockerTransientNode node = (DockerTransientNode) computer.getNode();
                node._terminate(listener);
            });
            return;
        }
        LOGGER.log(LOG_LEVEL, "Container " + containerId + " exists - launching agent.");
        super.launch(computer, listener);
    }

    private class DockerLog implements ResultCallback<Frame> {
        @Override
        public void close() throws IOException {}

        @Override
        public void onStart(Closeable closeable) {}

        @Override
        public void onNext(Frame object) {
            LOGGER.log(LOG_LEVEL, "Container " + containerId + " logged: " + object.toString());
        }

        @Override
        public void onError(Throwable throwable) {
            LOGGER.log(LOG_LEVEL, "Container " + containerId + " threw:", throwable);
        }

        @Override
        public void onComplete() {}
    }

    @Override
    public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
        final boolean shouldSeeWhatTheContainerLogged;
        synchronized (this) {
            shouldSeeWhatTheContainerLogged = !haveLoggedOnDisconnectAlready;
            haveLoggedOnDisconnectAlready = true;
        }
        if (shouldSeeWhatTheContainerLogged) {
            final DockerLog callback = new DockerLog();
            try (final DockerClient client = api.getClient()) {
                client.logContainerCmd(containerId)
                        .withStdErr(true)
                        .withStdOut(true)
                        .withTail(100)
                        .exec(callback);
            } catch (Exception ignored) {
                LOGGER.log(LOG_LEVEL, "Container " + containerId + " log could not be inspected.", ignored);
            }
        }
        super.beforeDisconnect(computer, listener);
    }
}
