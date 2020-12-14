package com.nirima.jenkins.plugins.docker;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * @author Slawomir Jaranowski
 */
@RunWith(Parameterized.class)
public class DockerImagePullStrategyTest {

    private final String imageName;
    private final DockerImagePullStrategy pullStrategy;
    private final boolean shouldPull;
    private final boolean existedImage;

    @Parameterized.Parameters(name = "existing image: ''{0}'', image to pull: ''{1}'', strategy: ''{2}''")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
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
        });
    }

    @Test
    public void shouldPullImageTest() {
        DockerClient dockerClient = mockDockerClient();
        assertEquals(shouldPull, pullStrategy.shouldPullImage(dockerClient, imageName));
    }

    public DockerImagePullStrategyTest(boolean existedImage, String imageName, DockerImagePullStrategy pullStrategy, boolean shouldPull) {
        this.existedImage =existedImage;
        this.imageName = imageName;
        this.pullStrategy = pullStrategy;
        this.shouldPull = shouldPull;
    }

    private DockerClient mockDockerClient() {
        InspectImageCmd cmd = mock(InspectImageCmd.class);
        if (existedImage)
            when(cmd.exec()).thenReturn(mock(InspectImageResponse.class));
        else
            when(cmd.exec()).thenThrow(new NotFoundException("not found"));
        DockerClient dockerClient = mock(DockerClient.class);
        when(dockerClient.inspectImageCmd(imageName)).thenReturn(cmd);

        return dockerClient;
    }
}
