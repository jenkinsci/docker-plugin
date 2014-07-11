package com.nirima.jenkins.plugins.docker;

import org.junit.Test;
import static org.junit.Assert.*;

public class DockerTemplateTest {

    private DockerTemplate getDockerTemplateInstanceWithDNSHost(String dnsString) {
        DockerTemplate instance = new DockerTemplate("image", null, "remoteFs", "credentialsId", " jvmOptions", " javaPath", "prefixStartSlaveCmd", " suffixStartSlaveCmd", "", dnsString, "dockerCommand", "lxcConf", "volumes", "hostname", false, "30");
        return instance;
    }

    @Test
    public void testDnsHosts() {
        DockerTemplate instance;
        String[] expected;

        instance = getDockerTemplateInstanceWithDNSHost("");
        assertEquals(0, instance.dnsHosts.length);

        instance = getDockerTemplateInstanceWithDNSHost("8.8.8.8");
        expected = new String[]{"8.8.8.8"};

        assertEquals(1, instance.dnsHosts.length);
        assertArrayEquals(expected, instance.dnsHosts);

        instance = getDockerTemplateInstanceWithDNSHost("8.8.8.8 8.8.4.4");
        expected = new String[]{"8.8.8.8", "8.8.4.4"};

        assertEquals(2, instance.dnsHosts.length);
        assertArrayEquals(expected, instance.dnsHosts);

    }

}
