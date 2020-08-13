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
    Long cpuPeriod = 0L;
    Long cpuQuota = 0L;
    Integer cpuShares = 1000;
    Integer shmSize = 1002;
    String prefixStartAgentCmd = "prefixStartAgentCmd";
    String suffixStartAgentCmd = " suffixStartAgentCmd";
    String instanceCapStr = "";
    String network = "";

    String dockerCommand = "dockerCommand";
    String volumesString = "volumes";
    String volumesFrom = "volumesFrom";
    String environmentsString = "environmentString";
    String hostname = "hostname";
    String user = "user1";
    String extraGroupsString = "foo\nbar";
    String bindPorts = "0.0.0.0:22";
    boolean bindAllPorts = true;
    boolean privileged = false;
    boolean tty = false;
    String macAddress = "92:d0:c6:0a:29:33";
    String extraHostsString = "extraHostsString";
    String capabilitiesToAddString = "CHOWN";
    String capabilitiesToDropString = "NET_ADMIN";
    String securityOptsString = "seccomp=unconfined";


    private DockerTemplate getDockerTemplateInstanceWithDNSHost(String dnsString) {
        final DockerTemplateBase dockerTemplateBase = new DockerTemplateBase(
                image, null, dnsString, network, dockerCommand, volumesString, volumesString,
                environmentsString, hostname, user, extraGroupsString, memoryLimit, memorySwap, cpuPeriod, cpuQuota,
                cpuShares, shmSize, bindPorts, bindAllPorts, privileged, tty, macAddress, extraHostsString);
        dockerTemplateBase.setCapabilitiesToAddString(capabilitiesToAddString);
        dockerTemplateBase.setCapabilitiesToDropString(capabilitiesToDropString);
        dockerTemplateBase.setSecurityOptsString(securityOptsString);

        return new DockerTemplate(dockerTemplateBase, null, labelString, remoteFs, instanceCapStr);
    }

    @Test
    public void testDnsHosts() {
        DockerTemplate instance;
        String[] expected;

        instance = getDockerTemplateInstanceWithDNSHost("");
        expected = null;
        assertArrayEquals(expected, instance.getDockerTemplateBase().dnsHosts);

        instance = getDockerTemplateInstanceWithDNSHost("8.8.8.8");
        expected = new String[]{"8.8.8.8"};
        assertArrayEquals(expected, instance.getDockerTemplateBase().dnsHosts);

        instance = getDockerTemplateInstanceWithDNSHost("8.8.8.8 8.8.4.4");
        expected = new String[]{"8.8.8.8", "8.8.4.4"};

        assertEquals(2, instance.getDockerTemplateBase().dnsHosts.length);
        assertArrayEquals(expected, instance.getDockerTemplateBase().dnsHosts);
    }

    @Test
    public void testLimits() {
        DockerTemplate instance;
        instance = getDockerTemplateInstanceWithDNSHost("");

        assertTrue("Error, wrong memoryLimit", 1024 == instance.getDockerTemplateBase().memoryLimit);
        assertTrue("Error, wrong memorySwap", 1280 == instance.getDockerTemplateBase().memorySwap);
        assertTrue("Error, wrong cpuShares", 1000 == instance.getDockerTemplateBase().cpuShares);
        assertTrue("Error, wrong shmSize", 1002 == instance.getDockerTemplateBase().shmSize);
    }

    @Test
    public void testCapabilities() {
        DockerTemplate instance;
        instance = getDockerTemplateInstanceWithDNSHost("");

        assertTrue("Error, wrong capAdd", instance.getDockerTemplateBase().getCapabilitiesToAdd().contains("CHOWN"));
        assertTrue("Error, wrong capDrop", instance.getDockerTemplateBase().getCapabilitiesToDrop().contains("NET_ADMIN"));
    }

}
