package com.nirima.jenkins.plugins.docker;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.nirima.jenkins.plugins.docker.cloudstat.CloudStatsFactory;
import org.junit.Test;

public class DockerNodeFactoryTest {
    @Test
    public void testGetInstance() {
        DockerNodeFactory dnf = DockerNodeFactory.getInstance();
        assertThat(dnf, is(instanceOf(CloudStatsFactory.class)));
    }
}
