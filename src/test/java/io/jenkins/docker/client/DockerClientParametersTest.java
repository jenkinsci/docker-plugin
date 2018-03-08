package io.jenkins.docker.client;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import org.junit.Test;

public class DockerClientParametersTest {
    @Test
    public void testHashCodeAndEquals() {
        final String dockerUri1 = "dockerUri1";
        final String dockerUri1a = new String(dockerUri1);
        final String dockerUri2 = "dockerUri2";
        final String credentialsId1 = "credentialsId1";
        final String credentialsId1a = new String(credentialsId1);
        final String credentialsId2 = "credentialsId2";
        final Integer readTimeoutInMsOrNull1 = Integer.valueOf(1234);
        final Integer readTimeoutInMsOrNull1a = new Integer(1234);
        final Integer readTimeoutInMsOrNull2 = Integer.valueOf(5678);
        final Integer readTimeoutInMsOrNull3 = null;
        final Integer connectTimeoutInMsOrNull1 = Integer.valueOf(5678);
        final Integer connectTimeoutInMsOrNull1a = new Integer(5678);
        final Integer connectTimeoutInMsOrNull2 = Integer.valueOf(1234);
        final Integer connectTimeoutInMsOrNull3 = null;
        final DockerClientParameters i1 = new DockerClientParameters(dockerUri1, credentialsId1, readTimeoutInMsOrNull1, connectTimeoutInMsOrNull1);
        final DockerClientParameters i2 = new DockerClientParameters(dockerUri1, credentialsId1, readTimeoutInMsOrNull3, connectTimeoutInMsOrNull3);
        final DockerClientParameters e1 = new DockerClientParameters(dockerUri1a, credentialsId1a, readTimeoutInMsOrNull1a, connectTimeoutInMsOrNull1a);
        final DockerClientParameters e2 = new DockerClientParameters(dockerUri1a, credentialsId1a, readTimeoutInMsOrNull3, connectTimeoutInMsOrNull3);

        final DockerClientParameters d01 = new DockerClientParameters(dockerUri2, credentialsId1, readTimeoutInMsOrNull1, connectTimeoutInMsOrNull1);
        final DockerClientParameters d02 = new DockerClientParameters(dockerUri1, credentialsId2, readTimeoutInMsOrNull1, connectTimeoutInMsOrNull1);
        final DockerClientParameters d03 = new DockerClientParameters(dockerUri1, credentialsId1, readTimeoutInMsOrNull2, connectTimeoutInMsOrNull1);
        final DockerClientParameters d04 = new DockerClientParameters(dockerUri1, credentialsId1, readTimeoutInMsOrNull3, connectTimeoutInMsOrNull1);
        final DockerClientParameters d05 = new DockerClientParameters(dockerUri1, credentialsId1, readTimeoutInMsOrNull1, connectTimeoutInMsOrNull2);
        final DockerClientParameters d06 = new DockerClientParameters(dockerUri1, credentialsId1, readTimeoutInMsOrNull1, connectTimeoutInMsOrNull3);

        assertEquals(i1, i1);
        assertEquals(i1, e1);
        assertEquals(e1, i1);
        assertEquals(i1.hashCode(), e1.hashCode());
        assertEquals(i2, e2);
        assertEquals(e2, i2);
        assertEquals(i2.hashCode(), e2.hashCode());
        assertNotEquals(i1, null);
        assertNotEquals(i1, "Foo");
        assertNotEquals(i1, d01);
        assertNotEquals(d01, i1);
        assertNotEquals(i1.hashCode(), d01.hashCode());
        assertNotEquals(i1, d02);
        assertNotEquals(d02, i1);
        assertNotEquals(i1.hashCode(), d02.hashCode());
        assertNotEquals(i1, d03);
        assertNotEquals(d03, i1);
        assertNotEquals(i1.hashCode(), d03.hashCode());
        assertNotEquals(i1, d04);
        assertNotEquals(d04, i1);
        assertNotEquals(i1.hashCode(), d04.hashCode());
        assertNotEquals(i1, d05);
        assertNotEquals(d05, i1);
        assertNotEquals(i1.hashCode(), d05.hashCode());
        assertNotEquals(i1, d06);
        assertNotEquals(d06, i1);
        assertNotEquals(i1.hashCode(), d06.hashCode());
    }

    @Test
    public void testGetters() {
        final String dockerUri1 = "dockerUri";
        final String credentialsId1 = "credentialsId";
        final Integer readTimeoutInMsOrNull1 = Integer.valueOf(123);
        final Integer readTimeoutInMsOrNull2 = null;
        final Integer connectTimeoutInMsOrNull1 = Integer.valueOf(456);
        final Integer connectTimeoutInMsOrNull2 = null;
        final DockerClientParameters i1 = new DockerClientParameters(dockerUri1, credentialsId1, readTimeoutInMsOrNull1, connectTimeoutInMsOrNull1);
        final DockerClientParameters i2 = new DockerClientParameters(dockerUri1, credentialsId1, readTimeoutInMsOrNull2, connectTimeoutInMsOrNull2);
        
        assertEquals(dockerUri1, i1.getDockerUri());
        assertEquals(credentialsId1, i1.getCredentialsId());
        assertEquals(readTimeoutInMsOrNull1, i1.getReadTimeoutInMsOrNull());
        assertEquals(readTimeoutInMsOrNull2, i2.getReadTimeoutInMsOrNull());
        assertEquals(connectTimeoutInMsOrNull1, i1.getConnectTimeoutInMsOrNull());
        assertEquals(connectTimeoutInMsOrNull2, i2.getConnectTimeoutInMsOrNull());
    }

    @Test
    public void testToString() {
        final String dockerUri1 = "MyDockerURI";
        final String credentialsId1 = "MyCredentials";
        final Integer readTimeoutInMsOrNull1 = Integer.valueOf(1928);
        final Integer readTimeoutInMsOrNull2 = null;
        final Integer connectTimeoutInMsOrNull1 = Integer.valueOf(3746);
        final Integer connectTimeoutInMsOrNull2 = null;
        final DockerClientParameters i1 = new DockerClientParameters(dockerUri1, credentialsId1, readTimeoutInMsOrNull1, connectTimeoutInMsOrNull1);
        final DockerClientParameters i2 = new DockerClientParameters(dockerUri1, credentialsId1, readTimeoutInMsOrNull2, connectTimeoutInMsOrNull2);

        final String s1 = i1.toString();
        final String s2 = i2.toString();
        assertThat(s1, containsString(dockerUri1));
        assertThat(s1, containsString(credentialsId1));
        assertThat(s1, containsString(readTimeoutInMsOrNull1.toString()));
        assertThat(s1, containsString(connectTimeoutInMsOrNull1.toString()));
        assertThat(s2, containsString(dockerUri1));
        assertThat(s2, containsString(credentialsId1));
        assertThat(s2, containsString("null"));
        assertThat(s2, containsString("null"));
    }
}
