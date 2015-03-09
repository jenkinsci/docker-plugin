package com.nirima.jenkins.plugins.docker;

import org.junit.Test;
import static org.junit.Assert.*;
import hudson.model.Node;

public class DockerTemplateTest {

    private DockerTemplate getDockerTemplateInstanceWithDNSHost(String dnsString) {
        DockerTemplate instance = new DockerTemplate("image", null, "remoteFs", "remoteFsMapping", "credentialsId", "idleTerminationMinutes", "sshLaunchTimeoutMinutes", " jvmOptions", " javaPath", 1024, 1000, "prefixStartSlaveCmd", " suffixStartSlaveCmd", "", dnsString, "dockerCommand", "volumes", "volumesFrom", "environmentsString", "lxcConf", "hostname", "0.0.0.0:22", true, false);
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
        
        assertTrue("Error, wrong memoryLimit", 1024 == instance.memoryLimit);

        assertTrue("Error, wrong cpuShares", 1000 == instance.cpuShares);
    }

}
