package io.jenkins.docker.connector;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import com.nirima.jenkins.plugins.docker.launcher.DockerComputerLauncher;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.util.ListBoxModel;
import jenkins.bouncycastle.api.PEMEncodable;
import jenkins.model.Jenkins;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;

import static com.trilead.ssh2.signature.RSASHA1Verify.encodeSSHRSAPublicKey;
import static hudson.remoting.Base64.encode;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerComputerSSHConnector extends DockerComputerLauncher {

    private final SSHKeyStrategy sshKeyStrategy;
    private int port;
    private String jvmOptions;
    private String javaPath;
    private String prefixStartSlaveCmd;
    private String suffixStartSlaveCmd;
    private Integer launchTimeoutSeconds;

    @DataBoundConstructor
    public DockerComputerSSHConnector(SSHKeyStrategy sshKeyStrategy) {
        this.sshKeyStrategy = sshKeyStrategy;
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

    public SSHLauncher launch(String host, int port) throws IOException, InterruptedException {
        return new SSHLauncher(host, port, sshKeyStrategy.getCredentials(), jvmOptions, javaPath, null, prefixStartSlaveCmd,
                suffixStartSlaveCmd, launchTimeoutSeconds);
    }


    @Extension
    public static final class DescriptorImpl extends Descriptor<DockerComputerLauncher> {

        @Override
        public String getDisplayName() {
            return "Docker SSH computer launcher";
        }

        public List getSSHKeyStrategyDescriptors() {
            return Jenkins.getInstance().getDescriptorList(SSHKeyStrategy.class);
        }
    }

    public abstract static class SSHKeyStrategy extends AbstractDescribableImpl<SSHKeyStrategy> {

        /** Credentials to use for SSHLauncher to establish a connection */
        public abstract StandardUsernameCredentials getCredentials() throws IOException;

        public abstract String getInjectedKey() throws IOException;

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
        public StandardUsernameCredentials getCredentials() throws IOException {
            InstanceIdentity id = InstanceIdentity.get();
            String pem = PEMEncodable.create(id.getPrivate()).encode();

            // TODO find how to inject authorized_key owned by a (configurable) user "jenkins"
            return new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, "InstanceIdentity", user,
                    new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(pem), null,
                    "private key for docker ssh agent");
        }

        @Override
        public String getInjectedKey() throws IOException {
            InstanceIdentity id = InstanceIdentity.get();
            final java.security.interfaces.RSAPublicKey rsa = id.getPublic();
            return "ssh-rsa " + encode(encodeSSHRSAPublicKey(new com.trilead.ssh2.signature.RSAPublicKey(rsa.getPublicExponent(), rsa.getModulus())));
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

        @DataBoundConstructor
        public ManuallyConfiguredSSHKey(String credentialsId) {
            this.credentialsId = credentialsId;
        }

        public String getCredentialsId() {
            return credentialsId;
        }

        @Override
        public StandardUsernameCredentials getCredentials() throws IOException {
            return SSHLauncher.lookupSystemCredentials(credentialsId);
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


}
