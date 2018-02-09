package io.jenkins.docker.client;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.VersionCmd;
import com.github.dockerjava.api.model.Version;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.SSLConfig;

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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.net.URI;
import java.util.Collections;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.*;
import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;
import static org.apache.commons.lang.StringUtils.trimToNull;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerAPI extends AbstractDescribableImpl<DockerAPI> implements Serializable, Closeable {

    private static final long serialVersionUID = 1L;


    private DockerServerEndpoint dockerHost;

    /** Connection timeout in seconds */
    private int connectTimeout;

    /** Read timeout in seconds */
    private int readTimeout;

    private String apiVersion;

    private String hostname;

    private transient DockerClient client = null;

    /**
     * Is this host actually a swarm?
     */
    private transient Boolean _isSwarm;

    @DataBoundConstructor
    public DockerAPI(DockerServerEndpoint dockerHost) {
        this.dockerHost = dockerHost;
    }

    public DockerAPI(DockerServerEndpoint dockerHost, int connectTimeout, int readTimeout, String apiVersion, String hostname) {
        this.dockerHost = dockerHost;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
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

    public int getReadTimeout() {
        return readTimeout;
    }

    @DataBoundSetter
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    @DataBoundSetter
    public void setApiVersion(String apiVersion) {
        this.apiVersion = trimToNull(apiVersion);
    }

    public String getHostname() {
        return hostname;
    }

    @DataBoundSetter
    public void setHostname(String hostname) {
        this.hostname = trimToNull(hostname);
    }

    public boolean isSwarm() {
        Version remoteVersion = getClient().versionCmd().exec();
        // Cache the return.
        if( _isSwarm == null ) {
            _isSwarm = remoteVersion.getVersion().startsWith("swarm");
        }
        return _isSwarm;
    }

    public DockerClient getClient() {
        if (client == null) {
            final Integer readTimeoutInMillisecondsOrNull = readTimeout > 0 ? Integer.valueOf(readTimeout * 1000) : null;
            final Integer connectTimeoutInMillisecondsOrNull = connectTimeout > 0 ? Integer.valueOf(connectTimeout * 1000) : null;
            client = DockerClientBuilder.getInstance(
                new DefaultDockerClientConfig.Builder()
                    .withDockerHost(dockerHost.getUri())
                    .withCustomSslConfig(toSSlConfig(dockerHost.getCredentialsId()))
                )
                .withDockerCmdExecFactory(new NettyDockerCmdExecFactory()
                .withReadTimeout(readTimeoutInMillisecondsOrNull)
                .withConnectTimeout(connectTimeoutInMillisecondsOrNull))
                .build();
        }
        return client;
    }

    @Override
    public void close() throws IOException {
        if (client != null) {
            client.close();
            client = null;
        }
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DockerAPI dockerAPI = (DockerAPI) o;

        if (connectTimeout != dockerAPI.connectTimeout) return false;
        if (readTimeout != dockerAPI.readTimeout) return false;
        if (dockerHost != null ? !dockerHost.equals(dockerAPI.dockerHost) : dockerAPI.dockerHost != null) return false;
        if (apiVersion != null ? !apiVersion.equals(dockerAPI.apiVersion) : dockerAPI.apiVersion != null) return false;
        if (hostname != null ? !hostname.equals(dockerAPI.hostname) : dockerAPI.hostname != null) return false;
        return client != null ? client.equals(dockerAPI.client) : dockerAPI.client == null;
    }

    @Override
    public int hashCode() {
        int result = dockerHost != null ? dockerHost.hashCode() : 0;
        result = 31 * result + connectTimeout;
        result = 31 * result + readTimeout;
        result = 31 * result + (apiVersion != null ? apiVersion.hashCode() : 0);
        result = 31 * result + (hostname != null ? hostname.hashCode() : 0);
        result = 31 * result + (client != null ? client.hashCode() : 0);
        return result;
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

        public FormValidation doCheckConnectionTimeout(@QueryParameter String value) {
            return FormValidation.validateNonNegativeInteger(value);
        }

        public FormValidation doCheckReadTimeout(@QueryParameter String value) {
            return FormValidation.validateNonNegativeInteger(value);
        }

        public FormValidation doTestConnection(
                @QueryParameter String uri,
                @QueryParameter String credentialsId,
                @QueryParameter String apiVersion,
                @QueryParameter int connectTimeout,
                @QueryParameter int readTimeout
        ) {
            try {
                final DockerServerEndpoint dsep = new DockerServerEndpoint(uri, credentialsId);
                final DockerAPI dapi = new DockerAPI(dsep, connectTimeout, readTimeout, apiVersion, null);
                try {
                    final DockerClient dc = dapi.getClient();
                    final VersionCmd vc = dc.versionCmd();
                    final Version v = vc.exec();
                    final String actualVersion = v.getVersion();
                    final String actualApiVersion = v.getApiVersion();
                    return FormValidation.ok("Version = " + actualVersion + ", API Version = " + actualApiVersion);
                } finally {
                    // nobody else can see this DockerAPI instance, we must close it
                    try {
                        dapi.close();
                    } catch (IOException ignored) {
                    }
                }
            } catch (Exception e) {
                return FormValidation.error(e, e.getMessage());
            }
        }
    }
}
