package com.nirima.jenkins.plugins.docker.cloudstat;

import com.nirima.jenkins.plugins.docker.DockerNodeFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.slaves.ComputerLauncher;
import io.jenkins.docker.DockerTransientNode;
import java.io.IOException;
import java.util.concurrent.Future;

/**
 * Another implementation, allows classloading to fail gracefully without
 * a lot of reflection, should the CloudStats plugin not be loaded.
 *
 * Per {@link org.jenkinsci.plugins.cloudstats.TrackedItem},
 * <i> It is necessary to implement this by
 * {@link hudson.slaves.NodeProvisioner.PlannedNode},
 * {@link hudson.model.Node} and {@link hudson.model.Computer}.</i>
 */
public class CloudStatsFactory implements DockerNodeFactory {

    private static final long serialVersionUID = 1;

    @NonNull
    @Override
    public DockerPlannedNode createPlannedNode(
            String displayName, Future<Node> future, int numExecutors, String cloud, String template, String node) {
        return new TrackedDockerPlannedNode(displayName, future, numExecutors, cloud, template, node);
    }

    @NonNull
    @Override
    public DockerTransientNode createTransientNode(
            String nodeName, String containerId, String effectiveRemoteFsDir, ComputerLauncher launcher)
            throws Descriptor.FormException, IOException {
        return new TrackedDockerTransientNode(nodeName, containerId, effectiveRemoteFsDir, launcher);
    }
}
