package io.jenkins.docker.connector;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.command.CreateContainerCmdImpl;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import com.trilead.ssh2.signature.RSAKeyAlgorithm;

import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import io.jenkins.docker.client.DockerAPI;
import jenkins.bouncycastle.api.PEMEncodable;

import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import static hudson.remoting.Base64.encode;

import java.io.IOException;
import java.util.Map;

public class DockerComputerSSHConnectorTest extends DockerComputerConnectorTest {

    private static final String SSH_AGENT_IMAGE_IMAGENAME = "jenkins/ssh-agent";
    private static final String SSH_AGENT_IMAGE_JAVAPATH = "/usr/local/openjdk-8/bin/java";

    @Test
    public void connectAgentViaSSHUsingInjectSshKey() throws Exception {
        final DockerComputerSSHConnector.SSHKeyStrategy sshKeyStrategy = new DockerComputerSSHConnector.InjectSSHKey(COMMON_IMAGE_USERNAME);
        final DockerComputerSSHConnector connector = new DockerComputerSSHConnector(sshKeyStrategy);
        connector.setJavaPath(SSH_AGENT_IMAGE_JAVAPATH);
        final DockerTemplate template = new DockerTemplate(
                new DockerTemplateBase(SSH_AGENT_IMAGE_IMAGENAME),
                connector,
                LABEL, COMMON_IMAGE_HOMEDIR, INSTANCE_CAP
        );
        template.setName("connectAgentViaSSHUsingInjectSshKey");
        should_connect_agent(template);
    }

    @Test
    public void connectAgentViaSSHUsingCredentialsKey() throws Exception {
        final InstanceIdentity id = InstanceIdentity.get();
        final String privateKey = PEMEncodable.create(id.getPrivate()).encode();
        final String publicKey = "ssh-rsa " + encode(new RSAKeyAlgorithm().encodePublicKey(id.getPublic()));
        final String credentialsId = "tempCredId";
        final StandardUsernameCredentials credentials = DockerComputerSSHConnector.makeCredentials(credentialsId, COMMON_IMAGE_USERNAME, privateKey);
        SystemCredentialsProvider.getInstance().getCredentials().add(credentials);
        final DockerComputerSSHConnector.SSHKeyStrategy sshKeyStrategy = new DockerComputerSSHConnector.ManuallyConfiguredSSHKey(credentialsId, new NonVerifyingKeyVerificationStrategy());
        final DockerComputerSSHConnector connector = new DockerComputerSSHConnector(sshKeyStrategy);
        connector.setJavaPath(SSH_AGENT_IMAGE_JAVAPATH);
        final DockerTemplate template = new DockerTemplate(
                new DockerTemplateBase(SSH_AGENT_IMAGE_IMAGENAME),
                connector,
                LABEL, COMMON_IMAGE_HOMEDIR, INSTANCE_CAP
        );
        template.getDockerTemplateBase().setEnvironmentsString("JENKINS_SLAVE_SSH_PUBKEY=" + publicKey);
        template.setName("connectAgentViaSSHUsingCredentialsKey");
        should_connect_agent(template);
    }

    @Test
    public void testPortBinding() throws IOException, InterruptedException {
        DockerComputerSSHConnector connector = new DockerComputerSSHConnector(Mockito.mock(DockerComputerSSHConnector.SSHKeyStrategy.class));
        CreateContainerCmdImpl cmd = new CreateContainerCmdImpl(Mockito.mock(CreateContainerCmd.Exec.class), Mockito.mock(AuthConfig.class), "");
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
