package io.jenkins.docker.connector;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.nirima.jenkins.plugins.docker.utils.PortUtils;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.signature.RSAKeyAlgorithm;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import hudson.plugins.sshslaves.verifiers.SshHostKeyVerificationStrategy;
import hudson.security.ACL;
import hudson.slaves.ComputerLauncher;
import hudson.util.ListBoxModel;
import io.jenkins.docker.client.DockerAPI;
import jenkins.bouncycastle.api.PEMEncodable;
import jenkins.model.Jenkins;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.jenkinsci.Symbol;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.bldToString;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.endToString;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.startToString;
import static hudson.remoting.Base64.encode;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerComputerSSHConnector extends DockerComputerConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerComputerSSHConnector.class);

    private final SSHKeyStrategy sshKeyStrategy;
    private int port;
    @CheckForNull
    private String jvmOptions;
    @CheckForNull
    private String javaPath;
    @CheckForNull
    private String prefixStartSlaveCmd;
    @CheckForNull
    private String suffixStartSlaveCmd;
    @CheckForNull
    private Integer launchTimeoutSeconds;
    @CheckForNull
    private Integer maxNumRetries;
    @CheckForNull
    private Integer retryWaitTime;

    @DataBoundConstructor
    public DockerComputerSSHConnector(SSHKeyStrategy sshKeyStrategy) {
        this.sshKeyStrategy = sshKeyStrategy;
        this.port = 22;
        this.maxNumRetries = 30;
        this.retryWaitTime = 2;
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

    @CheckForNull
    public String getJvmOptions() {
        return Util.fixEmptyAndTrim(jvmOptions);
    }

    @DataBoundSetter
    public void setJvmOptions(String jvmOptions) {
        this.jvmOptions = Util.fixEmptyAndTrim(jvmOptions);
    }

    @CheckForNull
    public String getJavaPath() {
        return Util.fixEmptyAndTrim(javaPath);
    }

    @DataBoundSetter
    public void setJavaPath(String javaPath) {
        this.javaPath = Util.fixEmptyAndTrim(javaPath);
    }

    @CheckForNull
    public String getPrefixStartSlaveCmd() {
        return Util.fixEmptyAndTrim(prefixStartSlaveCmd);
    }

    @DataBoundSetter
    public void setPrefixStartSlaveCmd(String prefixStartSlaveCmd) {
        this.prefixStartSlaveCmd = Util.fixEmptyAndTrim(prefixStartSlaveCmd);
    }

    @CheckForNull
    public String getSuffixStartSlaveCmd() {
        return Util.fixEmptyAndTrim(suffixStartSlaveCmd);
    }

    @DataBoundSetter
    public void setSuffixStartSlaveCmd(String suffixStartSlaveCmd) {
        this.suffixStartSlaveCmd = Util.fixEmptyAndTrim(suffixStartSlaveCmd);
    }

    @CheckForNull
    public Integer getLaunchTimeoutSeconds() {
        return launchTimeoutSeconds;
    }

    @DataBoundSetter
    public void setLaunchTimeoutSeconds(Integer launchTimeoutSeconds) {
        this.launchTimeoutSeconds = launchTimeoutSeconds;
    }

    @CheckForNull
    public Integer getMaxNumRetries() {
        return maxNumRetries;
    }

    @DataBoundSetter
    public void setMaxNumRetries(Integer maxNumRetries) {
        this.maxNumRetries = maxNumRetries;
    }

    @CheckForNull
    public Integer getRetryWaitTime() {
        return retryWaitTime;
    }

    @DataBoundSetter
    public void setRetryWaitTime(Integer retryWaitTime) {
        this.retryWaitTime = retryWaitTime;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hash(javaPath, jvmOptions, launchTimeoutSeconds, maxNumRetries, port,
                prefixStartSlaveCmd, retryWaitTime, sshKeyStrategy, suffixStartSlaveCmd);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        DockerComputerSSHConnector other = (DockerComputerSSHConnector) obj;
        return Objects.equals(javaPath, other.javaPath) && Objects.equals(jvmOptions, other.jvmOptions)
                && Objects.equals(launchTimeoutSeconds, other.launchTimeoutSeconds)
                && Objects.equals(maxNumRetries, other.maxNumRetries) && port == other.port
                && Objects.equals(prefixStartSlaveCmd, other.prefixStartSlaveCmd)
                && Objects.equals(retryWaitTime, other.retryWaitTime)
                && Objects.equals(sshKeyStrategy, other.sshKeyStrategy)
                && Objects.equals(suffixStartSlaveCmd, other.suffixStartSlaveCmd);
    }

    @Override
    public String toString() {
        // Maintenance node: This should list all the data we use in the equals()
        // method, but in the order the fields are declared in the class.
        // Note: If modifying this code, remember to update hashCode() and toString()
        final StringBuilder sb = startToString(this);
        bldToString(sb, "sshKeyStrategy", sshKeyStrategy);
        bldToString(sb, "port", port);
        bldToString(sb, "jvmOptions", jvmOptions);
        bldToString(sb, "javaPath", javaPath);
        bldToString(sb, "prefixStartSlaveCmd", prefixStartSlaveCmd);
        bldToString(sb, "suffixStartSlaveCmd", suffixStartSlaveCmd);
        bldToString(sb, "launchTimeoutSeconds", launchTimeoutSeconds);
        bldToString(sb, "maxNumRetries", maxNumRetries);
        bldToString(sb, "retryWaitTime", retryWaitTime);
        endToString(sb);
        return sb.toString();
    }

    @Override
    public void beforeContainerCreated(DockerAPI api, String workdir, CreateContainerCmd cmd) throws IOException, InterruptedException {
        // TODO define a strategy for SSHD process configuration so we support more than openssh's sshd
        final String[] cmdArray = cmd.getCmd();
        if (cmdArray == null || cmdArray.length == 0) {
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
            final String authorizedKeysCommand = "#!/bin/sh\n"
                    + "[ \"$1\" = \"" + sshKeyStrategy.getUser() + "\" ] "
                    + "&& echo '" + key + "'"
                    + "|| :";
            final byte[] authorizedKeysCommandAsBytes = authorizedKeysCommand.getBytes(StandardCharsets.UTF_8);
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 TarArchiveOutputStream tar = new TarArchiveOutputStream(bos)) {
                TarArchiveEntry entry = new TarArchiveEntry("authorized_key");
                entry.setSize(authorizedKeysCommandAsBytes.length);
                entry.setMode(0700);
                tar.putArchiveEntry(entry);
                tar.write(authorizedKeysCommandAsBytes);
                tar.closeArchiveEntry();
                tar.close();
                try (InputStream is = new ByteArrayInputStream(bos.toByteArray());
                     DockerClient client = api.getClient()) {
                    client.copyArchiveToContainerCmd(containerId)
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
        final PortUtils.ConnectionCheck connectionCheck = PortUtils.connectionCheck( address );
        final PortUtils.ConnectionCheckSSH connectionCheckSSH = connectionCheck.useSSH();
        final Integer maxNumRetriesOrNull = getMaxNumRetries();
        if ( maxNumRetriesOrNull!=null ) {
            connectionCheck.withRetries( maxNumRetriesOrNull );
        }
        final Integer retryWaitTimeOrNull = getRetryWaitTime();
        if ( retryWaitTimeOrNull!=null ) {
            connectionCheck.withEveryRetryWaitFor( retryWaitTimeOrNull, TimeUnit.SECONDS );
        }
        final Integer sshTimeoutSeconds = getLaunchTimeoutSeconds();
        if( sshTimeoutSeconds != null) {
            connectionCheckSSH.withSSHTimeout(sshTimeoutSeconds, TimeUnit.SECONDS);
        }
        final long timestampBeforeConnectionCheck = System.nanoTime();
        if (!connectionCheck.execute() || !connectionCheckSSH.execute()) {
            final long timestampAfterConnectionCheckEnded = System.nanoTime();
            final long nanosecondsElapsed = timestampAfterConnectionCheckEnded - timestampBeforeConnectionCheck;
            final long secondsElapsed = TimeUnit.NANOSECONDS.toSeconds(nanosecondsElapsed);
            final long millisecondsElapsed = TimeUnit.NANOSECONDS.toMillis(nanosecondsElapsed) - TimeUnit.SECONDS.toMillis(secondsElapsed);
            throw new IOException("SSH service hadn't started after " + secondsElapsed + " seconds and "
                    + millisecondsElapsed + " milliseconds."
                    + "Try increasing the number of retries (currently " + maxNumRetriesOrNull
                    + ") and/or the retry wait time (currently " + retryWaitTimeOrNull
                    + ") to allow for containers taking longer to start.");
        }
        return sshKeyStrategy.getSSHLauncher(address, this);
    }


    private static InetSocketAddress getBindingForPort(DockerAPI api, InspectContainerResponse ir, int internalPort) {
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

    private static String getExternalIP(DockerAPI api, InspectContainerResponse ir, NetworkSettings networkSettings,
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
        }
        return uri.getHost();
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
        @Override
        public abstract boolean equals(Object obj); // force subclasses to implement this
        @Override
        public abstract int hashCode(); // force subclasses to implement this
        @Override
        public abstract String toString(); // force subclasses to implement this
    }

    public static class InjectSSHKey extends SSHKeyStrategy {
        private final String user;

        @DataBoundConstructor
        public InjectSSHKey(String user) {
            this.user = user;
        }

        @Override
        public String getUser() {
            return user;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            InjectSSHKey other = (InjectSSHKey) obj;
            return Objects.equals(user, other.user);
        }

        @Override
        public int hashCode() {
            return Objects.hash(user);
        }

        @Override
        public String toString() {
            final StringBuilder sb = startToString(this);
            bldToString(sb, "user", user);
            endToString(sb);
            return sb.toString();
        }

        @Override
        public ComputerLauncher getSSHLauncher(InetSocketAddress address, DockerComputerSSHConnector connector) throws IOException {
            final InstanceIdentity id = InstanceIdentity.get();
            final String pem = PEMEncodable.create(id.getPrivate()).encode();
            return new DockerSSHLauncher(address.getHostString(), address.getPort(), user, pem,
                    connector.getJvmOptions(), connector.getJavaPath(), connector.getPrefixStartSlaveCmd(), connector.getSuffixStartSlaveCmd(),
                    connector.getLaunchTimeoutSeconds(), connector.getMaxNumRetries(), connector.getRetryWaitTime(),
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
        public int hashCode() {
            return Objects.hash(credentialsId, sshHostKeyVerificationStrategy);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ManuallyConfiguredSSHKey other = (ManuallyConfiguredSSHKey) obj;
            return Objects.equals(credentialsId, other.credentialsId)
                    && Objects.equals(sshHostKeyVerificationStrategy, other.sshHostKeyVerificationStrategy);
        }

        @Override
        public String toString() {
            final StringBuilder sb = startToString(this);
            bldToString(sb, "credentialsId", credentialsId);
            bldToString(sb, "sshHostKeyVerificationStrategy", sshHostKeyVerificationStrategy);
            endToString(sb);
            return sb.toString();
        }

        @Override
        public ComputerLauncher getSSHLauncher(InetSocketAddress address, DockerComputerSSHConnector connector) throws IOException {
            return new SSHLauncher(address.getHostString(), address.getPort(), getCredentialsId(),
                    connector.getJvmOptions(), connector.getJavaPath(), connector.getPrefixStartSlaveCmd(), connector.getSuffixStartSlaveCmd(),
                    connector.getLaunchTimeoutSeconds(), connector.getMaxNumRetries(), connector.getRetryWaitTime(),
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

            public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context, @QueryParameter String credentialsId) {
                if ( !hasPermission(context)) {
                    return new StandardUsernameListBoxModel()
                            .includeCurrentValue(credentialsId);
                }
                // Functionally the same as SSHLauncher's descriptor method, but without
                // filtering by host/port as we don't/can't know those yet.
                return new StandardUsernameListBoxModel()
                        .includeMatchingAs(
                                ACL.SYSTEM,
                                context,
                                StandardUsernameCredentials.class,
                                Collections.emptyList(),
                                SSHAuthenticator.matcher(Connection.class))
                        .includeCurrentValue(credentialsId);
            }

            private boolean hasPermission(Item context) {
                if (context != null) {
                    return context.hasPermission(Item.CONFIGURE);
                }
                return Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER);
            }
        }
    }

    /**
     * Custom flavour of SSHLauncher which allows to inject some custom credentials without this one being stored
     * in a CredentialStore
     */
    private static class DockerSSHLauncher extends SSHLauncher {
        private static final String CREDENTIAL_ID = "InstanceIdentity";
        private String user;
        private String privateKey;

        public DockerSSHLauncher(String host, int port, String user, String privateKey, String jvmOptions, String javaPath, String prefixStartSlaveCmd, String suffixStartSlaveCmd, Integer launchTimeoutSeconds, Integer maxNumRetries, Integer retryWaitTime, SshHostKeyVerificationStrategy sshHostKeyVerificationStrategy) {
            super(host, port, CREDENTIAL_ID, jvmOptions, javaPath, prefixStartSlaveCmd, suffixStartSlaveCmd, launchTimeoutSeconds, maxNumRetries, retryWaitTime, sshHostKeyVerificationStrategy);
            this.user = user;
            this.privateKey = privateKey;
        }

        @Override
        public StandardUsernameCredentials getCredentials() {
            return makeCredentials(CREDENTIAL_ID, user, privateKey);
        }
    }

    @Restricted(NoExternalUse.class)
    static StandardUsernameCredentials makeCredentials(String credId, String user, String privateKey) {
        return new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, credId, user,
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKey), null,
                "private key for docker ssh agent");
    }
}
