package io.jenkins.docker.client;

import com.github.dockerjava.core.SSLConfig;
import com.github.dockerjava.core.util.CertificateUtils;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;

/**
 + * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 + */
public class DockerServerCredentialsSSLConfig implements SSLConfig {
    private final DockerServerCredentials credentials;

    public DockerServerCredentialsSSLConfig(DockerServerCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    public SSLContext getSSLContext() throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {

        try {
            final KeyStore keyStore = CertificateUtils.createKeyStore(credentials.getClientKey(), credentials.getClientCertificate());
            final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, "docker".toCharArray());
            final KeyStore trustStore = CertificateUtils.createTrustStore(credentials.getServerCaCertificate());
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