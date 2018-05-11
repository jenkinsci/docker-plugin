package io.jenkins.docker.client;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;

import com.github.dockerjava.api.command.CreateContainerCmd;

public class DockerEnvUtilsTest {

    @Test
    public void addEnvToCmdGivenNullExistingEnvsThenSetsEnv() {
        // Given
        final CreateContainerCmd cmd = mock(CreateContainerCmd.class);
        final String[] existingEnvs = null;
        when(cmd.getEnv()).thenReturn(existingEnvs);
        final String envName = "name";
        final String envValue = "value";

        // When
        DockerEnvUtils.addEnvToCmd(envName, envValue, cmd);

        // Then
        verify(cmd, times(1)).withEnv("name=value");
    }

    @Test
    public void addEnvToCmdGivenEmptyExistingEnvsThenSetsEnv() {
        // Given
        final CreateContainerCmd cmd = mock(CreateContainerCmd.class);
        final String[] existingEnvs = new String[0];
        when(cmd.getEnv()).thenReturn(existingEnvs);
        final String envName = "name";
        final String envValue = "value";

        // When
        DockerEnvUtils.addEnvToCmd(envName, envValue, cmd);

        // Then
        verify(cmd, times(1)).withEnv("name=value");
    }

    @Test
    public void addEnvToCmdGivenExistingOtherEnvsThenAddsEnv() {
        // Given
        final CreateContainerCmd cmd = mock(CreateContainerCmd.class);
        final String[] existingEnvs = new String[]{
                "foo=bar",
                "flibble",
                "x="
        };
        when(cmd.getEnv()).thenReturn(existingEnvs);
        final String envName = "name";
        final String envValue = "value";

        // When
        DockerEnvUtils.addEnvToCmd(envName, envValue, cmd);

        // Then
        verify(cmd, times(1)).withEnv("foo=bar", "flibble", "x=", "name=value");
    }

    @Test
    public void addEnvToCmdGivenExistingClashingEnvsThenReplacesEnv() {
        // Given
        final CreateContainerCmd cmd = mock(CreateContainerCmd.class);
        final String[] existingEnvs = new String[]{
                "foo=bar",
                "name=oldvalue",
                "x="
        };
        when(cmd.getEnv()).thenReturn(existingEnvs);
        final String envName = "name";
        final String envValue = "value";

        // When
        DockerEnvUtils.addEnvToCmd(envName, envValue, cmd);

        // Then
        verify(cmd, times(1)).withEnv("foo=bar", "x=", "name=value");
    }

}
