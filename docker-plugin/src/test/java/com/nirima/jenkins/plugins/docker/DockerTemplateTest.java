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
        DockerTemplate instance = new DockerTemplate(image, labelString,
                remoteFs,
                remoteFsMapping,
                credentialsId, idleTerminationMinutes,
                sshLaunchTimeoutMinutes,
                jvmOptions, javaPath,
                memoryLimit, cpuShares,
                prefixStartSlaveCmd, suffixStartSlaveCmd,
                instanceCapStr, dnsString,
                dockerCommand,
                volumesString, volumesFrom,
                environmentsString,
                lxcConfString,
                hostname,
                bindPorts,
                bindAllPorts,
                privileged,
                tty,
                macAddress);

              
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
