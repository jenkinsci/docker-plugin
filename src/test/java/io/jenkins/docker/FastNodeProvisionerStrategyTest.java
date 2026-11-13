package io.jenkins.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import hudson.model.Label;
import hudson.model.LoadStatistics;
import hudson.model.Node;
import hudson.model.queue.CauseOfBlockage;
import hudson.slaves.Cloud;
import hudson.slaves.CloudProvisioningListener;
import hudson.slaves.NodeProvisioner;
import io.jenkins.docker.client.DockerAPI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FastNodeProvisionerStrategyTest {

    @BeforeEach
    void clearRecordedLaunches() {
        RecordingProvisioningListener.started.clear();
    }

    @Test
    void connectingWorkerCoversQueuedJobBeforeSmoothedCoreStrategy(JenkinsRule j) {
        FakeDockerCloud cloud = new FakeDockerCloud(List.of(plannedNode("spare")));
        j.jenkins.clouds.add(cloud);
        NodeProvisioner.StrategyState state = state(1, 1, 0, 0);
        // Actual HomeLab trace: queue=1 and connecting=1, but the core EMA still
        // estimates excess=0.729-0.066-0, above its 0.5 launch threshold.
        when(state.getQueueLengthLatest()).thenReturn(0.729f);
        when(state.getPlannedCapacityLatest()).thenReturn(0.066f);
        for (NodeProvisioner.Strategy strategy : j.jenkins.getExtensionList(NodeProvisioner.Strategy.class)) {
            if (strategy.apply(state) == NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED) {
                break;
            }
        }
        assertTrue(cloud.requests.isEmpty(), "A connecting executor already covers the queued job");
        verify(state, never()).recordPendingLaunches(anyCollection());
    }

    @Test
    void includesCapacityPlannedEarlierInSameReview(JenkinsRule j) {
        FakeDockerCloud cloud = new FakeDockerCloud(List.of(plannedNode("spare")));
        j.jenkins.clouds.add(cloud);
        assertEquals(
                NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED,
                new FastNodeProvisionerStrategy().apply(state(2, 0, 1, 1)));
        assertTrue(cloud.requests.isEmpty());
    }

    @Test
    void countsExecutorsRatherThanPlannedNodes(JenkinsRule j) {
        FakeDockerCloud cloud = new FakeDockerCloud(
                List.of(new NodeProvisioner.PlannedNode("two-executors", new CompletableFuture<Node>(), 2)));
        j.jenkins.clouds.add(cloud);
        assertEquals(
                NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED,
                new FastNodeProvisionerStrategy().apply(state(2, 0, 0, 0)));
        assertEquals(List.of(2), cloud.requests);
    }

    @Test
    void consultsRemainingStrategiesWhenDockerCannotMeetDemand(JenkinsRule j) {
        FakeDockerCloud cloud = new FakeDockerCloud(List.of(plannedNode("only-one")));
        j.jenkins.clouds.add(cloud);
        assertEquals(
                NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES,
                new FastNodeProvisionerStrategy().apply(state(2, 0, 0, 0)));
        assertEquals(List.of(2), cloud.requests);
    }

    @Test
    void leavesOtherCloudTypesToTheirStrategies(JenkinsRule j) {
        Cloud cloud = new OtherCloud();
        j.jenkins.clouds.add(cloud);
        assertEquals(
                NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES,
                new FastNodeProvisionerStrategy().apply(state(1, 0, 0, 0)));
    }

    @Test
    void honorsProvisioningVetoBeforeCreatingWorkers(JenkinsRule j) {
        FakeDockerCloud cloud = new FakeDockerCloud(List.of(plannedNode("vetoed")));
        j.jenkins.clouds.add(cloud);
        assertEquals(
                NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES,
                new FastNodeProvisionerStrategy().apply(state(1, 0, 0, 0)));
        assertTrue(cloud.requests.isEmpty());
        assertTrue(RecordingProvisioningListener.started.isEmpty());
    }

    private static class OtherCloud extends Cloud {
        OtherCloud() {
            super("other");
        }

        @Override
        public boolean canProvision(Label label) {
            return true;
        }

        @Override
        public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
            throw new AssertionError("The Docker strategy must leave other cloud types alone");
        }
    }

    @TestExtension("honorsProvisioningVetoBeforeCreatingWorkers")
    public static class VetoProvisioningListener extends CloudProvisioningListener {
        @Override
        public CauseOfBlockage canProvision(Cloud cloud, Cloud.CloudState state, int executors) {
            return CauseOfBlockage.fromMessage(hudson.model.Messages._Queue_WaitingForNextAvailableExecutor());
        }
    }

    private static NodeProvisioner.StrategyState state(int demand, int connecting, int planned, int additional) {
        LoadStatistics.LoadStatisticsSnapshot snapshot = mock(LoadStatistics.LoadStatisticsSnapshot.class);
        when(snapshot.getQueueLength()).thenReturn(demand);
        when(snapshot.getConnectingExecutors()).thenReturn(connecting);
        NodeProvisioner.StrategyState state = mock(NodeProvisioner.StrategyState.class);
        when(state.getSnapshot()).thenReturn(snapshot);
        when(state.getLabel()).thenReturn(Label.get("docker-capacity-test"));
        when(state.getPlannedCapacitySnapshot()).thenReturn(planned);
        when(state.getAdditionalPlannedCapacity()).thenReturn(additional);
        return state;
    }

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
        private final List<Integer> requests = new ArrayList<>();

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
            requests.add(excessWorkload);
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
