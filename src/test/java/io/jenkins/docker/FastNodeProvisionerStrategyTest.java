package io.jenkins.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import hudson.model.Label;
import hudson.model.LoadStatistics;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.CloudProvisioningListener;
import hudson.slaves.NodeProvisioner;
import io.jenkins.docker.client.DockerAPI;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FastNodeProvisionerStrategyTest {

    /**
     * When FastNodeProvisionerStrategy provisions nodes, it must call CloudProvisioningListener.onStarted.
     */
    @Test
    void firesOnStartedForProvisionedNodes(JenkinsRule j) {
        Label label = Label.get("docker-fast-test");
        List<NodeProvisioner.PlannedNode> planned = List.of(plannedNode("a"), plannedNode("b"));

        j.jenkins.clouds.add(new FakeDockerCloud(planned));

        LoadStatistics.LoadStatisticsSnapshot snapshot = mock(LoadStatistics.LoadStatisticsSnapshot.class);
        when(snapshot.getAvailableExecutors()).thenReturn(0);
        when(snapshot.getConnectingExecutors()).thenReturn(0);
        when(snapshot.getQueueLength()).thenReturn(planned.size());

        NodeProvisioner.StrategyState state = mock(NodeProvisioner.StrategyState.class);
        when(state.getLabel()).thenReturn(label);
        when(state.getSnapshot()).thenReturn(snapshot);
        when(state.getPlannedCapacitySnapshot()).thenReturn(0);

        new FastNodeProvisionerStrategy().apply(state);

        assertEquals(planned, List.copyOf(RecordingProvisioningListener.started));
    }

    private static NodeProvisioner.PlannedNode plannedNode(String name) {
        return new NodeProvisioner.PlannedNode(name, new CompletableFuture<Node>(), 1);
    }

    private static class FakeDockerCloud extends DockerCloud {
        private final transient Collection<NodeProvisioner.PlannedNode> planned;

        FakeDockerCloud(Collection<NodeProvisioner.PlannedNode> planned) {
            super("fake-docker", (DockerAPI) null, List.of());
            this.planned = planned;
        }

        @Override
        public boolean canProvision(Label label) {
            return true;
        }

        @Override
        public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
            return planned;
        }
    }

    @TestExtension
    public static class RecordingProvisioningListener extends CloudProvisioningListener {
        static final List<NodeProvisioner.PlannedNode> started = new CopyOnWriteArrayList<>();

        @Override
        public void onStarted(Cloud cloud, Label label, Collection<NodeProvisioner.PlannedNode> plannedNodes) {
            started.addAll(plannedNodes);
        }
    }
}
