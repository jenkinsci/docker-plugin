package io.jenkins.docker;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerComputer;
import com.nirima.jenkins.plugins.docker.DockerSlave;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.launcher.DockerComputerSSHLauncher;
import com.trilead.ssh2.signature.RSAPublicKey;
import hudson.model.Descriptor;
import hudson.plugins.sshslaves.SSHConnector;
import hudson.plugins.sshslaves.SSHLauncher;
import jenkins.bouncycastle.api.PEMEncodable;
import jenkins.model.Jenkins;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;

import static com.trilead.ssh2.signature.RSASHA1Verify.encodeSSHRSAPublicKey;
import static hudson.remoting.Base64.encode;

/**
 * Launch Jenkins agent and connect using ssh.
 * Instance identity is used as SSH key pair and  <code>sshd</code> in container is configured to accept it. This
 * enforce security as only master can connect to this container.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class SSHDockerSlaveProvisioner extends DockerSlaveProvisioner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSHDockerSlaveProvisioner.class);


    private final DockerComputerSSHLauncher launcher;
    private DockerSlave slave;

    public SSHDockerSlaveProvisioner(DockerCloud cloud, DockerTemplate template, DockerClient client, DockerComputerSSHLauncher launcher) {
        super(cloud, template, client);
        this.launcher = launcher;

    }

    @Override
    public DockerSlave provision() throws IOException, Descriptor.FormException, InterruptedException {

        String id = runContainer();

        final InspectContainerResponse inspect = client.inspectContainerCmd(container).exec();
        if ("exited".equals(inspect.getState().getStatus())) {
            // Something went wrong

            // FIXME report error "somewhere" visible to end user.
            LOGGER.error("Failed to launch docker SSH agent :" + inspect.getState().getExitCode());
        }

        StandardUsernameCredentials pk = getPrivateKeyAsCredentials();

        final InetSocketAddress address = launcher.getAddressForSSHD(cloud, inspect);
        final SSHConnector sshConnector = launcher.getSshConnector();

        SSHLauncher ssh = new SSHLauncher(address.getHostString(), address.getPort(), pk,
                sshConnector.jvmOptions,
                sshConnector.javaPath, sshConnector.prefixStartSlaveCmd,
                sshConnector.suffixStartSlaveCmd, sshConnector.launchTimeoutSeconds);

        this.slave = new DockerSlave(cloud, template, ssh);
        Jenkins.getActiveInstance().addNode(slave);
        final DockerComputer computer = this.slave.getComputer();
        if (computer != null) { 
            computer.setContainerId(id);
        }

        return slave;
    }

    /**
     * expose InstanceIdentity as a SSH key pair for credentials API
     */
    private StandardUsernameCredentials getPrivateKeyAsCredentials() throws IOException {

        if (!launcher.isUseSSHkey()) {
            return launcher.getSshConnector().getCredentials();
        }

        InstanceIdentity id = InstanceIdentity.get();
        String pem = PEMEncodable.create(id.getPrivate()).encode();

        // TODO find how to inject authorized_key owned by a (configurable) user "jenkins"
        return new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, "InstanceIdentity", "root",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(pem), null,
                "private key for docker ssh agent");
    }

    @Override
    protected void prepareCreateContainerCommand(CreateContainerCmd cmd) throws DockerException, IOException {

        final int sshPort = launcher.getSshConnector().port;


        // AuthorizedKeysCommand

        if (cmd.getCmd() == null || cmd.getCmd().length == 0) {

            if (launcher.isUseSSHkey()) {
                cmd.withCmd("/usr/sbin/sshd", "-D", "-p", String.valueOf(sshPort),
                            // override sshd_config to force retrieval of InstanceIdentity public for as authentication
                            "-o", "AuthorizedKeysCommand "+ "/root/authorized_key",
                            "-o", "AuthorizedKeysCommandUser root"
                );
            } else {
                cmd.withCmd("/usr/sbin/sshd", "-D", "-p", String.valueOf(sshPort));
            }
        }

        cmd.withPortSpecs(sshPort+"/tcp");
        cmd.withPortBindings(PortBinding.parse(":"+sshPort));
        cmd.withExposedPorts(ExposedPort.parse(sshPort+"/tcp"));
    }

    /**
     * Inject InstanceIdentity public key as authorized_key in sshd container.
     * @throws IOException
     */
    @Override
    protected void setupContainer() throws IOException {

        if (launcher.isUseSSHkey()) {
            InstanceIdentity id = InstanceIdentity.get();
            final java.security.interfaces.RSAPublicKey rsa = id.getPublic();
            String AuthorizedKeysCommand = "#!/bin/sh\n"
                    + "echo 'ssh-rsa " + encode(encodeSSHRSAPublicKey(new RSAPublicKey(rsa.getPublicExponent(), rsa.getModulus()))) + "'";

            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 TarArchiveOutputStream tar = new TarArchiveOutputStream(bos)) {
                TarArchiveEntry entry = new TarArchiveEntry("authorized_key");
                entry.setSize(AuthorizedKeysCommand.getBytes().length);
                entry.setMode(0700);
                tar.putArchiveEntry(entry);
                tar.write(AuthorizedKeysCommand.getBytes());
                tar.closeArchiveEntry();
                tar.close();

                try (InputStream is = new ByteArrayInputStream(bos.toByteArray())) {

                    client.copyArchiveToContainerCmd(container)
                            .withTarInputStream(is)
                            .withRemotePath("/root")
                            .exec();
                }
            }
        }
    }
}
