package io.jenkins.docker.connector;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Result;
import hudson.tasks.Shell;
import io.jenkins.docker.client.DockerAPI;
import org.apache.commons.lang3.SystemUtils;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class DockerComputerConnectorTest {

    @After
    public void cleanup() {
        // TODO rm orphaned container
    }

    @Rule
    public JenkinsRule j = new JenkinsRule();


    protected void should_connect_agent(DockerTemplate template) throws IOException, ExecutionException, InterruptedException {

        // FIXME on CI windows nodes don't have Docker4Windows
        Assume.assumeFalse(SystemUtils.IS_OS_WINDOWS);

        String dockerHost = SystemUtils.IS_OS_WINDOWS ? "tcp://localhost:2375" : "unix:///var/run/docker.sock";

        DockerCloud cloud = new DockerCloud("docker", new DockerAPI(new DockerServerEndpoint(dockerHost, null)),
                Collections.singletonList(template));

        j.jenkins.clouds.replaceBy(Collections.singleton(cloud));

        final FreeStyleProject project = j.createFreeStyleProject("test-docker-ssh");
        project.setAssignedLabel(Label.get("docker-agent"));
        project.getBuildersList().add(new Shell("whoami"));
        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        Assert.assertTrue(build.getResult() == Result.SUCCESS);
        Assert.assertTrue(build.getLog().contains("jenkins"));
    }


}
