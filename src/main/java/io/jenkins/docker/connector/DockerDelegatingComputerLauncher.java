package io.jenkins.docker.connector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;

import java.io.IOException;

class DockerDelegatingComputerLauncher extends DelegatingComputerLauncher {
    private final DockerAPI api;
    private final String containerId;

    public DockerDelegatingComputerLauncher(ComputerLauncher launcher, DockerAPI api, String containerId) {
        super(launcher);
        this.api = api;
        this.containerId = containerId;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        try(final DockerClient client = api.getClient()) {
            client.inspectContainerCmd(containerId).exec();
        } catch (NotFoundException e) {
            // Container has been removed
            Queue.withLock(() -> {
                DockerTransientNode node = (DockerTransientNode) computer.getNode();
                node.terminate(listener);
            });
            return;
        }
        super.launch(computer, listener);
    }
}
