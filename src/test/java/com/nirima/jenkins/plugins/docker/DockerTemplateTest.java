package com.nirima.jenkins.plugins.docker;

import static org.junit.Assert.*;

import org.junit.Test;

public class DockerTemplateTest {
    String image = "image";
    String labelString;
    String remoteFs = "remoteFs";
    String remoteFsMapping = "remoteFsMapping";
    String credentialsId = "credentialsId";
    String idleTerminationMinutes = "idleTerminationMinutes";
    String sshLaunchTimeoutMinutes = "sshLaunchTimeoutMinutes";
    String jvmOptions = " jvmOptions";
    String javaPath = " javaPath";
    Integer memoryLimit = 1024;
    Integer memorySwap = 1280;
    Integer cpuShares = 1000;
    Integer shmSize = 1002;
    String prefixStartSlaveCmd = "prefixStartSlaveCmd";
    String suffixStartSlaveCmd = " suffixStartSlaveCmd";
    String instanceCapStr = "";
    String network = "";
    

    String dockerCommand = "dockerCommand";
    String volumesString = "volumes";
    String volumesFrom = "volumesFrom";
    String environmentsString = "environmentString";
    String hostname = "hostname";
    String bindPorts = "0.0.0.0:22";
    boolean bindAllPorts = true;
    boolean privileged = false;
    boolean tty = false;
    String macAddress = "92:d0:c6:0a:29:33";
    String extraHostsString = "extraHostsString";


    private DockerTemplate getDockerTemplateInstanceWithDNSHost(String dnsString) {
        final DockerTemplateBase dockerTemplateBase = new DockerTemplateBase(image, null, dnsString, network,
                dockerCommand, volumesString, volumesString, environmentsString,
                hostname, memoryLimit, memorySwap, cpuShares, shmSize, bindPorts, bindAllPorts, privileged, tty, macAddress, extraHostsString);

        return new DockerTemplate(dockerTemplateBase, null, labelString, remoteFs, instanceCapStr);
    }

    @Test
    public void testDnsHosts() {
        DockerTemplate instance;
        String[] expected;

        instance = getDockerTemplateInstanceWithDNSHost("");
        assertEquals(0, instance.getDockerTemplateBase().dnsHosts.length);

        instance = getDockerTemplateInstanceWithDNSHost("8.8.8.8");
        expected = new String[]{"8.8.8.8"};

        assertEquals(1, instance.getDockerTemplateBase().dnsHosts.length);
        assertArrayEquals(expected, instance.getDockerTemplateBase().dnsHosts);

        instance = getDockerTemplateInstanceWithDNSHost("8.8.8.8 8.8.4.4");
        expected = new String[]{"8.8.8.8", "8.8.4.4"};

        assertEquals(2, instance.getDockerTemplateBase().dnsHosts.length);
        assertArrayEquals(expected, instance.getDockerTemplateBase().dnsHosts);
        
        assertTrue("Error, wrong memoryLimit", 1024 == instance.getDockerTemplateBase().memoryLimit);
        assertTrue("Error, wrong memorySwap", 1280 == instance.getDockerTemplateBase().memorySwap);
        assertTrue("Error, wrong cpuShares", 1000 == instance.getDockerTemplateBase().cpuShares);
        assertTrue("Error, wrong shmSize", 1002 == instance.getDockerTemplateBase().shmSize);
    }

}
