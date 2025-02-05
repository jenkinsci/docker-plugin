package io.jenkins.docker.pipeline;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import io.jenkins.docker.client.DockerAPI;
import java.util.Collections;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.DockerClientFactory;

@WithJenkins
class DockerAgentTest {

    @BeforeAll
    static void before() {
        DockerNodeStepTest.before();
    }

    private JenkinsRule r;

    @BeforeEach
    void setUpCloud(JenkinsRule r) {
        this.r = r;
        // as in DockerNodeStepTest.defaults:
        r.jenkins.clouds.add(new DockerCloud(
                "whatever",
                new DockerAPI(new DockerServerEndpoint("unix:///var/run/docker.sock", null)),
                Collections.emptyList()));
    }

    @Test
    void smokes() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
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
    void withArgs() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
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
