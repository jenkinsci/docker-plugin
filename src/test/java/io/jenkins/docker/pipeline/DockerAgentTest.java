package io.jenkins.docker.pipeline;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import io.jenkins.docker.client.DockerAPI;
import java.util.Collections;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
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

    @Test
    public void smokes() throws Exception {
        // as in DockerNodeStepTest.defaults:
        r.jenkins.clouds.add(new DockerCloud(
                "whatever",
                new DockerAPI(new DockerServerEndpoint("unix:///var/run/docker.sock", null)),
                Collections.emptyList()));
        WorkflowJob j = r.createProject(WorkflowJob.class, "p");
        j.setDefinition(new CpsFlowDefinition(
                "pipeline {\n" + "  agent {\n"
                        + "    dockerContainer 'openjdk:11'\n"
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
        r.assertLogContains("openjdk version \"11.", r.buildAndAssertSuccess(j));
    }
}
