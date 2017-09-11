package com.nirima.jenkins.plugins.docker;

import com.google.common.base.Joiner;
import com.nirima.jenkins.plugins.docker.launcher.DockerComputerLauncher;
import com.nirima.jenkins.plugins.docker.launcher.DockerComputerSSHLauncher;
import hudson.plugins.sshslaves.SSHConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deprecated variables
 *
 * @author Kanstantsin Shautsou
 */
@SuppressWarnings({"deprecation", "UnusedDeclaration"})
public abstract class DockerTemplateBackwardCompatibility {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerTemplateBackwardCompatibility.class);

//
//   Retention Strategy
//
    /**
     * Minutes before terminating an idle slave
     *
     * @deprecated migrated to retention strategy?
     */
    @Deprecated
    private transient String idleTerminationMinutes;

//
//     SSH Launcher settings
//
    /**
     * The id of the credentials to use.
     */
    @Deprecated
    private transient String credentialsId;

    /**
     * Minutes before SSHLauncher times out on launch
     */
    @Deprecated
    private transient String sshLaunchTimeoutMinutes;

    /**
     * Field jvmOptions.
     */
    @Deprecated
    private transient String jvmOptions;

    /**
     * Field javaPath.
     */
    @Deprecated
    private transient String javaPath;

    /**
     * Field prefixStartSlaveCmd.
     */
    @Deprecated
    private transient String prefixStartSlaveCmd;

    /**
     * Field suffixStartSlaveCmd.
     */
    @Deprecated
    private transient String suffixStartSlaveCmd;

    //
//     DockerTemplateBase values
//
    @Deprecated
    private transient String image;

    /**
     * Field dockerCommand
     */
    @Deprecated
    private transient String dockerCommand;

    /**
     * Field lxcConfString
     */
    @Deprecated
    private transient String lxcConfString;

    @Deprecated
    private transient String hostname;

    @Deprecated
    private transient String[] dnsHosts;

    @Deprecated
    private transient String network;

    /**
     * Every String is volume specification
     */
    @Deprecated
    private transient String[] volumes;

    /**
     * @deprecated use {@link #volumesFrom2}
     */
    @Deprecated
    private transient String volumesFrom;

    /**
     * Every String is volumeFrom specification
     */
    @Deprecated
    private transient String[] volumesFrom2;

    @Deprecated
    private transient String[] environment;

    @Deprecated
    private transient String bindPorts;
    @Deprecated
    private transient boolean bindAllPorts;

    @Deprecated
    private transient Integer memoryLimit;
    @Deprecated
    private transient Integer memorySwap;
    @Deprecated
    private transient Integer cpuShares;

    @Deprecated
    private transient boolean privileged;
    @Deprecated
    private transient boolean tty;

    @Deprecated
    private transient String macAddress;

    @Deprecated
    private String getDnsString() {
        return Joiner.on(" ").join(dnsHosts);
    }

    @Deprecated
    private String getNetwork() {
        return network;
    }

    @Deprecated
    private String getVolumesString() {
        return Joiner.on("\n").join(volumes);
    }

    @Deprecated
    private String getEnvironmentsString() {
        return environment == null ? "" : Joiner.on("\n").join(environment);
    }

    @Deprecated
    private String[] getVolumesFrom2() {
        return DockerTemplateBase.filterStringArray(volumesFrom2);
    }

    @Deprecated
    private String getVolumesFromString() {
        return Joiner.on("\n").join(getVolumesFrom2());
    }


    @Deprecated
    private int getSSHLaunchTimeoutMinutes() {
        if (sshLaunchTimeoutMinutes == null || sshLaunchTimeoutMinutes.trim().isEmpty()) {
            return 1;
        } else {
            try {
                return Integer.parseInt(sshLaunchTimeoutMinutes);
            } catch (NumberFormatException nfe) {
                LOGGER.info("Malformed SSH Launch Timeout value: '{}'. Fallback to 1 min.", sshLaunchTimeoutMinutes);
                return 1;
            }
        }
    }

    /**
     * @deprecated migrated to retention strategy
     */
    @Deprecated
    private int getIdleTerminationMinutes() {
        if (idleTerminationMinutes == null || idleTerminationMinutes.trim().isEmpty()) {
            return 10;
        } else {
            try {
                return Integer.parseInt(idleTerminationMinutes);
            } catch (NumberFormatException nfe) {
                LOGGER.info("Malformed idleTermination value: '{}'. Fallback to 30.", idleTerminationMinutes);
                return 10;
            }
        }
    }


    public abstract void setLauncher(DockerComputerLauncher launcher);

    public abstract void setDockerTemplateBase(DockerTemplateBase dockerTemplateBase);

    protected void convert1() {
        // migrate launcher
        final SSHConnector sshConnector = new SSHConnector(22, credentialsId, jvmOptions, javaPath,
                prefixStartSlaveCmd, suffixStartSlaveCmd, getSSHLaunchTimeoutMinutes() * 60);
        setLauncher(new DockerComputerSSHLauncher(sshConnector));

        // migrate dockerTemplate
        setDockerTemplateBase(new DockerTemplateBase(image,
                        getDnsString(),
                        getNetwork(),
                        dockerCommand,
                        getVolumesString(),
                        volumesFrom != null ? volumesFrom : getVolumesFromString(),
                        getEnvironmentsString(),
                        lxcConfString,
                        hostname,
                        memoryLimit,
                        memorySwap,
                        cpuShares,
                        bindPorts,
                        bindAllPorts,
                        privileged,
                        tty,
                        macAddress)
        );
    }
}
