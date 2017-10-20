package com.nirima.jenkins.plugins.docker;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.model.Image;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * @author Slawomir Jaranowski
 */
@RunWith(Parameterized.class)
public class DockerImagePullStrategyTest {

    private final List<Image> imageList;
    private final String imageName;
    private final DockerImagePullStrategy pullStrategy;
    private final boolean shouldPull;

    @Parameterized.Parameters(name = "existing image: ''{0}'', image to pull: ''{1}'', strategy: ''{2}''")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"", "repo/name", DockerImagePullStrategy.PULL_LATEST, true},
                {"repo/name:latest", "repo/name", DockerImagePullStrategy.PULL_LATEST, true},
                {"", "repo/name:latest", DockerImagePullStrategy.PULL_LATEST, true},
                {"repo/name:latest", "repo/name:latest", DockerImagePullStrategy.PULL_LATEST, true},
                {"", "repo/name:1.0", DockerImagePullStrategy.PULL_LATEST, true},
                {"repo/name:1.0", "repo/name:1.0", DockerImagePullStrategy.PULL_LATEST, false},

                {"", "repo/name", DockerImagePullStrategy.PULL_ALWAYS, true},
                {"repo/name:latest", "repo/name", DockerImagePullStrategy.PULL_ALWAYS, true},
                {"", "repo/name:latest", DockerImagePullStrategy.PULL_ALWAYS, true},
                {"repo/name:latest", "repo/name:latest", DockerImagePullStrategy.PULL_ALWAYS, true},
                {"", "repo/name:1.0", DockerImagePullStrategy.PULL_ALWAYS, true},
                {"repo/name:1.0", "repo/name:1.0", DockerImagePullStrategy.PULL_ALWAYS, true},

                {"", "repo/name", DockerImagePullStrategy.PULL_NEVER, false},
                {"repo/name:latest", "repo/name", DockerImagePullStrategy.PULL_NEVER, false},
                {"", "repo/name:latest", DockerImagePullStrategy.PULL_NEVER, false},
                {"repo/name:latest", "repo/name:latest", DockerImagePullStrategy.PULL_NEVER, false},
                {"", "repo/name:1.0", DockerImagePullStrategy.PULL_NEVER, false},
                {"repo/name:1.0", "repo/name:1.0", DockerImagePullStrategy.PULL_NEVER, false},

        });
    }

    @Test
    public void shouldPullImageTest() {

        DockerClient dockerClient = mockDockerClient();

        assertEquals(shouldPull, pullStrategy.shouldPullImage(dockerClient, imageName));
    }

    public DockerImagePullStrategyTest(String existedImage, String imageName, DockerImagePullStrategy pullStrategy, boolean shouldPull) {
        imageList = Collections.singletonList(mockImage(existedImage));
        this.imageName = imageName;
        this.pullStrategy = pullStrategy;
        this.shouldPull = shouldPull;
    }

    // util methods
    private DockerCloud createDockerCloud() {
        return new DockerCloud("name",
                Collections.<DockerTemplate>emptyList(), //templates
                "http://localhost:4243", //serverUrl
                100, //containerCap,
                10, // connectTimeout,
                10, // readTimeout,
                null, // credentialsId,
                null, //version
                null); // dockerHostname
    }
    private Image mockImage(String repoTag) {
        Image img = mock(Image.class);
        when(img.getRepoTags()).thenReturn(new String[]{repoTag});
        return img;
    }

    private DockerClient mockDockerClient() {

        ListImagesCmd listImagesCmd = mock(ListImagesCmd.class);

        when(listImagesCmd.exec()).thenReturn(imageList);

        DockerClient dockerClient = mock(DockerClient.class);
        when(dockerClient.listImagesCmd()).thenReturn(listImagesCmd);

        return dockerClient;
    }
}
