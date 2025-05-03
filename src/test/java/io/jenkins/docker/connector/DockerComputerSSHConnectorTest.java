package io.jenkins.docker.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.command.CreateContainerCmdImpl;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import com.trilead.ssh2.signature.RSAKeyAlgorithm;
import hudson.Functions;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import io.jenkins.docker.client.DockerAPI;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import jenkins.bouncycastle.api.PEMEncodable;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mockito;
import org.testcontainers.DockerClientFactory;

@WithJenkins
class DockerComputerSSHConnectorTest extends DockerComputerConnectorTest {

    private static final String SSH_AGENT_IMAGE_IMAGENAME = "jenkins/ssh-agent";
    /**
     * Where the JDK can be found.
     * <p>
     * <b>MAINTENANCE NOTE:</b> Originally, Java was on the PATH and the SSH
     * connector found it there. Then, the image changed and java wasn't on the path
     * anymore and had to be set in the unit-tests to
     * <code>"/usr/local/openjdk-8/bin/java"</code>. Then, the image changed again
     * and java was on the path again but had moved.
     * </p>
     * TL;DR: If java is on the path then this can (and should) be null, but if it
     * isn't on the path then we'll need to set this to where java has been moved
     * to.
     */
    private static final String SSH_AGENT_IMAGE_JAVAPATH = null;

    protected static String getJenkinsDockerImageVersionForThisEnvironment() {
        /*
         * Maintenance note: This code needs to follow the tagging strategy used in
         * https://hub.docker.com/r/jenkins/ssh-agent/tags etc.
         */
        // TODO Switch to "latest" after adapting to https://github.com/jenkinsci/docker-ssh-agent/issues/513
        String label = "6.11.1";
        if (Functions.isWindows()) {
            label = label + "-nanoserver-ltsc2019";
        }
        return getJavaVersion() >= 21 ? label + "-jdk21" : label + "-jdk17";
    }

    @Test
    void connectAgentViaSSHUsingInjectSshKey() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
        final DockerComputerSSHConnector.SSHKeyStrategy sshKeyStrategy =
                new DockerComputerSSHConnector.InjectSSHKey(COMMON_IMAGE_USERNAME);
        final DockerComputerSSHConnector connector = new DockerComputerSSHConnector(sshKeyStrategy);
        connector.setJavaPath(SSH_AGENT_IMAGE_JAVAPATH);
        final String imagenameAndVersion =
                SSH_AGENT_IMAGE_IMAGENAME + ':' + getJenkinsDockerImageVersionForThisEnvironment();

