package io.jenkins.docker.client;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Version;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.SSLConfig;
import com.github.dockerjava.netty.NettyDockerCmdExecFactory;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Collections;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.*;
import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerAPI extends AbstractDescribableImpl<DockerAPI> implements Serializable {

    private static final long serialVersionUID = 1L;


    private DockerServerEndpoint dockerHost;

    private int connectTimeout;

    private String apiVersion;

    private String hostname;

    private transient DockerClient client = null;

    @DataBoundConstructor
    public DockerAPI(DockerServerEndpoint dockerHost) {
        this.dockerHost = dockerHost;
    }

    public DockerAPI(DockerServerEndpoint dockerHost, int connectTimeout, String apiVersion, String hostname) {
        this.dockerHost = dockerHost;
        this.connectTimeout = connectTimeout;
        this.apiVersion = apiVersion;
        this.hostname = hostname;
    }

    public DockerServerEndpoint getDockerHost() {
        return dockerHost;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    @DataBoundSetter
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    @DataBoundSetter
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getHostname() {
        return hostname;
    }

    @DataBoundSetter
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public DockerClient getClient() {
        if (client == null) {
            client = DockerClientBuilder.getInstance(
                new DefaultDockerClientConfig.Builder()
                    .withDockerHost(dockerHost.getUri())
                    .withCustomSslConfig(toSSlConfig(dockerHost.getCredentialsId()))
                )
                .withDockerCmdExecFactory(new NettyDockerCmdExecFactory()
                .withConnectTimeout(connectTimeout))
                .build();
        }
        return client;
    }

    private static SSLConfig toSSlConfig(String credentialsId) {
        if (credentialsId == null) return null;
        
        DockerServerCredentials credentials = firstOrNull(
            lookupCredentials(
                DockerServerCredentials.class,
                Jenkins.getInstance(),
                ACL.SYSTEM,
                Collections.<DomainRequirement>emptyList()),
            withId(credentialsId));
        return credentials == null ? null :
            new DockerServerCredentialsSSLConfig(credentials);
    }

    /**
     * Create a plain {@link Socket} to docker API endpoint
     */
    public Socket getSocket() throws IOException {

        try {
            final URI uri = new URI(dockerHost.getUri());
            if ("unix".equals(uri.getScheme())) {
                final AFUNIXSocketAddress unix = new AFUNIXSocketAddress(new File("/var/run/docker.sock"));
                final Socket socket = AFUNIXSocket.newInstance();
                socket.connect(unix);
                return socket;
            }

            final SSLConfig sslConfig = toSSlConfig(dockerHost.getCredentialsId());
            if (sslConfig != null) {
                return sslConfig.getSSLContext().getSocketFactory().createSocket(uri.getHost(), uri.getPort());
            } else {
                return new Socket(uri.getHost(), uri.getPort());
            }
        } catch (Exception e) {
            throw new IOException("Failed to create a Socker for docker URI " + dockerHost.getUri(), e);
        }


    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerAPI> {

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context, @QueryParameter String value) {
            AccessControlled ac = (context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance());
            if (!ac.hasPermission(Jenkins.ADMINISTER)) {
                return new StandardListBoxModel().includeCurrentValue(value);
            }

            return new StandardListBoxModel().includeAs(
                    ACL.SYSTEM, context, DockerServerCredentials.class,
                    Collections.<DomainRequirement>emptyList());
        }

        public FormValidation doTestConnection(
                @QueryParameter String uri,
                @QueryParameter String credentialsId,
                @QueryParameter String apiVersion,
                @QueryParameter Integer connectTimeout
        ) throws IOException, ServletException, DockerException {
            try {
                DockerClient dc = new DockerAPI(new DockerServerEndpoint(uri, credentialsId), connectTimeout, apiVersion, null)
                        .getClient();
                Version verResult = dc.versionCmd().exec();

                return FormValidation.ok("Version = " + verResult.getVersion() + ", API Version = " + verResult.getApiVersion());
            } catch (Exception e) {
                return FormValidation.error(e, e.getMessage());
            }
        }
    }
}
