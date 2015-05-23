package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.*;
import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import hudson.slaves.RetentionStrategy;
import com.github.dockerjava.api.command.StartContainerCmd;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.mockito.Matchers;
import org.mockito.Mockito;

public class DockerTemplateTest {

    CreateContainerCmd createContainerCmd;
    StartContainerCmd startContainerCmd;

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

    String dnsString = "0.0.0.0";

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
                tty);

              
        return instance;
    }
    private DockerTemplate getDockerTemplateInstanceWithVolumesString(String volumesString) {
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
                tty);


        return instance;
    }

    @Before
    public void beforeEach(){

        createContainerCmd = Mockito.mock(CreateContainerCmd.class);
        when(createContainerCmd.withBinds(Matchers.<Bind>anyVararg())).thenReturn(createContainerCmd);
        when(createContainerCmd.withVolumes(Matchers.<Volume>anyVararg())).thenReturn(createContainerCmd);
        when(createContainerCmd.withPortBindings(Matchers.<PortBinding>anyVararg())).thenReturn(createContainerCmd);
        when(createContainerCmd.withPublishAllPorts(Matchers.anyBoolean())).thenReturn(createContainerCmd);
        when(createContainerCmd.withPrivileged(Matchers.anyBoolean())).thenReturn(createContainerCmd);
        when(createContainerCmd.withDns(Matchers.<String>anyVararg())).thenReturn(createContainerCmd);
        when(createContainerCmd.withLxcConf(Matchers.<LxcConf>anyVararg())).thenReturn(createContainerCmd);
        when(createContainerCmd.withVolumesFrom(Matchers.<VolumesFrom>anyVararg())).thenReturn(createContainerCmd);
        
        startContainerCmd = Mockito.mock(StartContainerCmd.class);
        when(startContainerCmd.withBinds(Matchers.<Bind>anyVararg())).thenReturn(startContainerCmd);
        when(startContainerCmd.withPortBindings(Matchers.<PortBinding>anyVararg())).thenReturn(startContainerCmd);
        when(startContainerCmd.withPublishAllPorts(Matchers.anyBoolean())).thenReturn(startContainerCmd);
        when(startContainerCmd.withPrivileged(Matchers.anyBoolean())).thenReturn(startContainerCmd);
        when(startContainerCmd.withDns(Matchers.<String>anyVararg())).thenReturn(startContainerCmd);
        when(startContainerCmd.withLxcConf(Matchers.<LxcConf>anyVararg())).thenReturn(startContainerCmd);
        when(startContainerCmd.withVolumesFrom(any(String.class))).thenReturn(startContainerCmd);
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

    @Test
    public void testCreateContainerWithNoVolume() throws Exception {
        DockerTemplate instance;

        CreateContainerCmd createContainerCmd = Mockito.mock(CreateContainerCmd.class);

        instance = getDockerTemplateInstanceWithVolumesString("");
        instance.createContainerConfig(createContainerCmd);
        verify(createContainerCmd,times(0)).withBinds(any(Bind.class));
        verify(createContainerCmd,times(0)).withVolumes(any(Volume.class));
    }

    @Test
    public void testCreateContainerWith1Volume() throws Exception{
        DockerTemplate instance;

        instance = getDockerTemplateInstanceWithVolumesString("volume");
        instance.createContainerConfig(createContainerCmd);
        verify(createContainerCmd,times(0)).withBinds(any(Bind.class));
        verify(createContainerCmd,times(1)).withVolumes(any(Volume.class));
    }

    @Test
    public void testCreateContainerWith2Volumes() throws Exception{
        DockerTemplate instance;

        instance = getDockerTemplateInstanceWithVolumesString("volume0 volume1");
        instance.createContainerConfig(createContainerCmd);
        verify(createContainerCmd,times(0)).withBinds(any(Bind.class));
        verify(createContainerCmd,times(2)).withVolumes(any(Volume.class));
    }

    @Test
    public void testCreateContainerWithHostVolume() throws Exception{
        DockerTemplate instance;

        instance = getDockerTemplateInstanceWithVolumesString("host/path:container/path");
        instance.createContainerConfig(createContainerCmd);
        verify(createContainerCmd,times(1)).withBinds(any(Bind.class));
        verify(createContainerCmd,times(0)).withVolumes(any(Volume.class));
    }

    @Test
    public void testCreateContainerWithHostVolumeRo() throws Exception{
        DockerTemplate instance;

        instance = getDockerTemplateInstanceWithVolumesString("host/path:container/path:ro");
        instance.createContainerConfig(createContainerCmd);
        verify(createContainerCmd,times(1)).withBinds(any(Bind.class));
        verify(createContainerCmd,times(0)).withVolumes(any(Volume.class));
    }

    @Test
    public void testCreateContainerWith2HostVolumes() throws Exception{
        DockerTemplate instance;

        instance = getDockerTemplateInstanceWithVolumesString("host/path:container/path:ro host/path2:container/path2:rw");
        instance.createContainerConfig(createContainerCmd);
        verify(createContainerCmd,times(2)).withBinds(any(Bind.class));
        verify(createContainerCmd,times(0)).withVolumes(any(Volume.class));
    }

    @Test
    public void testStartContainerWithNoVolume() throws Exception {
        DockerTemplate instance;

        instance = getDockerTemplateInstanceWithVolumesString("");
        instance.createHostConfig(startContainerCmd);
        verify(startContainerCmd,times(0)).withBinds(any(Bind.class));
    }


    @Test
    public void testStartContainerWithHostVolume() throws Exception{
        DockerTemplate instance;

        instance = getDockerTemplateInstanceWithVolumesString("host/path:container/path");
        instance.createHostConfig(startContainerCmd);
        verify(startContainerCmd,times(1)).withBinds(any(Bind.class));
    }

    @Test
    public void testStartContainerWithHostVolumeRo() throws Exception{
        DockerTemplate instance;

        instance = getDockerTemplateInstanceWithVolumesString("host/path:container/path:ro");
        instance.createHostConfig(startContainerCmd);
        verify(startContainerCmd,times(1)).withBinds(any(Bind.class));
    }

    @Test
    public void testStartContainerWith2HostVolumes() throws Exception{
        DockerTemplate instance;

        instance = getDockerTemplateInstanceWithVolumesString("host/path:container/path:ro host/path2:container/path2:rw");
        instance.createHostConfig(startContainerCmd);
        verify(startContainerCmd,times(2)).withBinds(any(Bind.class));
    }
}
