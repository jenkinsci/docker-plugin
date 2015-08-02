package com.nirima.jenkins.plugins.docker;

import org.junit.Test;

import java.util.Collections;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerCloudTest {
    @Test
    public void testConstructor_0_10_2() {
        new DockerCloud("name",
                Collections.<DockerTemplate>emptyList(), //templates
                "http://localhost:4243", //serverUrl
                100, //containerCap,
                10, // connectTimeout,
                10, // readTimeout,
                null, // credentialsId,
                null); //version
    }
}
