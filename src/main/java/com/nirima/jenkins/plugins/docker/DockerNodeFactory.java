package com.nirima.jenkins.plugins.docker;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProvisioner;
import io.jenkins.docker.DockerTransientNode;
import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.Future;

public interface DockerNodeFactory extends Serializable {

    /**
     * Safely load the instance of the DockerNodeFactory
     * @return a new instance of a implementation of DockerNodeFactory
     */
    static DockerNodeFactory getInstance() {
        try {
            return (DockerNodeFactory) Class.forName("com.nirima.jenkins.plugins.docker.cloudstat.CloudStatsFactory")
                    .newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            // could not load cloud stats library
        }
        return new DockerNodeFactory() {
            private static final long serialVersionUid = 1L;

            @NonNull
            @Override
            public DockerPlannedNode createPlannedNode(
                    String displayName,
                    Future<Node> future,
                    int numExecutors,
                    String cloud,
                    String template,
                    String node) {
                return new DockerPlannedNode(displayName, future, numExecutors);
            }

            @NonNull
            @Override
            public DockerTransientNode createTransientNode(
                    String nodeName, String containerId, String effectiveRemoteFsDir, ComputerLauncher launcher)
                    throws Descriptor.FormException, IOException {
                return new DockerTransientNode(nodeName, containerId, effectiveRemoteFsDir, launcher);
            }
        };
    }

    /**
     * Create a Planned Node with functionality for tracking via CloudStats
     * @param displayName parameter for PlannedNode constructor
     * @param future  parameter for PlannedNode constructor
     * @param numExecutors parameter for PlannedNode constructor
     * @param cloud parameter for ProvisioningActivity.Id constructor
     * @param template  parameter for ProvisioningActivity.Id constructor
     * @param node parameter for ProvisioningActivity.Id constructor
     * @return a new PlannedNode instance which may support tracking
     */
    @NonNull
    public DockerPlannedNode createPlannedNode(
            @NonNull String displayName,
            @NonNull Future<Node> future,
            int numExecutors,
            @NonNull String cloud,
            @NonNull String template,
            @NonNull String node);

    /**
     * Create a new DockerTransientNode. This method is required to allow
     * instances of TrackedDockerTransientNode to be returned, rather than
     * use the original DockerTransientNode, when CloudStats plugin is loaded
     * @param nodeName parameter for DockerTransientNode constructor
     * @param containerId parameter for DockerTransientNode constructor
     * @param effectiveRemoteFsDir parameter for DockerTransientNode constructor
     * @param launcher parameter for DockerTransientNode constructor
     * @return a new DockerTransientNode instance
     */
    @NonNull
    public DockerTransientNode createTransientNode(
            @NonNull String nodeName,
            @NonNull String containerId,
            @NonNull String effectiveRemoteFsDir,
            @NonNull ComputerLauncher launcher)
            throws Descriptor.FormException, IOException;

    public class DockerPlannedNode extends NodeProvisioner.PlannedNode {

        public DockerPlannedNode(String displayName, Future<Node> future, int numExecutors) {
            super(displayName, future, numExecutors);
        }

        public void notifyStarted() {
            // does nothing
        }

        public void notifyFailure(Throwable e) {
            // does nothing
        }

        public void notifySuccess(Node node) {
            // does nothing
        }
    }
}
