package com.nirima.jenkins.plugins.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author Slawomir Jaranowski
 */
class DockerImagePullStrategyTest {

    static Object[][] data() {
        return new Object[][] {
            {false, "repo/name:latest", DockerImagePullStrategy.PULL_LATEST, true},
            {true, "repo/name:latest", DockerImagePullStrategy.PULL_LATEST, true},
            {false, "repo/name:1.0", DockerImagePullStrategy.PULL_LATEST, true},
            {true, "repo/name:1.0", DockerImagePullStrategy.PULL_LATEST, false},
            {false, "repo/name:latest", DockerImagePullStrategy.PULL_ALWAYS, true},
            {true, "repo/name:latest", DockerImagePullStrategy.PULL_ALWAYS, true},
            {false, "repo/name:1.0", DockerImagePullStrategy.PULL_ALWAYS, true},
            {true, "repo/name:1.0", DockerImagePullStrategy.PULL_ALWAYS, true},
            {false, "repo/name:latest", DockerImagePullStrategy.PULL_NEVER, false},
            {true, "repo/name:latest", DockerImagePullStrategy.PULL_NEVER, false},
            {false, "repo/name:1.0", DockerImagePullStrategy.PULL_NEVER, false},
            {true, "repo/name:1.0", DockerImagePullStrategy.PULL_NEVER, false},
        };
    }

    @ParameterizedTest(name = "existing image: ''{0}'', image to pull: ''{1}'', strategy: ''{2}''")
    @MethodSource("data")
    void shouldPullImageTest(
            boolean existedImage, String imageName, DockerImagePullStrategy pullStrategy, boolean shouldPull) {
        DockerClient dockerClient = mockDockerClient(existedImage, imageName);
        assertEquals(shouldPull, pullStrategy.shouldPullImage(dockerClient, imageName));
    }

    private DockerClient mockDockerClient(boolean existedImage, String imageName) {
        InspectImageCmd cmd = mock(InspectImageCmd.class);
        if (existedImage) {
            when(cmd.exec()).thenReturn(mock(InspectImageResponse.class));
        } else {
            when(cmd.exec()).thenThrow(new NotFoundException("not found"));
        }
        DockerClient dockerClient = mock(DockerClient.class);
        when(dockerClient.inspectImageCmd(imageName)).thenReturn(cmd);

        return dockerClient;
    }
}
