package com.nirima.jenkins.plugins.docker.launcher;

import hudson.plugins.sshslaves.SSHConnector;
import io.jenkins.docker.connector.DockerComputerSSHConnector;
import org.apache.commons.lang.StringUtils;

/**
 * Configurable SSH launcher that expected ssh port to be exposed from docker container.
 */
@Deprecated
public class DockerComputerSSHLauncher extends DockerComputerLauncher {

    protected final SSHConnector sshConnector;

    private Boolean useSSHKey;

    private String user;

    public DockerComputerSSHLauncher(SSHConnector sshConnector) {
        this.sshConnector = sshConnector;
    }

    public SSHConnector getSshConnector() {
        return sshConnector;
    }

    public void setUseSSHKey(boolean useSSHKey) {
        this.useSSHKey = useSSHKey;
    }

    public boolean isUseSSHkey() {
        return useSSHKey == null || useSSHKey.booleanValue();
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getUser() {
        return StringUtils.isEmpty(user) ? "root" : user;
    }


    private Object readResolve() {
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
