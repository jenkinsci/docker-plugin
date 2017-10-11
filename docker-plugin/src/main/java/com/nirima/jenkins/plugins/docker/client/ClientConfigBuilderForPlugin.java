package com.nirima.jenkins.plugins.docker.client;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.CertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.KeystoreSSLConfig;
import com.github.dockerjava.core.LocalDirectorySSLConfig;
import com.github.dockerjava.core.SSLConfig;
import com.github.dockerjava.core.util.CertificateUtils;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.utils.DockerDirectoryCredentials;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.firstOrNull;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.withId;
import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;
import static com.github.dockerjava.core.DefaultDockerClientConfig.createDefaultConfigBuilder;
import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * @author lanwen (Merkushev Kirill)
 */
public class ClientConfigBuilderForPlugin {

    private static final Logger LOGGER = Logger.getLogger(ClientConfigBuilderForPlugin.class.getName());

    private DefaultDockerClientConfig.Builder config = createDefaultConfigBuilder();

    private ClientConfigBuilderForPlugin() {
    }

    public static ClientConfigBuilderForPlugin dockerClientConfig() {
        return new ClientConfigBuilderForPlugin();
    }

    /**
     * Provides ready to use docker client with information from docker cloud
     *
     * @param cloud docker cloud with info about url, version, creds and timeout
     *
     * @return docker-java client
     */
    public ClientConfigBuilderForPlugin forCloud(DockerCloud cloud) {
        LOGGER.log(Level.FINE, "Building connection to docker host \"{0}\" at: {1}",
                new Object[]{cloud.getDisplayName(), cloud.getDockerHost().getUri()});

        forServer(cloud.getDockerHost().getUri(), cloud.version);

        return withCredentials(cloud.getDockerHost().getCredentialsId());
    }

    /**
     * Method to setup url and docker-api version. Convenient for test-connection purposes and quick requests
     *
     * @param uri     docker server uri
     * @param version docker-api version
     *
     * @return this builder
     */
    public ClientConfigBuilderForPlugin forServer(String uri, @Nullable String version) {
        config.withDockerHost(URI.create(uri).toString())
                .withApiVersion(version);
        return this;
    }

    /**
     * Sets username and password or ssl config by credentials id
     *
     * @param credentialsId credentials to find in jenkins
     *
     * @return docker-java client
     */
    public ClientConfigBuilderForPlugin withCredentials(String credentialsId) {
        if (isNotBlank(credentialsId)) {
            Credentials credentials = lookupSystemCredentials(credentialsId);

            if (credentials instanceof DockerServerCredentials) {
                final DockerServerCredentials c = (DockerServerCredentials) credentials;
                config.withCustomSslConfig(new DockerServerCredentialsSSLConfig(c));
            }
            else if (credentials instanceof CertificateCredentials) {
                CertificateCredentials certificateCredentials = (CertificateCredentials) credentials;
                config.withCustomSslConfig(new KeystoreSSLConfig(
                        certificateCredentials.getKeyStore(),
                        certificateCredentials.getPassword().getPlainText()
                ));
            }
            else if( credentials instanceof DockerDirectoryCredentials) {
                DockerDirectoryCredentials ddc = (DockerDirectoryCredentials)credentials;
                config.withCustomSslConfig( new LocalDirectorySSLConfig(ddc.getPath()));

            } else if (credentials instanceof StandardUsernamePasswordCredentials) {
                StandardUsernamePasswordCredentials usernamePasswordCredentials =
                        ((StandardUsernamePasswordCredentials) credentials);

                // TODO: Don't think this makes sense. API change in docker-java
                // has exposed that this probably wasn't the intent!
                config.withRegistryUsername(usernamePasswordCredentials.getUsername());
                config.withRegistryPassword(usernamePasswordCredentials.getPassword().getPlainText());
            }
        }
        return this;
    }


    /**
     * Build the config
     */
    public DockerClientConfig build() {
        return config.build();
    }

    /**
     * Shortcut to build an actual client.
     *
     * Consider if you actually want to do this or alternatively
     * build the config then build the client, as if your activity is on a remote
     * node, the client will fail to serialize.
     */
    public DockerClient buildClient() {
        return ClientBuilderForPlugin.builder().withDockerClientConfig(build()).build();
    }

    /**
     * For test purposes mostly
     *
     * @return docker config builder
     */
    /* package */ DefaultDockerClientConfig.Builder config() {
        return config;
    }

    /**
     * Util method to find credential by id in jenkins
     *
     * @param credentialsId credentials to find in jenkins
     *
     * @return {@link CertificateCredentials} or {@link StandardUsernamePasswordCredentials} expected
     */
    private static Credentials lookupSystemCredentials(String credentialsId) {
        return firstOrNull(
                lookupCredentials(
                        Credentials.class,
                        Jenkins.getInstance(),
                        ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList()
                ),
                withId(credentialsId)
        );
    }

    private static class DockerServerCredentialsSSLConfig implements SSLConfig {
        private final DockerServerCredentials c;

        public DockerServerCredentialsSSLConfig(DockerServerCredentials c) {
            this.c = c;
        }

        @Override
        public SSLContext getSSLContext() throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {

            try {
                final KeyStore keyStore = CertificateUtils.createKeyStore(c.getClientKey(), c.getClientCertificate());
                final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, "docker".toCharArray());
                final KeyStore trustStore = CertificateUtils.createTrustStore(c.getServerCaCertificate());
                final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);

                final SSLContext context = SSLContext.getInstance("TLS");
                context.init(keyManagerFactory.getKeyManagers(),
                        trustManagerFactory.getTrustManagers(), null);
                return context;
            } catch (CertificateException | InvalidKeySpecException | IOException e) {
                throw new KeyStoreException("Can't build keystore from provided client key/certificate", e);
            }
        }
    }
}
