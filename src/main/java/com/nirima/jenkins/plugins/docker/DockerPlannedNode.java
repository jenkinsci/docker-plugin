package com.nirima.jenkins.plugins.docker;

import hudson.model.Node;
import io.jenkins.docker.DockerTransientNode;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedPlannedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns cleanup after core consumes a completed Docker provisioning attempt. */
class DockerPlannedNode extends TrackedPlannedNode {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerPlannedNode.class);
    private final CompletableFuture<Node> completedNode;

    DockerPlannedNode(ProvisioningActivity.Id id, int executors, CompletableFuture<Node> completedNode) {
        super(id, executors, completedNode);
        this.completedNode = completedNode;
    }

    @Override
    public void spent() {
        final Node node;
        try {
            node = completedNode.getNow(null);
        } catch (CancellationException | CompletionException failure) {
            // The provisioning task already owns cleanup when no node is delivered.
            return;
        }
        if (node instanceof DockerTransientNode dockerNode
                && (dockerNode.isInitialSavePending()
                        || Jenkins.get().getNode(dockerNode.getNodeName()) != dockerNode)) {
            // Core invokes spent even after an unchecked registration failure,
            // for which CloudProvisioningListener.onRollback is not dispatched.
            dockerNode.terminate(LOGGER);
        }
    }
}
