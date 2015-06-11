package com.nirima.jenkins.plugins.docker;

import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import hudson.slaves.RetentionStrategy;
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
    Integer cpuShares = 1000;
    String prefixStartSlaveCmd = "prefixStartSlaveCmd";
    String suffixStartSlaveCmd = " suffixStartSlaveCmd";
    String instanceCapStr = "";

    String dockerCommand = "dockerCommand";
    String volumesString = "volumes";
    String volumesFrom = "volumesFrom";
    String environmentsString = "environmentString";
    String lxcConfString = "lxcConf";
    String hostname = "hostname";
    String bindPorts = "0.0.0.0:22";
    boolean bindAllPorts = true;
    boolean privileged = false;
    boolean tty = false;
    String macAddress = "92:d0:c6:0a:29:33";


    private DockerTemplate getDockerTemplateInstanceWithDNSHost(String dnsString) {
        final DockerTemplateBase dockerTemplateBase = new DockerTemplateBase(image, dnsString,
                dockerCommand, volumesString, volumesString, environmentsString,
                lxcConfString, hostname, memoryLimit, cpuShares, bindPorts, bindAllPorts, privileged, tty, macAddress);
        DockerTemplate dockerTemplate = new DockerTemplate(
                dockerTemplateBase,
                labelString,
                remoteFs,
                remoteFsMapping,
                instanceCapStr
        );

        return dockerTemplate;
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

        assertTrue("Error, wrong cpuShares", 1000 == instance.getDockerTemplateBase().cpuShares);
    }

}