        final DockerTemplate template = new DockerTemplate(
                new DockerTemplateBase(imagenameAndVersion),
                connector,
                getLabelForTemplate(),
                COMMON_IMAGE_HOMEDIR,
                INSTANCE_CAP);
        template.setName("connectAgentViaSSHUsingInjectSshKey");
        should_connect_agent(template);
    }

    @Test
    void connectAgentViaSSHUsingCredentialsKey() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
        final InstanceIdentity id = InstanceIdentity.get();
        final String privateKey = PEMEncodable.create(id.getPrivate()).encode();
        final String publicKey =
                "ssh-rsa " + Base64.getEncoder().encodeToString(new RSAKeyAlgorithm().encodePublicKey(id.getPublic()));
        final String credentialsId = "tempCredId";
        final StandardUsernameCredentials credentials =
                DockerComputerSSHConnector.makeCredentials(credentialsId, COMMON_IMAGE_USERNAME, privateKey);
        SystemCredentialsProvider.getInstance().getCredentials().add(credentials);
        final DockerComputerSSHConnector.SSHKeyStrategy sshKeyStrategy =
                new DockerComputerSSHConnector.ManuallyConfiguredSSHKey(
                        credentialsId, new NonVerifyingKeyVerificationStrategy());
        final DockerComputerSSHConnector connector = new DockerComputerSSHConnector(sshKeyStrategy);
        connector.setJavaPath(SSH_AGENT_IMAGE_JAVAPATH);
        final String imagenameAndVersion =
                SSH_AGENT_IMAGE_IMAGENAME + ':' + getJenkinsDockerImageVersionForThisEnvironment();
        final DockerTemplate template = new DockerTemplate(
                new DockerTemplateBase(imagenameAndVersion),
                connector,
                getLabelForTemplate(),
                COMMON_IMAGE_HOMEDIR,
                INSTANCE_CAP);
        template.getDockerTemplateBase().setEnvironmentsString("JENKINS_AGENT_SSH_PUBKEY=" + publicKey);
        template.setName("connectAgentViaSSHUsingCredentialsKey");
        should_connect_agent(template);
    }

    @Test
    void testPortBinding() throws IOException, InterruptedException {
        // Given
        DockerComputerSSHConnector connector =
                new DockerComputerSSHConnector(Mockito.mock(DockerComputerSSHConnector.SSHKeyStrategy.class));
        CreateContainerCmdImpl cmd = new CreateContainerCmdImpl(
                Mockito.mock(CreateContainerCmd.Exec.class), Mockito.mock(AuthConfig.class), "");
        HostConfig hostConfig = cmd.getHostConfig();
        if (hostConfig == null) {
            hostConfig = new HostConfig();
            cmd.withHostConfig(hostConfig);
        }
        final PortBinding exportContainerPort42 = PortBinding.parse("42:42");
        hostConfig.withPortBindings(exportContainerPort42);
        final ExposedPort port42 = new ExposedPort(42);
        final ExposedPort port22 = new ExposedPort(22);

        // When
        connector.setPort(22);
        connector.beforeContainerCreated(Mockito.mock(DockerAPI.class), "/workdir", cmd);

        // Then
        final Ports actualPortBindings = cmd.getHostConfig().getPortBindings();
        assertNotNull(actualPortBindings);
        final Map<ExposedPort, Ports.Binding[]> actualBindingMap = actualPortBindings.getBindings();
        assertNotNull(actualBindingMap);
        assertEquals(2, actualBindingMap.size());

        final Ports.Binding[] actualBindingsForPort42 = actualBindingMap.get(port42);
        assertNotNull(actualBindingsForPort42);
        assertEquals(1, actualBindingsForPort42.length);
        final String actualHostPortSpecForPort42 = actualBindingsForPort42[0].getHostPortSpec();
        assertEquals("42", actualHostPortSpecForPort42);

        final Ports.Binding[] actualBindingsForPort22 = actualBindingMap.get(port22);
        assertNotNull(actualBindingsForPort22);
        assertEquals(1, actualBindingsForPort22.length);
        final String actualHostPortSpecForPort22 = actualBindingsForPort22[0].getHostPortSpec();
        assertNull(actualHostPortSpecForPort22);
    }

    @Test
    void testPortBindingPort22() throws IOException, InterruptedException {
        // Given
        DockerComputerSSHConnector connector =
                new DockerComputerSSHConnector(Mockito.mock(DockerComputerSSHConnector.SSHKeyStrategy.class));
        CreateContainerCmdImpl cmd = new CreateContainerCmdImpl(
                Mockito.mock(CreateContainerCmd.Exec.class), Mockito.mock(AuthConfig.class), "");
        HostConfig hostConfig = cmd.getHostConfig();
        if (hostConfig == null) {
            hostConfig = new HostConfig();
            cmd.withHostConfig(hostConfig);
        }
        final PortBinding exportContainerPort42 = PortBinding.parse("42:42");
        final PortBinding exportContainerPort22 = PortBinding.parse("3022:22");
        hostConfig.withPortBindings(exportContainerPort42, exportContainerPort22);
        final ExposedPort port42 = new ExposedPort(42);
        final ExposedPort port22 = new ExposedPort(22);

        // When
        connector.setPort(22);
        connector.beforeContainerCreated(Mockito.mock(DockerAPI.class), "/workdir", cmd);

        // Then
        final Ports actualPortBindings = cmd.getHostConfig().getPortBindings();
        assertNotNull(actualPortBindings);
        final Map<ExposedPort, Ports.Binding[]> actualBindingMap = actualPortBindings.getBindings();
        assertNotNull(actualBindingMap);
        assertEquals(2, actualBindingMap.size());

        final Ports.Binding[] actualBindingsForPort42 = actualBindingMap.get(port42);
        assertNotNull(actualBindingsForPort42);
        assertEquals(1, actualBindingsForPort42.length);
        final String actualHostPortSpecForPort42 = actualBindingsForPort42[0].getHostPortSpec();
        assertEquals("42", actualHostPortSpecForPort42);

        final Ports.Binding[] actualBindingsForPort22 = actualBindingMap.get(port22);
        assertNotNull(actualBindingsForPort22);
        assertEquals(1, actualBindingsForPort22.length);
        final String actualHostPortSpecForPort22 = actualBindingsForPort22[0].getHostPortSpec();
        assertEquals("3022", actualHostPortSpecForPort22);
    }
}
