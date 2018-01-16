package io.jenkins.docker.connector;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import com.nirima.jenkins.plugins.docker.utils.PortUtils;
import com.trilead.ssh2.signature.RSAKeyAlgorithm;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import hudson.plugins.sshslaves.verifiers.SshHostKeyVerificationStrategy;
import hudson.slaves.ComputerLauncher;
import hudson.util.ListBoxModel;
import io.jenkins.docker.client.DockerAPI;
import jenkins.bouncycastle.api.PEMEncodable;
import jenkins.model.Jenkins;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.jenkinsci.Symbol;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static hudson.remoting.Base64.encode;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerComputerSSHConnector extends DockerComputerConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerComputerSSHConnector.class);

    private final SSHKeyStrategy sshKeyStrategy;
    private int port;
    private String jvmOptions;
    private String javaPath;
    private String prefixStartSlaveCmd;
    private String suffixStartSlaveCmd;
    private Integer launchTimeoutSeconds;
    private Integer maxNumRetries;
    private Integer retryWaitTime;

    @DataBoundConstructor
    public DockerComputerSSHConnector(SSHKeyStrategy sshKeyStrategy) {
        this.sshKeyStrategy = sshKeyStrategy;
        this.port = 22;
    }

    public SSHKeyStrategy getSshKeyStrategy() {
        return sshKeyStrategy;
    }

    public int getPort() {
        return port;
    }

    @DataBoundSetter
    public void setPort(int port) {
        this.port = port;
    }

    public String getJvmOptions() {
        return jvmOptions;
    }

    @DataBoundSetter
    public void setJvmOptions(String jvmOptions) {
        this.jvmOptions = jvmOptions;
    }

    public String getJavaPath() {
        return javaPath;
    }

    @DataBoundSetter
    public void setJavaPath(String javaPath) {
        this.javaPath = javaPath;
    }

    public String getPrefixStartSlaveCmd() {
        return prefixStartSlaveCmd;
    }

    @DataBoundSetter
    public void setPrefixStartSlaveCmd(String prefixStartSlaveCmd) {
        this.prefixStartSlaveCmd = prefixStartSlaveCmd;
    }

    public String getSuffixStartSlaveCmd() {
        return suffixStartSlaveCmd;
    }

    @DataBoundSetter
    public void setSuffixStartSlaveCmd(String suffixStartSlaveCmd) {
        this.suffixStartSlaveCmd = suffixStartSlaveCmd;
    }

    public Integer getLaunchTimeoutSeconds() {
        return launchTimeoutSeconds;
    }

    @DataBoundSetter
    public void setLaunchTimeoutSeconds(Integer launchTimeoutSeconds) {
        this.launchTimeoutSeconds = launchTimeoutSeconds;
    }

    public Integer getMaxNumRetries() {
        return maxNumRetries;
    }

    @DataBoundSetter
    public void setMaxNumRetries(Integer maxNumRetries) {
        this.maxNumRetries = maxNumRetries;
    }

    public Integer getRetryWaitTime() {
        return retryWaitTime;
    }

    @DataBoundSetter
    public void setRetryWaitTime(Integer retryWaitTime) {
        this.retryWaitTime = retryWaitTime;
    }

    @Override
    public void beforeContainerCreated(DockerAPI api, String workdir, CreateContainerCmd cmd) throws IOException, InterruptedException {

        // TODO define a strategy for SSHD process configuration so we support more than openssh's sshd
        if (cmd.getCmd() == null || cmd.getCmd().length == 0) {

            if (sshKeyStrategy.getInjectedKey() != null) {
                cmd.withCmd("/usr/sbin/sshd", "-D", "-p", String.valueOf(port),
                        // override sshd_config to force retrieval of InstanceIdentity public for as authentication
                        "-o", "AuthorizedKeysCommand=/root/authorized_key",
                        "-o", "AuthorizedKeysCommandUser=root"
                );
            } else {
                cmd.withCmd("/usr/sbin/sshd", "-D", "-p", String.valueOf(port));
            }
        }

        cmd.withPortSpecs(port+"/tcp");
        final PortBinding sshPortBinding = PortBinding.parse(":" + port);
        final Ports portBindings = cmd.getPortBindings();
        if(portBindings != null) {
            portBindings.add(sshPortBinding);
            cmd.withPortBindings(portBindings);
        } else {
            cmd.withPortBindings(sshPortBinding);
        }
        cmd.withExposedPorts(ExposedPort.parse(port+"/tcp"));
    }

    @Override
    public void beforeContainerStarted(DockerAPI api, String workdir, String containerId) throws IOException, InterruptedException {

        final String key = sshKeyStrategy.getInjectedKey();
        if (key != null) {
            String AuthorizedKeysCommand = "#!/bin/sh\n"
                    + "[ \"$1\" = \"" + sshKeyStrategy.getUser() + "\" ] "
                    + "&& echo '" + key + "'"
                    + "|| :";

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

                    api.getClient().copyArchiveToContainerCmd(containerId)
                            .withTarInputStream(is)
                            .withRemotePath("/root")
                            .exec();
                }
            }
        }
    }

    @Override
    protected ComputerLauncher createLauncher(DockerAPI api, String workdir, InspectContainerResponse inspect, TaskListener listener) throws IOException, InterruptedException {
        if ("exited".equals(inspect.getState().getStatus())) {
            // Something went wrong
            // FIXME report error "somewhere" visible to end user.
            LOGGER.error("Failed to launch docker SSH agent :" + inspect.getState().getExitCode());
            throw new IOException("Failed to launch docker SSH agent. Container exited with status " + inspect.getState().getExitCode());
        }
        LOGGER.debug("container created {}", inspect);

        final InetSocketAddress address = getBindingForPort(api, inspect, port);

        // Wait until sshd has started
        // TODO we could (also) have a more generic mechanism relying on healthcheck (inspect State.Health.Status)
        final PortUtils.ConnectionCheck connectionCheck =
                PortUtils.connectionCheck( address )
                        .withRetries( 30 )
                        .withEveryRetryWaitFor( 2, TimeUnit.SECONDS );
        if (!connectionCheck.execute() || !connectionCheck.useSSH().execute()) {
            throw new IOException("SSH service didn't started after 60s.");
        }

        return sshKeyStrategy.getSSHLauncher(address, this);
    }


    private InetSocketAddress getBindingForPort(DockerAPI api, InspectContainerResponse ir, int internalPort) {
        // get exposed port
        ExposedPort sshPort = new ExposedPort(internalPort);
        Integer port = 22;

        final NetworkSettings networkSettings = ir.getNetworkSettings();
        final Ports ports = networkSettings.getPorts();
        final Map<ExposedPort, Ports.Binding[]> bindings = ports.getBindings();

        // Get the binding that goes to the port that we're interested in (e.g: 22)
        final Ports.Binding[] sshBindings = bindings.get(sshPort);

        // Find where it's mapped to
        for (Ports.Binding b : sshBindings) {
            String hps = b.getHostPortSpec();
            port = Integer.valueOf(hps);
        }
        String host = getExternalIP(api, ir, networkSettings, sshBindings);

        return new InetSocketAddress(host, port);
    }

    private String getExternalIP(DockerAPI api, InspectContainerResponse ir, NetworkSettings networkSettings,
                                 Ports.Binding[] sshBindings) {
        // If an explicit IP/hostname has been defined, always prefer this one
        String dockerHostname = api.getHostname();
        if (dockerHostname != null && !dockerHostname.trim().isEmpty()) {
            return dockerHostname;
        }

        // for (standalone) swarm, need to get the address of the actual host
        if (api.isSwarm()) {
            for (Ports.Binding b : sshBindings) {
                String ipAddress = b.getHostIp();
                if (ipAddress != null && !"0.0.0.0".equals(ipAddress)) {
                    return ipAddress;
                }
            }
        }

        // see https://github.com/joyent/sdc-docker/issues/132
        final String driver = ir.getExecDriver();
        if (driver != null && driver.startsWith("sdc")) {
            // We run on Joyent's Triton
            // see https://docs.joyent.com/public-cloud/instances/docker/how/inspect-containers
            return networkSettings.getIpAddress();
        }

        final URI uri = URI.create(api.getDockerHost().getUri());
        if(uri.getScheme().equals("unix")) {
            // Communicating with unix domain socket. so we assume localhost
            return "0.0.0.0";
        } else {
            return uri.getHost();
        }
    }

    @Extension @Symbol("ssh")
    public static final class DescriptorImpl extends Descriptor<DockerComputerConnector> {

        @Override
        public String getDisplayName() {
            return "Connect with SSH";
        }

        public List getSSHKeyStrategyDescriptors() {
            return Jenkins.getInstance().getDescriptorList(SSHKeyStrategy.class);
        }
    }

    public abstract static class SSHKeyStrategy extends AbstractDescribableImpl<SSHKeyStrategy> {

        public abstract String getInjectedKey() throws IOException;

        public abstract String getUser();

        public abstract ComputerLauncher getSSHLauncher(InetSocketAddress address, DockerComputerSSHConnector dockerComputerSSHConnector) throws IOException;
    }

    public static class InjectSSHKey extends SSHKeyStrategy {

        private final String user;

        @DataBoundConstructor
        public InjectSSHKey(String user) {
            this.user = user;
        }

        public String getUser() {
            return user;
        }

        @Override
        public ComputerLauncher getSSHLauncher(InetSocketAddress address, DockerComputerSSHConnector connector) throws IOException {
            InstanceIdentity id = InstanceIdentity.get();
            String pem = PEMEncodable.create(id.getPrivate()).encode();

            return new DockerSSHLauncher(address.getHostString(), address.getPort(), user, pem,
                    connector.jvmOptions, connector.javaPath, connector.prefixStartSlaveCmd, connector.suffixStartSlaveCmd,
                    connector.launchTimeoutSeconds, connector.maxNumRetries, connector.retryWaitTime,
                    new NonVerifyingKeyVerificationStrategy()
            );
        }

        @Override
        public String getInjectedKey() throws IOException {
            InstanceIdentity id = InstanceIdentity.get();
            return "ssh-rsa " + encode(new RSAKeyAlgorithm().encodePublicKey(id.getPublic()));
        }

        @Extension
        public static final class DescriptorImpl extends Descriptor<SSHKeyStrategy> {

            @Nonnull
            @Override
            public String getDisplayName() {
                return "Inject SSH key";
            }
        }

    }

    public static class ManuallyConfiguredSSHKey extends SSHKeyStrategy {

        private final String credentialsId;

        private final SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy;

        @DataBoundConstructor
        public ManuallyConfiguredSSHKey(String credentialsId, SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy) {
            this.credentialsId = credentialsId;
            this.sshHostKeyVerificationStrategy = sshHostKeyVerificationStrategy;
        }

        public String getCredentialsId() {
            return credentialsId;
        }

        @Override
        public String getUser() {
            return SSHLauncher.lookupSystemCredentials(credentialsId).getUsername();
        }

        public SshHostKeyVerificationStrategy getSshHostKeyVerificationStrategy() {
            return sshHostKeyVerificationStrategy;
        }

        @Override
        public ComputerLauncher getSSHLauncher(InetSocketAddress address, DockerComputerSSHConnector connector) throws IOException {
            return new SSHLauncher(address.getHostString(), address.getPort(), getCredentialsId(),
                    connector.jvmOptions, connector.javaPath, connector.prefixStartSlaveCmd, connector.suffixStartSlaveCmd,
                    connector.launchTimeoutSeconds, connector.maxNumRetries, connector.retryWaitTime,
                    sshHostKeyVerificationStrategy
            );
        }

        @Override
        public String getInjectedKey() throws IOException {
            return null;
        }

        @Extension
        public static final class DescriptorImpl extends Descriptor<SSHKeyStrategy> {

            @Nonnull
            @Override
            public String getDisplayName() {
                return "Use configured SSH credentials";
            }

            public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
                return DockerTemplateBase.DescriptorImpl.doFillCredentialsIdItems(context);
            }
        }
    }

    /**
     * Custom flavour of SSHLauncher which allows to inject some custom credentials without this one being stored
     * in a CredentialStore
     */
    private static class DockerSSHLauncher extends SSHLauncher {

        private String user;
        private String privateKey;

        public DockerSSHLauncher(String host, int port, String user, String privateKey, String jvmOptions, String javaPath, String prefixStartSlaveCmd, String suffixStartSlaveCmd, Integer launchTimeoutSeconds, Integer maxNumRetries, Integer retryWaitTime, SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy) {
            super(host, port, "InstanceIdentity", jvmOptions, javaPath, prefixStartSlaveCmd, suffixStartSlaveCmd, launchTimeoutSeconds, maxNumRetries, retryWaitTime, sshHostKeyVerificationStrategy);
            this.user = user;
            this.privateKey = privateKey;
        }

        @Override
        public StandardUsernameCredentials getCredentials() {
            return new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, "InstanceIdentity", user,
                    new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKey), null,
                    "private key for docker ssh agent");
        }
    }
}
