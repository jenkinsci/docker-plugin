package io.jenkins.docker.connector;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.command.CreateContainerCmdImpl;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Map;

public class DockerComputerSSHConnectorTest {

    @Test
    public void testPortBinding() throws IOException, InterruptedException {
        DockerComputerSSHConnector connector = new DockerComputerSSHConnector(Mockito.mock(DockerComputerSSHConnector.SSHKeyStrategy.class));
        CreateContainerCmdImpl cmd = new CreateContainerCmdImpl(Mockito.mock(CreateContainerCmd.Exec.class), "");
        cmd.withPortBindings(PortBinding.parse("42:42"));

        connector.setPort(22);
        connector.beforeContainerCreated(Mockito.mock(DockerCloud.class), Mockito.mock(DockerTemplate.class), cmd);
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
