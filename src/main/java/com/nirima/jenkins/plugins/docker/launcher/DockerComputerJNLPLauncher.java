package com.nirima.jenkins.plugins.docker.launcher;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jenkins.docker.connector.DockerComputerConnector;
import io.jenkins.docker.connector.DockerComputerJNLPConnector;

/**
 * JNLP launcher. Doesn't require open ports on docker host.
 * <p>
 * Steps:
 * - runs container with nop command
 * - as launch action executes jnlp connection to master
 * </p>
 *
 * @author Kanstantsin Shautsou
 */
@SuppressFBWarnings(
        value = "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD",
        justification = "Deprecated; required for backwards compatibility only.")
@Deprecated
public class DockerComputerJNLPLauncher extends DockerComputerLauncher {

    protected String user;

    @Override
    public DockerComputerConnector convertToConnector() {
        final DockerComputerJNLPConnector connector = new DockerComputerJNLPConnector();
        connector.setUser(user);
        return connector;
    }
}
