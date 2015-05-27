package com.nirima.jenkins.plugins.docker;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.*;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerTemplate2Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @LocalData
    public void shouldLoad090rc1Config() {
        final DockerCloud dock = (DockerCloud) j.getInstance().getCloud("dock");
        final DockerTemplate template = dock.getTemplate("image:b25");
        final String[] volumes = template.getVolumes();

        assertThat("volumes", asList(volumes), hasSize(2));
        assertThat(volumes[0], equalTo("/host/path:/container/path:ro"));
        assertThat(volumes[1], equalTo("/host/path2:/container/path2:ro"));

        final String[] volumesFrom2 = template.getVolumesFrom2();
        assertThat("volumesFrom2", asList(volumesFrom2), hasSize(1));
        assertThat(volumesFrom2[0], equalTo("otherContainer:ro"));
        assertThat("volumesFrom", template.getVolumesFrom(), nullValue());

        assertThat(template.getImage(), equalTo("image:b25"));
    }

    @Test
    @LocalData
    public void shouldLoadEmptyVolumesFrom() {
        final DockerCloud dock = (DockerCloud) j.getInstance().getCloud("dock");
        final DockerTemplate template = dock.getTemplate("jenkins-ubuntu-slave");
        final String[] volumes = template.getVolumes();
        final String[] volumesFrom2 = template.getVolumesFrom2();

        assertThat("volumes", asList(volumes), hasSize(0));
        assertThat("volumesFrom2", asList(volumesFrom2), hasSize(0));
        assertThat("volumesFrom", template.getVolumesFrom(), nullValue());
    }
}
