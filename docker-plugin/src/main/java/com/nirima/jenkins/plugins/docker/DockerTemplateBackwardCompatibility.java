package com.nirima.jenkins.plugins.docker;

import hudson.plugins.sshslaves.SSHConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shaded.com.google.common.base.Joiner;

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
    private String idleTerminationMinutes;

//
//     SSH Launcher settings
//
    /**
     * The id of the credentials to use.
     */
    @Deprecated
    private String credentialsId;

    /**
     * Minutes before SSHLauncher times out on launch
     */
    @Deprecated
    private String sshLaunchTimeoutMinutes;

    /**
     * Field jvmOptions.
     */
    @Deprecated
    private String jvmOptions;

    /**
     * Field javaPath.
     */
    @Deprecated
    private String javaPath;

    /**
     * Field prefixStartSlaveCmd.
     */
    @Deprecated
    private String prefixStartSlaveCmd;

    /**
     * Field suffixStartSlaveCmd.
     */
    @Deprecated
    private String suffixStartSlaveCmd;

    //
//     DockerTemplateBase values
//
    @Deprecated
    private String image;

    /**
     * Field dockerCommand
     */
    @Deprecated
    private String dockerCommand;

    /**
     * Field lxcConfString
     */
    @Deprecated
    private String lxcConfString;

    @Deprecated
    private String hostname;

    @Deprecated
    private String[] dnsHosts;

    /**
     * Every String is volume specification
     */
    @Deprecated
    private String[] volumes;

    /**
     * @deprecated use {@link #volumesFrom2}
     */
    @Deprecated
    private String volumesFrom;

    /**
     * Every String is volumeFrom specification
     */
    @Deprecated
    private String[] volumesFrom2;

    @Deprecated
    private String[] environment;

    @Deprecated
    private String bindPorts;
    @Deprecated
    private boolean bindAllPorts;

    @Deprecated
    private Integer memoryLimit;
    @Deprecated
    private Integer cpuShares;

    @Deprecated
    private boolean privileged;
    @Deprecated
    private boolean tty;

    @Deprecated
    private String macAddress;

    @Deprecated
    private String getDnsString() {
        return Joiner.on(" ").join(dnsHosts);
    }

    @Deprecated
    private String getVolumesString() {
        return Joiner.on("\n").join(volumes);
    }

    @Deprecated
    private String getEnvironmentsString() {
        return Joiner.on("\n").join(environment);
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
            return 0;
        } else {
            try {
                return Integer.parseInt(idleTerminationMinutes);
            } catch (NumberFormatException nfe) {
                LOGGER.info("Malformed idleTermination value: '{}'. Fallback to 30.", idleTerminationMinutes);
                return 30;
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
                        dockerCommand,
                        getVolumesString(),
                        getVolumesFromString(),
                        getEnvironmentsString(),
                        lxcConfString,
                        hostname,
                        memoryLimit,
                        cpuShares,
                        bindPorts,
                        bindAllPorts,
                        privileged,
                        tty,
                        macAddress
                )
        );
    }
}