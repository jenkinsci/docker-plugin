package com.nirima.jenkins.plugins.docker;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.CauseOfBlockage;
import java.util.Calendar;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class DockerQueueTaskDispatcherTest {

    @Test
    void exposesProvisioningFailureAsQueueWhy(JenkinsRule rule) throws Exception {
        final DockerTemplate template = new DockerTemplate(
                new DockerTemplateBase("registry.example.com/agent:pr-105"), null, "docker", null, "1");
        template.setPullStrategy(DockerImagePullStrategy.PULL_NEVER);
        final FreeStyleProject project = rule.createFreeStyleProject("queued-provisioning-failure");
        project.setAssignedLabel(new LabelAtom("unavailable-docker-agent"));
        assertNotNull(project.scheduleBuild2(0));
        final Queue.Item item = rule.jenkins.getQueue().getItem(project);
        assertNotNull(item);

        DockerQueueTaskDispatcher.recordProvisioningFailure(
                item, template, new IllegalStateException("No such image"), 300_000L);
        rule.jenkins.getQueue().maintain();

        final Queue.Item blockedItem = rule.jenkins.getQueue().getItem(project);
        assertNotNull(blockedItem);
        assertTrue(blockedItem.getWhy().contains("No such image"));
        assertTrue(blockedItem.getWhy().contains("Retrying in"));
        rule.jenkins.getQueue().cancel(project);
    }

    @Test
    void displaysProvisioningFailureUntilRetryIsDue(JenkinsRule rule) throws Exception {
        final DockerTemplate template = new DockerTemplate(
                new DockerTemplateBase("registry.example.com/agent:pr-105"), null, "docker", null, "1");
        template.setPullStrategy(DockerImagePullStrategy.PULL_NEVER);
        final Queue.WaitingItem item = new Queue.WaitingItem(
                Calendar.getInstance(), rule.createFreeStyleProject("failed-provisioning"), List.of());

        DockerQueueTaskDispatcher.recordProvisioningFailure(
                item,
                template,
                new RuntimeException("provisioning failed", new IllegalStateException("No such image")),
                300_000L,
                1_000L);

        final CauseOfBlockage cause = DockerQueueTaskDispatcher.causeOfBlockage(item, 2_000L);
        assertNotNull(cause);
        assertTrue(cause.getShortDescription().contains("registry.example.com/agent:pr-105"));
        assertTrue(cause.getShortDescription().contains("pull strategy: Never pull"));
        assertTrue(cause.getShortDescription().contains("No such image"));
        assertTrue(cause.getShortDescription().contains("Retrying in"));
    }

    @Test
    void clearsProvisioningFailureWhenRetryIsDue(JenkinsRule rule) throws Exception {
        final DockerTemplate template = new DockerTemplate(
                new DockerTemplateBase("registry.example.com/agent:pr-105"), null, "docker", null, "1");
        final Queue.WaitingItem item = new Queue.WaitingItem(
                Calendar.getInstance(), rule.createFreeStyleProject("retry-provisioning"), List.of());

        DockerQueueTaskDispatcher.recordProvisioningFailure(
                item, template, new IllegalStateException("No such image"), 300_000L, 1_000L);

        assertNull(DockerQueueTaskDispatcher.causeOfBlockage(item, 301_000L));
        assertNull(DockerQueueTaskDispatcher.causeOfBlockage(item, 301_001L));
    }
}
