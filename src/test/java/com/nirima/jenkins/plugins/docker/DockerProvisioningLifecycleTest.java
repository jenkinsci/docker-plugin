package com.nirima.jenkins.plugins.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import hudson.BulkChange;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProvisioner;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/** Docker-free reproductions of the registration boundaries used by cloud workers. */
@WithJenkins
class DockerProvisioningLifecycleTest {
    @Test
    void completedWorkerIsNotRegisteredAgain(JenkinsRule jenkins) throws Exception {
        DockerTransientNode node = newNode("completed-worker");
        CountDownLatch releaseProvisioning = new CountDownLatch(1);
        DockerTemplate template = mock(DockerTemplate.class);
        when(template.getDisabled()).thenReturn(new DockerDisabled());
        when(template.getMode()).thenReturn(Node.Mode.NORMAL);
        when(template.getImage()).thenReturn("image");
        when(template.getName()).thenReturn("template");
        when(template.getNumExecutors()).thenReturn(1);
        when(template.provisionNode(any(), any())).thenAnswer(invocation -> {
            assertTrue(releaseProvisioning.await(10, TimeUnit.SECONDS));
            return node;
        });
        DockerCloud cloud = new DockerCloud("cloud", null, List.of(template));
        cloud.setContainerCap(Integer.MAX_VALUE);
        var plannedNodes = cloud.provision((Label) null, 1);
        assertEquals(1, plannedNodes.size());
        var planned = plannedNodes.iterator().next();
        // Execute the core consumer before the plugin's provisioning thread continues.
        // This is a legal CompletableFuture ordering, with no timing sleeps or Docker daemon.
        @SuppressWarnings("unchecked")
        CompletableFuture<Node> ready = (CompletableFuture<Node>) planned.future;
        var consumed = ready.thenAccept(worker -> {
            try {
                jenkins.jenkins.addNode(worker);
                jenkins.jenkins.removeNode(worker);
            } catch (IOException failure) {
                throw new RuntimeException(failure);
            }
        });
        try {
            releaseProvisioning.countDown();
            consumed.get(10, TimeUnit.SECONDS);
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                while (cloud.countContainersInProgress() != 0) {
                    Thread.sleep(10);
                }
            });
            assertNull(jenkins.jenkins.getNode(node.getNodeName()), "A completed worker must not reappear");
        } finally {
            releaseProvisioning.countDown();
        }
    }

    @Test
    void completedProvisioningWakesCoreConsumer(JenkinsRule jenkins) throws Exception {
        DockerTransientNode node = newNode("ready-worker");
        CountDownLatch releaseProvisioning = new CountDownLatch(1);
        DockerTemplate template = mock(DockerTemplate.class);
        when(template.getDisabled()).thenReturn(new DockerDisabled());
        when(template.getMode()).thenReturn(Node.Mode.NORMAL);
        when(template.getImage()).thenReturn("image");
        when(template.getName()).thenReturn("template");
        when(template.getNumExecutors()).thenReturn(1);
        when(template.provisionNode(any(), any())).thenAnswer(invocation -> {
            assertTrue(releaseProvisioning.await(10, TimeUnit.SECONDS));
            return node;
        });
        DockerCloud cloud = new DockerCloud("ready-cloud", null, List.of(template));
        cloud.setContainerCap(Integer.MAX_VALUE);
        var planned = cloud.provision((Label) null, 1).iterator().next();
        NodeProvisioner core = jenkins.jenkins.unlabeledNodeProvisioner;
        recordPendingLaunch(core, planned);
        try {
            releaseProvisioning.countDown();
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                while (!core.getPendingLaunches().isEmpty()) {
                    Thread.sleep(10);
                }
            });
            assertSame(node, jenkins.jenkins.getNode(node.getNodeName()));
            assertTrue(node.isAcceptingTasks());
        } finally {
            releaseProvisioning.countDown();
        }
    }

    @Test
    void notificationErrorDoesNotReclaimDeliveredWorker(JenkinsRule jenkins) throws Exception {
        DockerTransientNode node = spy(newNode("notification-worker"));
        doNothing().when(node).terminate(any(org.slf4j.Logger.class));
        DockerTemplate template = mock(DockerTemplate.class);
        when(template.getDisabled()).thenReturn(new DockerDisabled());
        when(template.getMode()).thenReturn(Node.Mode.NORMAL);
        when(template.getImage()).thenReturn("image");
        when(template.getName()).thenReturn("template");
        when(template.getNumExecutors()).thenReturn(1);
        when(template.provisionNode(any(), any())).thenReturn(node);
        DockerCloud cloud = spy(new DockerCloud("notification-cloud", null, List.of(template)));
        cloud.setContainerCap(Integer.MAX_VALUE);
        doThrow(new OutOfMemoryError("Expected notification thread failure"))
                .when(cloud)
                .requestProvisioningReview(any());
        var planned = cloud.provision((Label) null, 1).iterator().next();
        assertSame(node, planned.future.get(10, TimeUnit.SECONDS));
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            while (cloud.countContainersInProgress() != 0) {
                Thread.sleep(10);
            }
        });
        verify(cloud).requestProvisioningReview(any());
        verify(node, never()).terminate(any(org.slf4j.Logger.class));
        consumeWithCore(jenkins, planned);
        assertSame(node, jenkins.jenkins.getNode(node.getNodeName()));
        assertTrue(node.isAcceptingTasks());
    }

    @Test
    void cancelledDeliveryRetainsProvisioningCleanupOwnership(JenkinsRule jenkins) throws Exception {
        DockerTransientNode node = spy(newNode("cancelled-worker"));
        CountDownLatch releaseProvisioning = new CountDownLatch(1);
        CountDownLatch terminated = new CountDownLatch(1);
        doAnswer(invocation -> {
                    terminated.countDown();
                    return null;
                })
                .when(node)
                .terminate(any(org.slf4j.Logger.class));
        DockerTemplate template = mock(DockerTemplate.class);
        when(template.getDisabled()).thenReturn(new DockerDisabled());
        when(template.getMode()).thenReturn(Node.Mode.NORMAL);
        when(template.getImage()).thenReturn("image");
        when(template.getName()).thenReturn("template");
        when(template.getNumExecutors()).thenReturn(1);
        when(template.provisionNode(any(), any())).thenAnswer(invocation -> {
            assertTrue(releaseProvisioning.await(10, TimeUnit.SECONDS));
            return node;
        });
        DockerCloud cloud = new DockerCloud("cancelled-cloud", null, List.of(template));
        cloud.setContainerCap(Integer.MAX_VALUE);
        var planned = cloud.provision((Label) null, 1).iterator().next();
        try {
            assertTrue(planned.future.cancel(true));
            releaseProvisioning.countDown();
            assertTrue(terminated.await(10, TimeUnit.SECONDS), "Undelivered workers must retain a cleanup owner");
            assertNull(jenkins.jenkins.getNode("cancelled-worker"));
        } finally {
            releaseProvisioning.countDown();
        }
    }

    @Test
    void workerCannotAcceptTasksWhileInitialSaveIsPending(JenkinsRule jenkins) throws Exception {
        CountDownLatch saveStarted = new CountDownLatch(1);
        CountDownLatch releaseSave = new CountDownLatch(1);
        DockerTransientNode node = new SavingNode(saveStarted, releaseSave);
        node.setLauncher(new UnavailableLauncher());
        var executor = Executors.newSingleThreadExecutor();
        var registration = executor.submit(() -> {
            jenkins.jenkins.addNode(node);
            return null;
        });
        try {
            assertTrue(saveStarted.await(10, TimeUnit.SECONDS));
            assertSame(node, jenkins.jenkins.getNode(node.getNodeName()));
            assertFalse(node.isAcceptingTasks(), "A fast job must not run before initial persistence finishes");
            releaseSave.countDown();
            registration.get(10, TimeUnit.SECONDS);
            assertTrue(node.isAcceptingTasks());
        } finally {
            releaseSave.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void failedInitialSaveDoesNotAdmitTasks(JenkinsRule jenkins) throws Exception {
        DockerTransientNode node = new DockerTransientNode("failed-worker", "unused-container", "/tmp/agent") {
            @Override
            public void save() throws IOException {
                throw new IOException("Expected initial persistence failure");
            }
        };
        node.setLauncher(new UnavailableLauncher());
        assertThrows(IOException.class, () -> jenkins.jenkins.addNode(node));
        assertNull(jenkins.jenkins.getNode(node.getNodeName()));
        assertFalse(node.isAcceptingTasks());
    }

    @Test
    void failedCloudRegistrationRemovesOwnedContainer(JenkinsRule jenkins) throws Exception {
        assertFailedRegistrationCleanup(jenkins, false);
    }

    @Test
    void uncheckedCloudRegistrationFailureRemovesOwnedContainer(JenkinsRule jenkins) throws Exception {
        assertFailedRegistrationCleanup(jenkins, true);
    }

    private static void assertFailedRegistrationCleanup(JenkinsRule jenkins, boolean unchecked) throws Exception {
        DockerTransientNode node = new FailingSaveNode(unchecked);
        node.setLauncher(new UnavailableLauncher());
        node.setRemoveVolumes(true);
        DockerAPI api = mock(DockerAPI.class);
        DockerClient client = mock(DockerClient.class);
        StopContainerCmd stop = mock(StopContainerCmd.class);
        RemoveContainerCmd remove = mock(RemoveContainerCmd.class);
        when(api.getClient()).thenReturn(client);
        when(client.stopContainerCmd("owned-container")).thenReturn(stop);
        when(stop.withTimeout(anyInt())).thenReturn(stop);
        when(client.removeContainerCmd("owned-container")).thenReturn(remove);
        when(remove.withRemoveVolumes(true)).thenReturn(remove);
        CountDownLatch removed = new CountDownLatch(1);
        doAnswer(invocation -> {
                    removed.countDown();
                    return null;
                })
                .when(remove)
                .exec();
        DockerTemplate template = mock(DockerTemplate.class);
        when(template.getDisabled()).thenReturn(new DockerDisabled());
        when(template.getMode()).thenReturn(Node.Mode.NORMAL);
        when(template.getImage()).thenReturn("image");
        when(template.getName()).thenReturn("template");
        when(template.getNumExecutors()).thenReturn(1);
        when(template.provisionNode(any(), any())).thenReturn(node);
        DockerCloud cloud = new DockerCloud("rollback-cloud", api, List.of(template));
        cloud.setContainerCap(Integer.MAX_VALUE);
        var planned = cloud.provision((Label) null, 1).iterator().next();
        assertSame(node, planned.future.get(10, TimeUnit.SECONDS));
        // Drive the real core consumer. Its unchecked-error branch does not fire
        // onRollback, but its finally block must still call PlannedNode.spent.
        consumeWithCore(jenkins, planned);
        assertTrue(removed.await(10, TimeUnit.SECONDS), "Core rollback must remove the owned container");
        verify(client).stopContainerCmd("owned-container");
        verify(client).removeContainerCmd("owned-container");
        assertNull(jenkins.jenkins.getNode(node.getNodeName()));
        assertFalse(node.isAcceptingTasks());
    }

    @Test
    void persistedWorkerRetainsAcceptanceSetting(JenkinsRule jenkins) throws Exception {
        DockerTransientNode node = newNode("persisted-worker");
        jenkins.jenkins.addNode(node);
        assertTrue(node.isAcceptingTasks());
        DockerTransientNode restored =
                (DockerTransientNode) jenkins.jenkins.XSTREAM2.fromXML(jenkins.jenkins.XSTREAM2.toXML(node));
        assertTrue(restored.isAcceptingTasks(), "Restored workers must not wait for a new initial save");
        node.setAcceptingTasks(false);
        restored = (DockerTransientNode) jenkins.jenkins.XSTREAM2.fromXML(jenkins.jenkins.XSTREAM2.toXML(node));
        assertFalse(restored.isAcceptingTasks(), "Explicit task rejection must survive serialization");
    }

    @Test
    void unregisteredSaveDoesNotAdmitTasks(JenkinsRule jenkins) throws Exception {
        DockerTransientNode node = newNode("unregistered-worker");
        node.save();
        node.setAcceptingTasks(true);
        assertFalse(node.isAcceptingTasks());
        jenkins.jenkins.addNode(node);
        assertTrue(node.isAcceptingTasks());
    }

    @Test
    void bulkChangeDoesNotAdmitTasksBeforeCommit(JenkinsRule jenkins) throws Exception {
        DockerTransientNode node = newNode("bulk-worker");
        try (BulkChange change = new BulkChange(node)) {
            jenkins.jenkins.addNode(node);
            assertFalse(node.isAcceptingTasks());
            change.commit();
        }
        assertTrue(node.isAcceptingTasks());
    }

    @SuppressWarnings("unchecked")
    private static void consumeWithCore(JenkinsRule jenkins, NodeProvisioner.PlannedNode planned) throws Exception {
        NodeProvisioner provisioner = new NodeProvisioner(null, jenkins.jenkins.unlabeledLoad);
        recordPendingLaunch(provisioner, planned);
        var update = NodeProvisioner.class.getDeclaredMethod("update");
        update.setAccessible(true);
        update.invoke(provisioner);
        assertTrue(provisioner.getPendingLaunches().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static void recordPendingLaunch(NodeProvisioner provisioner, NodeProvisioner.PlannedNode planned)
            throws Exception {
        var pending = NodeProvisioner.class.getDeclaredField("pendingLaunches");
        pending.setAccessible(true);
        ((AtomicReference<List<NodeProvisioner.PlannedNode>>) pending.get(provisioner))
                .set(new ArrayList<>(List.of(planned)));
    }

    private static class FailingSaveNode extends DockerTransientNode {
        private final transient boolean unchecked;

        FailingSaveNode(boolean unchecked) throws Exception {
            super("rollback-worker", "owned-container", "/tmp/agent");
            this.unchecked = unchecked;
        }

        @Override
        public void save() throws IOException {
            if (unchecked) {
                throw new IllegalStateException("Expected unchecked core commit failure");
            }
            throw new IOException("Expected core commit failure");
        }
    }

    private static class SavingNode extends DockerTransientNode {
        private final transient CountDownLatch saveStarted;
        private final transient CountDownLatch releaseSave;

        SavingNode(CountDownLatch saveStarted, CountDownLatch releaseSave) throws Exception {
            super("saving-worker", "unused-container", "/tmp/agent");
            this.saveStarted = saveStarted;
            this.releaseSave = releaseSave;
        }

        @Override
        public void save() throws IOException {
            saveStarted.countDown();
            try {
                if (!releaseSave.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting for save release");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IOException(failure);
            }
            super.save();
        }
    }

    private static DockerTransientNode newNode(String name) throws Exception {
        DockerTransientNode node = new DockerTransientNode(name, "unused-container", "/tmp/agent");
        node.setLauncher(new UnavailableLauncher());
        return node;
    }

    private static class UnavailableLauncher extends ComputerLauncher {
        @Override
        public boolean isLaunchSupported() {
            return false;
        }
    }
}
