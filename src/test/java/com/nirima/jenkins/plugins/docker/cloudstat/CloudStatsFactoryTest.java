package com.nirima.jenkins.plugins.docker.cloudstat;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.nirima.jenkins.plugins.docker.DockerNodeFactory;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;
import hudson.model.Node;
import hudson.slaves.ComputerLauncher;
import io.jenkins.docker.DockerTransientNode;
import java.util.concurrent.CompletableFuture;
import org.jenkinsci.plugins.cloudstats.TrackedItem;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;

public class CloudStatsFactoryTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

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
        DockerNodeFactory.DockerPlannedNode result =
                uut.createPlannedNode("testNode", future, 1, "coudName", "templateName", "nodeName");
        assertNotNull(result);
        assertThat(result, is(instanceOf(TrackedItem.class)));
    }

    @Test
    public void createTransientNode() throws Throwable {
        CloudStatsFactory uut = new CloudStatsFactory();
        ComputerLauncher launcher = mock(ComputerLauncher.class);
        DockerTransientNode result = uut.createTransientNode("nodeName", "containerId", "/foo/bar", launcher);

        assertNotNull(result);
        verifyNoMoreInteractions(launcher);
        assertThat(result, is(instanceOf(TrackedItem.class)));
    }
}
