package com.nirima.jenkins.plugins.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.logging.Level;

/**
 * Deprecated variables
 *
 * @author Kanstantsin Shautsou
 */
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
    @Deprecated protected String idleTerminationMinutes;

//
//     SSH Launcher settings
//
    /**
     * The id of the credentials to use.
     */
    @Deprecated protected String credentialsId;

    /**
     * Minutes before SSHLauncher times out on launch
     */
    @Deprecated protected String sshLaunchTimeoutMinutes;

    /**
     * Field jvmOptions.
     */
    @Deprecated protected String jvmOptions;

    /**
     * Field javaPath.
     */
    @Deprecated protected String javaPath;

    /**
     * Field prefixStartSlaveCmd.
     */
    @Deprecated protected String prefixStartSlaveCmd;

    /**
     * Field suffixStartSlaveCmd.
     */
    @Deprecated protected String suffixStartSlaveCmd;

//
//     DockerTemplateBase values
//
    @Deprecated protected String image;

    /**
     * Field dockerCommand
     */
    @Deprecated protected String dockerCommand;

    /**
     * Field lxcConfString
     */
    @Deprecated protected String lxcConfString;

    @Deprecated protected String hostname;

    @Deprecated protected String[] dnsHosts;

    /**
     * Every String is volume specification
     */
    @Deprecated protected String[] volumes;

    /**
     * @deprecated use {@link #volumesFrom2}
     */
    @Deprecated protected String volumesFrom;

    /**
     * Every String is volumeFrom specification
     */
    @Deprecated protected String[] volumesFrom2;

    @Deprecated protected String[] environment;

    @Deprecated protected String bindPorts;
    @Deprecated protected boolean bindAllPorts;

    @Deprecated protected Integer memoryLimit;
    @Deprecated protected Integer cpuShares;

    @Deprecated protected boolean privileged;
    @Deprecated protected boolean tty;

    @Deprecated protected String macAddress;

    @Deprecated
    protected int getSSHLaunchTimeoutMinutes() {
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
    public int getIdleTerminationMinutes() {
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

}