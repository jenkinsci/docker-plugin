package com.nirima.jenkins.plugins.docker;

import com.nirima.jenkins.plugins.docker.cloudstat.CloudStatsFactory;
import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class DockerNodeFactoryTest {
    @Test
    public void testGetInstance() {
        DockerNodeFactory dnf = DockerNodeFactory.getInstance();
        assertThat(dnf, is(instanceOf(CloudStatsFactory.class)));
    }
}