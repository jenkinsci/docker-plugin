package com.nirima.jenkins.plugins.docker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DockerTemplateTest {
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
    String cgroupParent = "cgroupParent";
    Long cpuPeriod = 0L;
    Long cpuQuota = 0L;
    Integer cpuShares = 1000;
    Integer shmSize = 1002;
    String prefixStartAgentCmd = "prefixStartAgentCmd";
    String suffixStartAgentCmd = " suffixStartAgentCmd";
    String instanceCapStr = "";
    String network = "";
    String dnsSearchString = "docker.com";

    String dockerCommand = "dockerCommand";
    String mountsString = "mounts";
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

    @SuppressWarnings("deprecation")
    private DockerTemplate getDockerTemplateInstanceWithDNSHost(String dnsString) {
        final DockerTemplateBase dockerTemplateBase = new DockerTemplateBase(
                image,
                null,
                dnsString,
                dnsSearchString,
                network,
                dockerCommand,
                mountsString,
                volumesFrom,
                environmentsString,
                hostname,
                user,
                extraGroupsString,
                memoryLimit,
                memorySwap,
                cpuPeriod,
                cpuQuota,
                cpuShares,
                shmSize,
                bindPorts,
                bindAllPorts,
                privileged,
                tty,
                macAddress,
                extraHostsString);
        dockerTemplateBase.setCapabilitiesToAddString(capabilitiesToAddString);
        dockerTemplateBase.setCapabilitiesToDropString(capabilitiesToDropString);
        dockerTemplateBase.setSecurityOptsString(securityOptsString);
        dockerTemplateBase.setCgroupParent(cgroupParent);

        return new DockerTemplate(dockerTemplateBase, null, labelString, remoteFs, instanceCapStr);
    }

    @Test
    void testDnsHosts() {
        DockerTemplate instance;
        String[] expected;

        instance = getDockerTemplateInstanceWithDNSHost("");
        expected = null;
        assertArrayEquals(expected, instance.getDockerTemplateBase().dnsHosts);

        instance = getDockerTemplateInstanceWithDNSHost("8.8.8.8");
        expected = new String[] {"8.8.8.8"};
        assertArrayEquals(expected, instance.getDockerTemplateBase().dnsHosts);

        instance = getDockerTemplateInstanceWithDNSHost("8.8.8.8 8.8.4.4");
        expected = new String[] {"8.8.8.8", "8.8.4.4"};

        assertEquals(2, instance.getDockerTemplateBase().dnsHosts.length);
        assertArrayEquals(expected, instance.getDockerTemplateBase().dnsHosts);
    }

    @Test
    void testDnsSearch() {
        DockerTemplate instance;
        String[] expected;

        instance = getDockerTemplateInstanceWithDNSHost("");
        expected = new String[] {"docker.com"};
        assertArrayEquals(expected, instance.getDockerTemplateBase().dnsSearch);
    }

    @Test
    void testLimits() {
        DockerTemplate instance;
        instance = getDockerTemplateInstanceWithDNSHost("");

        assertEquals(1024, instance.getDockerTemplateBase().memoryLimit.intValue(), "Error, wrong memoryLimit");
        assertEquals(1280, instance.getDockerTemplateBase().memorySwap.intValue(), "Error, wrong memorySwap");
        assertEquals(1000, instance.getDockerTemplateBase().cpuShares.intValue(), "Error, wrong cpuShares");
        assertEquals(1002, instance.getDockerTemplateBase().shmSize.intValue(), "Error, wrong shmSize");
    }

    @Test
    void testCapabilities() {
        DockerTemplate instance;
        instance = getDockerTemplateInstanceWithDNSHost("");

        assertTrue(instance.getDockerTemplateBase().getCapabilitiesToAdd().contains("CHOWN"), "Error, wrong capAdd");
        assertTrue(
                instance.getDockerTemplateBase().getCapabilitiesToDrop().contains("NET_ADMIN"), "Error, wrong capDrop");
    }
}
