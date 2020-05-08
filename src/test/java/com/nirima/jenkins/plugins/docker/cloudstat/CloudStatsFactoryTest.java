package com.nirima.jenkins.plugins.docker.cloudstat;

import com.nirima.jenkins.plugins.docker.DockerNodeFactory;
import com.nirima.jenkins.plugins.docker.DockerTemplateBaseTest;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;
import hudson.model.Node;
import hudson.slaves.ComputerLauncher;
import io.jenkins.docker.DockerTransientNode;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.cloudstats.TrackedItem;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class CloudStatsFactoryTest {

    @BeforeClass
    public static void setUpClass() {
        JenkinsUtils.setTestInstanceId(CloudStatsFactoryTest.class.getSimpleName());

    }

    @AfterClass
    public static void tearDownClass() {
        JenkinsUtils.setTestInstanceId(null);
    }

    @Test
    public void createPlannedNode() {
        CloudStatsFactory uut = new CloudStatsFactory();
        CompletableFuture<Node> future = new CompletableFuture<>();
        DockerNodeFactory.DockerPlannedNode result = uut.createPlannedNode("testNode", future, 1, "coudName", "templateName", "nodeName");
        assertNotNull(result);
        assertThat(result, is(instanceOf(TrackedItem.class)));
    }

    @Test
    @Ignore("Requires PowerMock of Jenkins and very little logic here. See https://wiki.jenkins.io/display/JENKINS/Mocking+in+Unit+Tests")
    public void createTransientNode() throws Throwable{
        CloudStatsFactory uut = new CloudStatsFactory();
        ComputerLauncher launcher = mock(ComputerLauncher.class);
        DockerTransientNode result = uut.createTransientNode("nodeName","containerId","/foo/bar", launcher);

        assertNotNull(result);
        verifyNoMoreInteractions(launcher);
        assertThat(result, is(instanceOf(TrackedItem.class)));

    }
}