package io.jenkins.docker.client;

/**
 * Thrown when a credentials id configured on a Docker endpoint does not resolve to global Docker
 * Server Credentials. This may be transient (e.g. while the credentials are recreated during Jenkins
 * startup) or a configuration error; either way no client is built rather than one that silently
 * falls back to plain HTTP.
 */
public class MissingDockerServerCredentialsException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public MissingDockerServerCredentialsException(String credentialsId) {
        super("No Docker Server Credentials found with id '" + credentialsId + "'");
    }
}
