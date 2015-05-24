package com.nirima.jenkins.plugins.docker.client;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.CertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.KeystoreSSLConfig;
import com.github.dockerjava.jaxrs.DockerCmdExecFactoryImpl;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import hudson.security.ACL;
import jenkins.model.Jenkins;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.firstOrNull;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.withId;
import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;
import static com.github.dockerjava.core.DockerClientConfig.createDefaultConfigBuilder;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * @author lanwen (Merkushev Kirill)
 */
public class ClientBuilderForPlugin {

    private static final Logger LOGGER = Logger.getLogger(ClientBuilderForPlugin.class.getName());

    private DockerClientConfig.DockerClientConfigBuilder config = createDefaultConfigBuilder();

    private ClientBuilderForPlugin() {
    }

    public static ClientBuilderForPlugin dockerClient() {
        return new ClientBuilderForPlugin();
    }

    /**
     * Provides ready to use docker client with information from docker cloud
     *
     * @param cloud docker cloud with info about url, version, creds and timeout
     *
     * @return docker-java client
     */
    public DockerClient forCloud(DockerCloud cloud) {
        LOGGER.log(Level.FINE, "Building connection to docker host \"{0}\" at: {1}",
                new Object[]{cloud.getDisplayName(), cloud.serverUrl});

        forServer(cloud.serverUrl, cloud.version);

        if (cloud.readTimeout > 0) {
            config.withReadTimeout((int) SECONDS.toMillis(cloud.readTimeout));
        }

        return withCredentials(cloud.credentialsId);
    }

    /**
     * Method to setup url and docker-api version. Convenient for test-connection purposes and quick requests
     *
     * @param uri     docker server uri
     * @param version docker-api version
     *
     * @return this builder
     */
    public ClientBuilderForPlugin forServer(String uri, @Nullable String version) {
        config.withUri(URI.create(uri).toString())
                .withVersion(version);
        return this;
    }

    /**
     * Sets username and password or ssl config by credentials id
     *
     * @param credentialsId credentials to find in jenkins
     *
     * @return docker-java client
     */
    public DockerClient withCredentials(String credentialsId) {
        if (isNotBlank(credentialsId)) {
            Credentials credentials = lookupSystemCredentials(credentialsId);

            if (credentials instanceof CertificateCredentials) {
                CertificateCredentials certificateCredentials = (CertificateCredentials) credentials;
                config.withSSLConfig(new KeystoreSSLConfig(
                        certificateCredentials.getKeyStore(),
                        certificateCredentials.getPassword().getPlainText()
                ));
            } else if (credentials instanceof StandardUsernamePasswordCredentials) {
                StandardUsernamePasswordCredentials usernamePasswordCredentials =
                        ((StandardUsernamePasswordCredentials) credentials);

                config.withUsername(usernamePasswordCredentials.getUsername());
                config.withPassword(usernamePasswordCredentials.getPassword().getPlainText());
            }
        }
        return build();
    }

    /**
     * Return client with current config applied
     *
     * @return docker-java client
     */
    public DockerClient build() {
        return DockerClientBuilder.getInstance(config)
                .withDockerCmdExecFactory(new DockerCmdExecFactoryImpl())
                .build();
    }

    /**
     * For test purposes mostly
     *
     * @return docker config builder
     */
    /* package */ DockerClientConfig.DockerClientConfigBuilder config() {
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

}
