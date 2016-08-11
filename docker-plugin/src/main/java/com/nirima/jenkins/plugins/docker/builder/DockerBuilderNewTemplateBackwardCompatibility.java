package com.nirima.jenkins.plugins.docker.builder;

import com.nirima.jenkins.plugins.docker.DockerImagePullStrategy;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import com.nirima.jenkins.plugins.docker.launcher.DockerComputerSSHLauncher;
import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import hudson.model.Node;
import hudson.plugins.sshslaves.SSHConnector;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.Builder;

/**
 * @author Kanstantsin Shautsou
 */
public abstract class DockerBuilderNewTemplateBackwardCompatibility extends Builder {
    /**
     * @deprecated
     */
    @Deprecated
    protected transient String image,
            labelString,
            remoteFsMapping,
            remoteFs,
            credentialsId,
            idleTerminationMinutes,
            sshLaunchTimeoutMinutes,
            jvmOptions,
            javaPath,
            prefixStartSlaveCmd,
            suffixStartSlaveCmd,
            instanceCapStr,
            dnsString,
            network,
            dockerCommand,
            volumesString,
            volumesFrom,
            environmentsString,
            lxcConfString,
            bindPorts,
            hostname,
            macAddress;

    @Deprecated
    protected transient RetentionStrategy retentionStrategy;

    @Deprecated
    protected transient Integer memoryLimit, memorySwap, cpuShares;

    @Deprecated
    protected transient boolean bindAllPorts, privileged, tty;


    public abstract void setDockerTemplate(DockerTemplate dockerTemplate);

    protected void convert1() {
        final DockerTemplateBase dockerTemplateBase = new DockerTemplateBase(image, dnsString, network, dockerCommand,
                volumesString, null, environmentsString, lxcConfString,
                hostname, memoryLimit, memorySwap, cpuShares, bindPorts, bindAllPorts, privileged, tty, macAddress
        );

        final DockerComputerSSHLauncher dockerComputerSSHLauncher = new DockerComputerSSHLauncher(
                new SSHConnector(22, credentialsId, jvmOptions, javaPath, prefixStartSlaveCmd, suffixStartSlaveCmd,
                        Integer.parseInt(sshLaunchTimeoutMinutes) * 60)
        );

        final DockerTemplate dockerTemplate = new DockerTemplate(dockerTemplateBase, labelString, remoteFs,
                remoteFsMapping, instanceCapStr, Node.Mode.NORMAL, 1, dockerComputerSSHLauncher,
                new DockerOnceRetentionStrategy(10), false, DockerImagePullStrategy.PULL_LATEST);
        setDockerTemplate(dockerTemplate);
    }

}
