package io.jenkins.docker.client;

import static com.cloudbees.plugins.credentials.CredentialsScope.SYSTEM;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.dockerjava.api.DockerClient;
import hudson.model.Descriptor.FormException;
import hudson.util.Secret;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests for {@link DockerAPI#getClient()} credentials resolution.
 * <p>
 * These tests do not require a running Docker daemon: building a {@link DockerClient} only
 * builds the underlying HTTP client (and, when TLS is configured, its {@link javax.net.ssl.SSLContext}),
 * it never opens a connection to the configured endpoint.
 */
@WithJenkins
class DockerAPITest {

    private static final X500Name CA_SUBJECT = new X500Name("CN=docker-plugin-test-ca");
    private static final X500Name CLIENT_SUBJECT = new X500Name("CN=docker-plugin-test-client");

    /**
     * PEM material for a self-signed CA and a client certificate signed by it, generated once for
     * the whole test class. Generated at test time (rather than checked in as static fixture files)
     * so that no private key material, even a throwaway one, ever reaches source control.
     */
    private static final String CA_CERTIFICATE_PEM;

    private static final String CLIENT_CERTIFICATE_PEM;
    private static final String CLIENT_KEY_PEM;

    static {
        try {
            final KeyPair caKeyPair = generateRsaKeyPair();
            final X509CertificateHolder caCertificateHolder =
                    buildCertificate(CA_SUBJECT, CA_SUBJECT, caKeyPair.getPublic(), caKeyPair.getPrivate(), true);

            final KeyPair clientKeyPair = generateRsaKeyPair();
            final X509CertificateHolder clientCertificateHolder = buildCertificate(
                    CA_SUBJECT, CLIENT_SUBJECT, clientKeyPair.getPublic(), caKeyPair.getPrivate(), false);

            final JcaX509CertificateConverter certificateConverter = new JcaX509CertificateConverter();
            CA_CERTIFICATE_PEM = toPem(certificateConverter.getCertificate(caCertificateHolder));
            CLIENT_CERTIFICATE_PEM = toPem(certificateConverter.getCertificate(clientCertificateHolder));
            CLIENT_KEY_PEM = toPem(clientKeyPair.getPrivate());
        } catch (GeneralSecurityException | OperatorCreationException | IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Each test uses its own endpoint URI, because {@code DockerAPI}'s client cache is static
     * and shared by the whole JVM: reusing a URI across tests could make one test observe
     * another test's cached client.
     */
    private static String uniqueUri(String prefix) {
        return "tcp://" + prefix + "-" + UUID.randomUUID() + ".invalid:2376";
    }

    private static KeyPair generateRsaKeyPair() throws GeneralSecurityException {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    /** Builds an X.509 certificate for {@code subject}, signed by {@code issuerPrivateKey}. */
    private static X509CertificateHolder buildCertificate(
            X500Name issuer,
            X500Name subject,
            PublicKey subjectPublicKey,
            PrivateKey issuerPrivateKey,
            boolean isCertificateAuthority)
            throws OperatorCreationException, CertIOException {
        final Instant now = Instant.now();
        final JcaX509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now.minus(Duration.ofDays(1))),
                Date.from(now.plus(Duration.ofDays(365))),
                subject,
                subjectPublicKey);
        if (isCertificateAuthority) {
            certificateBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        }
        final ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(issuerPrivateKey);
        return certificateBuilder.build(signer);
    }

    private static String toPem(Object pemObject) throws IOException {
        final StringWriter writer = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(pemObject);
        }
        return writer.toString();
    }

    @Test
    void getClientThrowsWhenCredentialsIdDoesNotResolveToAnyCredentials(
            @SuppressWarnings("unused") JenkinsRule jenkins) {
        final String credentialsId = "missing-credentials-" + UUID.randomUUID();
        final DockerServerEndpoint endpoint = new DockerServerEndpoint(uniqueUri("missing"), credentialsId);
        final DockerAPI api = new DockerAPI(endpoint);

        final IllegalStateException thrown = assertThrows(IllegalStateException.class, api::getClient);

        assertTrue(
                thrown.getMessage().contains(credentialsId),
                "Exception message should mention the unresolved credentials id, but was: " + thrown.getMessage());
    }

    @Test
    void getClientKeepsThrowingWhenCalledAgainWithoutCachingAPlainHttpClient(
            @SuppressWarnings("unused") JenkinsRule jenkins) {
        final String credentialsId = "missing-credentials-" + UUID.randomUUID();
        final DockerServerEndpoint endpoint = new DockerServerEndpoint(uniqueUri("missing-repeat"), credentialsId);
        final DockerAPI api = new DockerAPI(endpoint);

        assertThrows(IllegalStateException.class, api::getClient);
        // A second call for the very same (uri, credentialsId) pair must throw again: nothing must
        // have been cached under that key by the first, failing call.
        assertThrows(IllegalStateException.class, api::getClient);
    }

    @Test
    void getClientThrowsWhenCredentialsIdResolvesToACredentialOfTheWrongType(
            @SuppressWarnings("unused") JenkinsRule jenkins) throws FormException {
        final String credentialsId = "wrong-type-" + UUID.randomUUID();
        SystemCredentialsProvider.getInstance()
                .getCredentials()
                .add(new UsernamePasswordCredentialsImpl(SYSTEM, credentialsId, "test", "user", "password"));

        final DockerServerEndpoint endpoint = new DockerServerEndpoint(uniqueUri("wrong-type"), credentialsId);
        final DockerAPI api = new DockerAPI(endpoint);

        final IllegalStateException thrown = assertThrows(IllegalStateException.class, api::getClient);

        assertTrue(
                thrown.getMessage().contains(credentialsId),
                "Exception message should mention the unresolved credentials id, but was: " + thrown.getMessage());
    }

    @Test
    void getClientDoesNotThrowWhenCredentialsIdIsNull(@SuppressWarnings("unused") JenkinsRule jenkins) {
        final DockerServerEndpoint endpoint = new DockerServerEndpoint(uniqueUri("plain"), null);
        final DockerAPI api = new DockerAPI(endpoint);

        assertDoesNotThrow(() -> {
            try (DockerClient client = api.getClient()) {
                assertNotNull(client);
            }
        });
    }

    @Test
    void getClientBuildsATlsClientOnceAValidCredentialIsAddedForTheSameEndpoint(
            @SuppressWarnings("unused") JenkinsRule jenkins) {
        final String credentialsId = "will-be-added-" + UUID.randomUUID();
        final DockerServerEndpoint endpoint = new DockerServerEndpoint(uniqueUri("becomes-valid"), credentialsId);
        final DockerAPI api = new DockerAPI(endpoint);

        // Before the credential exists, resolving the client must fail...
        assertThrows(IllegalStateException.class, api::getClient);

        // ...and once it has been added, the very same endpoint must build a working TLS client:
        // SSLConfig#getSSLContext() is called eagerly while building the client, so this would
        // throw here if the credential had not really been picked up.
        final Secret clientKey = Secret.fromString(CLIENT_KEY_PEM);
        SystemCredentialsProvider.getInstance()
                .getCredentials()
                .add(new DockerServerCredentials(
                        SYSTEM, credentialsId, "test", clientKey, CLIENT_CERTIFICATE_PEM, CA_CERTIFICATE_PEM));

        assertDoesNotThrow(() -> {
            try (DockerClient client = api.getClient()) {
                assertNotNull(client);
            }
        });
    }
}
