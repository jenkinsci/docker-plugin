package io.jenkins.docker.connector;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerTemplate;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import hudson.slaves.Cloud;
import hudson.tasks.Shell;
import hudson.util.StreamTaskListener;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;
import org.apache.commons.lang3.SystemUtils;
import org.hamcrest.number.OrderingComparison;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class DockerComputerConnectorTest {
    protected static final String LABEL = "docker-agent";
    protected static final String COMMON_IMAGE_USERNAME = "jenkins";
    protected static final String COMMON_IMAGE_HOMEDIR = "/home/jenkins/agent";
    protected static final String INSTANCE_CAP = "10";

    private static int testNumber;
    private String cloudName;

    @BeforeClass
    public static void setUpClass() {
        testNumber=0;
    }

    @Before
    public void setUp() {
        testNumber++;
        cloudName = "DockerCloudFor" + this.getClass().getSimpleName() + testNumber;
    }

    @After
    public void cleanup() throws Exception {
        terminateAllDockerNodes();
        final long startTimeMs = System.currentTimeMillis();
        final Long maxWaitTimeMs = 60 * 1000L;
        final long initialWaitTime = 50;
        long currentWaitTime = initialWaitTime;
        while( dockerIsStillBusy()) {
            currentWaitTime = currentWaitTime * 2;
            Thread.sleep(currentWaitTime);
            terminateAllDockerNodes();
            final long currentTimeMs = System.currentTimeMillis();
            final Long elapsedTimeMs = currentTimeMs - startTimeMs;
            Assert.assertThat(elapsedTimeMs, OrderingComparison.lessThan(maxWaitTimeMs));
        }
    }

    private void terminateAllDockerNodes() {
        final TaskListener tl = new StreamTaskListener(System.out, Charset.forName("UTF-8"));
        for( final Node n : j.jenkins.getNodes() ) {
            if( n instanceof DockerTransientNode ) {
                final DockerTransientNode dn = (DockerTransientNode)n;
                dn.terminate(tl);
            }
        }
    }

    private boolean dockerIsStillBusy() throws Exception {
        for( final Node n : j.jenkins.getNodes() ) {
            if( n instanceof DockerTransientNode ) {
                return true;
            }
        }
        for( final Cloud c : j.jenkins.clouds) {
            if( c instanceof DockerCloud) {
                DockerCloud cloud = (DockerCloud)c;
                for( final DockerTemplate t : cloud.getTemplates() ) {
                    final int containersInProgress = cloud.countContainersInProgress(t);
                    if( containersInProgress > 0 ) {
                        return true;
                    }
                }
                final int containersInDocker = cloud.countContainersInDocker(null);
                if( containersInDocker > 0 ) {
                    return true;
                }
            }
        }
        return false;
    }

    @Rule
    public JenkinsRule j = new JenkinsRule();

    protected void should_connect_agent(DockerTemplate template) throws IOException, ExecutionException, InterruptedException, TimeoutException {

        // FIXME on CI windows nodes don't have Docker4Windows
        Assume.assumeFalse(SystemUtils.IS_OS_WINDOWS);

        String dockerHost = SystemUtils.IS_OS_WINDOWS ? "tcp://localhost:2375" : "unix:///var/run/docker.sock";

        DockerCloud cloud = new DockerCloud(cloudName, new DockerAPI(new DockerServerEndpoint(dockerHost, null)),
                Collections.singletonList(template));

        j.jenkins.clouds.replaceBy(Collections.singleton(cloud));

        final FreeStyleProject project = j.createFreeStyleProject("test-docker-ssh");
        project.setAssignedLabel(Label.get(LABEL));
        project.getBuildersList().add(new Shell("whoami"));
        final QueueTaskFuture<FreeStyleBuild> scheduledBuild = project.scheduleBuild2(0);
        try {
            final FreeStyleBuild build = scheduledBuild.get(60L, TimeUnit.SECONDS);
            Assert.assertTrue(build.getResult() == Result.SUCCESS);
            Assert.assertTrue(build.getLog().contains("jenkins"));
        } finally {
            scheduledBuild.cancel(true);
        }
    }
}
