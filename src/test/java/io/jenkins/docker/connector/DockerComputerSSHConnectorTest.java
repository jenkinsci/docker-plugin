package io.jenkins.docker.connector;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.command.CreateContainerCmdImpl;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Result;
import hudson.tasks.Shell;
import io.jenkins.docker.client.DockerAPI;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class DockerComputerSSHConnectorTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void run_ssh_agent() throws IOException, ExecutionException, InterruptedException {
        DockerCloud cloud = new DockerCloud("docker", new DockerAPI(new DockerServerEndpoint("unix:///var/run/docker.sock", null)),
                Collections.singletonList(
                        new DockerTemplate(
                                new DockerTemplateBase("jenkins/ssh-slave"),
                                new DockerComputerSSHConnector(new DockerComputerSSHConnector.InjectSSHKey("jenkins")),
                                "docker-ssh", "/home/jenkins", "10", Collections.EMPTY_LIST
                        )
                ));

        jenkins.getInstance().clouds.replaceBy(Collections.singleton(cloud));

        final FreeStyleProject project = jenkins.createFreeStyleProject("test-docker-ssh");
        project.setAssignedLabel(Label.get("docker-ssh"));
        project.getBuildersList().add(new Shell("echo 'hello docker'"));
        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        Assert.assertTrue(build.getResult() == Result.SUCCESS);
    }


    @Test
    public void testPortBinding() throws IOException, InterruptedException {
        DockerComputerSSHConnector connector = new DockerComputerSSHConnector(Mockito.mock(DockerComputerSSHConnector.SSHKeyStrategy.class));
        CreateContainerCmdImpl cmd = new CreateContainerCmdImpl(Mockito.mock(CreateContainerCmd.Exec.class), "");
        cmd.withPortBindings(PortBinding.parse("42:42"));

        connector.setPort(22);
        connector.beforeContainerCreated(Mockito.mock(DockerAPI.class), "/workdir", cmd);
        final Ports portBindings = cmd.getPortBindings();
        Assert.assertNotNull(portBindings);
        final Map<ExposedPort, Ports.Binding[]> bindingMap = portBindings.getBindings();
        Assert.assertNotNull(bindingMap);
        Assert.assertEquals(2, bindingMap.size());

        final Ports.Binding[] configuredBindings = bindingMap.get(new ExposedPort(42));
        Assert.assertNotNull(configuredBindings);
        Assert.assertEquals(1, configuredBindings.length);
        Assert.assertEquals("42", configuredBindings[0].getHostPortSpec());

        final Ports.Binding[] sshBindings = bindingMap.get(new ExposedPort(22));
        Assert.assertNotNull(sshBindings);
        Assert.assertEquals(1, sshBindings.length);
        Assert.assertNull(sshBindings[0].getHostPortSpec());

        System.out.println();
    }
}
