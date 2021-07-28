package com.nirima.jenkins.plugins.docker.launcher;

import hudson.plugins.sshslaves.SSHConnector;
import io.jenkins.docker.connector.DockerComputerConnector;
import io.jenkins.docker.connector.DockerComputerSSHConnector;

/**
 * Configurable SSH launcher that expected ssh port to be exposed from docker container.
 */
@Deprecated
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = { "UWF_UNWRITTEN_FIELD",
        "UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD",
        "NP_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD" }, justification = "Deprecated; required for backwards compatibility only.")
public class DockerComputerSSHLauncher extends DockerComputerLauncher {

    protected SSHConnector sshConnector;

    private Boolean useSSHKey;

    private String user;

    public boolean isUseSSHKey() {
        return useSSHKey != null && useSSHKey;
    }

    @Override
    public DockerComputerConnector convertToConnector() {
        DockerComputerSSHConnector.SSHKeyStrategy strategy =
                isUseSSHKey() ? new DockerComputerSSHConnector.InjectSSHKey(user)
                          : new DockerComputerSSHConnector.ManuallyConfiguredSSHKey(sshConnector.getCredentialsId(), null);
        final DockerComputerSSHConnector connector = new DockerComputerSSHConnector(strategy);
        connector.setJavaPath(sshConnector.getJavaPath());
        connector.setJvmOptions(sshConnector.getJvmOptions());
        connector.setLaunchTimeoutSeconds(sshConnector.getLaunchTimeoutSeconds());
        connector.setPort(sshConnector.getPort());
        connector.setPrefixStartSlaveCmd(sshConnector.getPrefixStartSlaveCmd());
        connector.setSuffixStartSlaveCmd(sshConnector.getSuffixStartSlaveCmd());

        return connector;
    }
}
