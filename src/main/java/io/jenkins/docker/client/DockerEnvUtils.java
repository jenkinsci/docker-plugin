package io.jenkins.docker.client;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import com.github.dockerjava.api.command.CreateContainerCmd;

public class DockerEnvUtils {

    private DockerEnvUtils() {
    }

    /**
     * Adds (or updates) an environment variable to the list of environment
     * variables being passed into a create-container command.
     * 
     * @param envName
     *            The name of the environment variable to set.
     * @param envValue
     *            The value to set it to.
     * @param cmd
     *            The {@link CreateContainerCmd} whose environment settings are
     *            to be adjusted.
     */
    @Restricted(NoExternalUse.class)
    public static void addEnvToCmd(String envName, String envValue, CreateContainerCmd cmd) {
        final String[] oldEnvsOrNull = cmd.getEnv();
        final String[] oldEnvs = oldEnvsOrNull == null ? new String[0] : oldEnvsOrNull;
        final List<String> envs = new ArrayList<>(oldEnvs.length);
        for (final String oldEnv : oldEnvs) {
            final int equalsIndex = oldEnv.indexOf('=');
            if (equalsIndex < 0) {
                envs.add(oldEnv);
            } else {
                final String oldEnvName = oldEnv.substring(0, equalsIndex);
                if (!oldEnvName.equals(envName)) {
                    envs.add(oldEnv);
                }
            }
        }
        envs.add(envName + '=' + envValue);
        final String[] newEnvs = envs.toArray(new String[envs.size()]);
        cmd.withEnv(newEnvs);
    }
}
