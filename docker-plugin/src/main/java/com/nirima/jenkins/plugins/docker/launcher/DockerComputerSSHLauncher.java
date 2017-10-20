package com.nirima.jenkins.plugins.docker.launcher;

import hudson.plugins.sshslaves.SSHConnector;
import io.jenkins.docker.connector.DockerComputerConnector;
import io.jenkins.docker.connector.DockerComputerSSHConnector;

/**
 * Configurable SSH launcher that expected ssh port to be exposed from docker container.
 */
@Deprecated
public class DockerComputerSSHLauncher extends DockerComputerLauncher {

    protected SSHConnector sshConnector;

    private Boolean useSSHKey;

    private String user;

    public DockerComputerConnector convertToConnector() {
        DockerComputerSSHConnector.SSHKeyStrategy strategy =
                useSSHKey ? new DockerComputerSSHConnector.InjectSSHKey(user)
                          : new DockerComputerSSHConnector.ManuallyConfiguredSSHKey(sshConnector.getCredentialsId());
        final DockerComputerSSHConnector connector = new DockerComputerSSHConnector(strategy);
        connector.setJavaPath(sshConnector.javaPath);
        connector.setJvmOptions(sshConnector.jvmOptions);
        connector.setLaunchTimeoutSeconds(sshConnector.launchTimeoutSeconds);
        connector.setPort(sshConnector.port);
        connector.setPrefixStartSlaveCmd(sshConnector.suffixStartSlaveCmd);
        connector.setSuffixStartSlaveCmd(sshConnector.prefixStartSlaveCmd);

        return connector;
    }

}
