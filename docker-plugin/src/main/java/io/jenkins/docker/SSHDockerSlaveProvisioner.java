package io.jenkins.docker;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerComputer;
import com.nirima.jenkins.plugins.docker.DockerSlave;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.utils.PortUtils;
import hudson.model.Descriptor;
import hudson.plugins.sshslaves.SSHLauncher;
import io.jenkins.docker.connector.DockerComputerSSHConnector;
import jenkins.model.Jenkins;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Launch Jenkins agent and connect using ssh.
 * Instance identity is used as SSH key pair and  <code>sshd</code> in container is configured to accept it. This
 * enforce security as only master can connect to this container.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class SSHDockerSlaveProvisioner extends DockerSlaveProvisioner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSHDockerSlaveProvisioner.class);


    private final DockerComputerSSHConnector launcher;
    private DockerSlave slave;

    public SSHDockerSlaveProvisioner(DockerCloud cloud, DockerTemplate template, DockerClient client, DockerComputerSSHConnector launcher) {
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
            throw new IOException("Failed to launch docker SSH agent. Container exited with status " + inspect.getState().getExitCode());
        }
        LOGGER.debug("container created {}", inspect);

        final InetSocketAddress address = getBindingForPort(cloud, inspect, launcher.getPort());

        // Wait until sshd has started
        // TODO we could (also) have a more generic mechanism relying on healthcheck (inspect State.Health.Status)
        final PortUtils.ConnectionCheck connectionCheck =
                PortUtils.connectionCheck( address )
                        .withRetries( 30 )
                        .withEveryRetryWaitFor( 2, TimeUnit.SECONDS );
        if (!connectionCheck.execute() || !connectionCheck.useSSH().execute()) {
            throw new IOException("SSH service didn't started after 60s.");
        }

        StandardUsernameCredentials pk = launcher.getSshKeyStrategy().getCredentials();
        SSHLauncher ssh = new SSHLauncher(address.getHostString(), address.getPort(), pk,
                launcher.getJvmOptions(),
                launcher.getJavaPath(), launcher.getPrefixStartSlaveCmd(),
                launcher.getSuffixStartSlaveCmd(), launcher.getLaunchTimeoutSeconds());

        this.slave = new DockerSlave(cloud, template, ssh);
        Jenkins.getActiveInstance().addNode(slave);
        final DockerComputer computer = this.slave.getComputer();
        if (computer != null) { 
            computer.setContainerId(id);
        }

        return slave;
    }

    @Override
    protected void prepareCreateContainerCommand(CreateContainerCmd cmd) throws DockerException, IOException {

        final int sshPort = launcher.getPort();


        // TODO define a strategy for SSHD process configuration so we support more than openssh's sshd
        if (cmd.getCmd() == null || cmd.getCmd().length == 0) {

            if (launcher.getSshKeyStrategy().getInjectedKey() != null) {
                cmd.withCmd("/usr/sbin/sshd", "-D", "-p", String.valueOf(sshPort),
                            // override sshd_config to force retrieval of InstanceIdentity public for as authentication
                            "-o", "AuthorizedKeysCommand "+ "/root/authorized_key \"%u\"",
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

        final String key = launcher.getSshKeyStrategy().getInjectedKey();
        if (key != null) {
            String AuthorizedKeysCommand = "#!/bin/sh\n"
                    + "[ \"$1\" = \"" + ((DockerComputerSSHConnector.InjectSSHKey) launcher.getSshKeyStrategy()).getUser()
                    + "\" ] && echo '" + key + "' || :";

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


    // TODO move external port binding detection to dedicated class / strategy (there's to much ways to get this wrong)

    @Restricted(NoExternalUse.class)
    public static InetSocketAddress getBindingForPort(DockerCloud cloud, InspectContainerResponse ir, int internalPort) {
        // get exposed port
        ExposedPort sshPort = new ExposedPort(internalPort);
        String host = null;
        Integer port = 22;

        final NetworkSettings networkSettings = ir.getNetworkSettings();
        final Ports ports = networkSettings.getPorts();
        final Map<ExposedPort, Ports.Binding[]> bindings = ports.getBindings();

        // Get the binding that goes to the port that we're interested in (e.g: 22)
        final Ports.Binding[] sshBindings = bindings.get(sshPort);

        // Find where it's mapped to
        for (Ports.Binding b : sshBindings) {

            // TODO: I don't really follow why docker-java here is capable of
            // returning a range - surely an exposed port cannot be bound to a *range*?
            String hps = b.getHostPortSpec();

            port = Integer.valueOf(hps);
            host = b.getHostIp();
        }

        //get address, if docker on localhost, then use local?
        if (host == null || host.equals("0.0.0.0")) {
            String url = cloud.getDockerHost().getUri();
            host = getDockerHostFromCloud(cloud);

            if( url.startsWith("unix") && (host == null || host.trim().isEmpty()) ) {
                // Communicating with local sockets.
                host = "0.0.0.0";
            } else {

            /* Don't use IP from DOCKER_HOST because it is invalid or we are
             * connecting to a system that supports a single host abstraction
             * like Joyent's Triton. */
                if (host == null || host.equals("0.0.0.0")) {
                    // Try to connect to the container directly (without going through the host)
                    host = networkSettings.getIpAddress();
                    port = internalPort;
                }
            }
        }

        return new InetSocketAddress(host, port);
    }

    private static String getDockerHostFromCloud(DockerCloud cloud) {
        String url;
        String host;
        url = cloud.getDockerHost().getUri();
        String dockerHostname = cloud.getDockerHostname();
        if (dockerHostname != null && !dockerHostname.trim().isEmpty()) {
            return dockerHostname;
        } else {
            return URI.create(url).getHost();
        }
    }

}
