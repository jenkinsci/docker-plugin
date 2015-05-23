package com.nirima.jenkins.plugins.docker;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.junit.Assert.*;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerTemplate2Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @LocalData
    public void testLoad090rc1Config() {
        final DockerCloud dock = (DockerCloud) j.getInstance().getCloud("dock");
        final DockerTemplate template = dock.getTemplate("image:b25");
        final String[] volumes = template.getVolumes();

        assertEquals(volumes.length, 2);
        assertEquals(volumes[0], "/host/path:/container/path:ro");
        assertEquals(volumes[1], "/host/path2:/container/path2:ro");

        final String[] volumesFrom2 = template.getVolumesFrom2();
        assertEquals(volumesFrom2.length, 1);
        assertEquals(volumesFrom2[0], "otherContainer:ro");
        assertNull(template.getVolumesFrom());
    }
}
