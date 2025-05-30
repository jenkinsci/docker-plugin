package io.jenkins.docker.connector;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import hudson.slaves.Cloud;
import hudson.tasks.BatchFile;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import hudson.util.StreamTaskListener;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.hamcrest.number.OrderingComparison;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@WithJenkins
public abstract class DockerComputerConnectorTest {
    protected static final String COMMON_IMAGE_USERNAME = "jenkins";
    protected static final String COMMON_IMAGE_HOMEDIR = "/home/jenkins/agent";
    protected static final String INSTANCE_CAP = "10";

    protected JenkinsRule j;

    protected static int getJavaVersion() {
        Runtime.Version runtimeVersion = Runtime.version();
        return runtimeVersion.version().get(0);
    }

    protected static String getJenkinsDockerImageVersionForThisEnvironment() {
        /*
         * Maintenance note: This code needs to follow the tagging strategy used in
         * https://hub.docker.com/r/jenkins/agent/tags etc.
         */
        final int javaVersion = getJavaVersion();
        if (Functions.isWindows()) {
            if (javaVersion >= 21) {
                return "jdk21-nanoserver-1809";
            }
            return "jdk17-nanoserver-1809";
        }
        if (javaVersion >= 21) {
            return "latest-jdk21";
        }
        return "latest-jdk17";
    }

    protected String getLabelForTemplate() {
        return "dockerAgent" + testNumber + "For" + this.getClass().getSimpleName();
    }

    private static int testNumber;
    private String cloudName;

    @BeforeAll
    public static void setUpClass() {
        testNumber = 0;
    }

    @BeforeEach
    public void setUp(JenkinsRule j) {
        this.j = j;
        testNumber++;
        cloudName = "DockerCloud" + testNumber + "For" + this.getClass().getSimpleName();
    }

    @AfterEach
    public void cleanup() throws Exception {
        terminateAllDockerNodes();
        final long startTimeMs = System.currentTimeMillis();
        final Long maxWaitTimeMs = 60 * 1000L;
        final long initialWaitTime = 50;
        long currentWaitTime = initialWaitTime;
        while (dockerIsStillBusy()) {
            currentWaitTime = currentWaitTime * 2;
            Thread.sleep(currentWaitTime);
            terminateAllDockerNodes();
            final long currentTimeMs = System.currentTimeMillis();
            final Long elapsedTimeMs = currentTimeMs - startTimeMs;
            assertThat(elapsedTimeMs, OrderingComparison.lessThan(maxWaitTimeMs));
        }
    }

    private void terminateAllDockerNodes() {
        final TaskListener tl = new StreamTaskListener(System.out, StandardCharsets.UTF_8);
        for (final Node n : j.jenkins.getNodes()) {
            if (n instanceof DockerTransientNode) {
                final DockerTransientNode dn = (DockerTransientNode) n;
                dn._terminate(tl);
            }
        }
    }

    private boolean dockerIsStillBusy() throws Exception {
        for (final Node n : j.jenkins.getNodes()) {
            if (n instanceof DockerTransientNode) {
                return true;
            }
        }
        for (final Cloud c : j.jenkins.clouds) {
            if (c instanceof DockerCloud) {
                DockerCloud cloud = (DockerCloud) c;
                for (final DockerTemplate t : cloud.getTemplates()) {
                    final int containersInProgress = cloud.countContainersInProgress(t);
                    if (containersInProgress > 0) {
                        return true;
                    }
                }
                final int containersInDocker = cloud.countContainersInDocker(null);
                if (containersInDocker > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void should_connect_agent(DockerTemplate template)
            throws IOException, ExecutionException, InterruptedException, TimeoutException {

        // FIXME on CI windows nodes don't have Docker4Windows
        assumeFalse(Functions.isWindows());

        // Given
        final String templateLabel = template.getLabelString();
        assertFalse(templateLabel.contains(" "), "Error in test code - test template must be a single unique string");
        final String expectedTextInBuildLog = "ourBuildDidActuallyRun" + System.nanoTime();
        final Builder buildStepToEchoExpectedText;
        final String dockerHost;
        if (Functions.isWindows()) {
            buildStepToEchoExpectedText = new BatchFile("echo " + expectedTextInBuildLog);
            dockerHost = "tcp://localhost:2375";
        } else {
            buildStepToEchoExpectedText = new Shell("echo " + expectedTextInBuildLog);
            dockerHost = "unix:///var/run/docker.sock";
        }

        DockerCloud cloud = new DockerCloud(
                cloudName, new DockerAPI(new DockerServerEndpoint(dockerHost, null)), List.of(template));
        j.jenkins.clouds.replaceBy(Set.of(cloud));

        // When
        final FreeStyleProject project = j.createFreeStyleProject("test-docker-agent-can-connect");
        project.setAssignedLabel(Label.get(templateLabel));
        project.getBuildersList().add(buildStepToEchoExpectedText);
        final QueueTaskFuture<FreeStyleBuild> scheduledBuild = project.scheduleBuild2(0);
        final Result actualBuildResult;
        final List<String> actualBuildLog;
        try {
            final FreeStyleBuild build = scheduledBuild.get(120L, TimeUnit.SECONDS);
            actualBuildResult = build.getResult();
            actualBuildLog = build.getLog(1000);
        } finally {
            scheduledBuild.cancel(true);
        }

        // Then
        assertEquals(Result.SUCCESS, actualBuildResult);
        assertTrue(actualBuildLog.contains(expectedTextInBuildLog));
    }
}
