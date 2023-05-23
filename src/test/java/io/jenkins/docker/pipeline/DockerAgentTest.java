package io.jenkins.docker.pipeline;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import io.jenkins.docker.client.DockerAPI;
import java.util.Collections;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class DockerAgentTest {

    @BeforeClass
    public static void before() {
        DockerNodeStepTest.before();
    }

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Before
    public void setUpCloud() {
        // as in DockerNodeStepTest.defaults:
        r.jenkins.clouds.add(new DockerCloud(
                "whatever",
                new DockerAPI(new DockerServerEndpoint("unix:///var/run/docker.sock", null)),
                Collections.emptyList()));
    }

    @Test
    public void smokes() throws Exception {
        WorkflowJob j = r.createProject(WorkflowJob.class, "p");
        j.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" + "  agent {\n"
                        + "    dockerContainer 'eclipse-temurin:17'\n"
                        + "  }\n"
                        + "  stages {\n"
                        + "    stage('whatever') {\n"
                        + "      steps {\n"
                        + "        sh 'java -version'\n"
                        + "      }\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n",
                true));
        r.assertLogContains("openjdk version \"17.", r.buildAndAssertSuccess(j));
    }

    @Test
    public void withArgs() throws Exception {
        WorkflowJob j = r.createProject(WorkflowJob.class, "p");
        j.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" + "  agent {\n"
                        + "    dockerContainer {\n" + "      image 'eclipse-temurin:17'\n"
                        + "      connector attach(jvmArgsString: '-showversion')\n"
                        + "    }\n"
                        + "  }\n"
                        + "  stages {\n"
                        + "    stage('whatever') {\n"
                        + "      steps {\n"
                        + "        sh 'which java'\n"
                        + "      }\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n",
                true));
        // TODO why does DockerTemplate.doProvisionNode fail to stream container output?
        r.assertLogContains("/bin/java", r.buildAndAssertSuccess(j));
    }
}
