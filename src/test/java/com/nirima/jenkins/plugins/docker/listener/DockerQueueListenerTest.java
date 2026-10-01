package com.nirima.jenkins.plugins.docker.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.StringParameterValue;
import java.util.Calendar;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class DockerQueueListenerTest {

    @Test
    void resolvesParameterizedImage(JenkinsRule rule) throws Exception {
        DockerTemplate template = new DockerTemplate(
                new DockerTemplateBase("registry.example.com/ci/build-agent:${IMAGE_TAG}"), null, "docker", null, "1");
        Queue.WaitingItem item = new Queue.WaitingItem(
                Calendar.getInstance(),
                rule.createFreeStyleProject("parameterized-image"),
                List.of(new ParametersAction(new StringParameterValue("IMAGE_TAG", "pr-1234"))));

        assertEquals("registry.example.com/ci/build-agent:pr-1234", DockerQueueListener.resolveImage(template, item));
    }

    @Test
    void leavesImageUnresolvedWhenParameterIsUnavailable(JenkinsRule rule) throws Exception {
        DockerTemplate template = new DockerTemplate(
                new DockerTemplateBase("registry.example.com/ci/build-agent:${IMAGE_TAG}"), null, "docker", null, "1");
        Queue.WaitingItem item = new Queue.WaitingItem(
                Calendar.getInstance(), rule.createFreeStyleProject("missing-parameter"), List.of());

        assertNull(DockerQueueListener.resolveImage(template, item));
    }

    @Test
    void leavesImageUnresolvedWhenParameterIsEmpty(JenkinsRule rule) throws Exception {
        DockerTemplate template = new DockerTemplate(
                new DockerTemplateBase("registry.example.com/ci/build-agent:${IMAGE_TAG}"), null, "docker", null, "1");
        Queue.WaitingItem item = new Queue.WaitingItem(
                Calendar.getInstance(),
                rule.createFreeStyleProject("empty-parameter"),
                List.of(new ParametersAction(new StringParameterValue("IMAGE_TAG", ""))));

        assertNull(DockerQueueListener.resolveImage(template, item));
    }
}
