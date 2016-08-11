package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import shaded.com.google.common.base.Strings;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;
import hudson.model.Descriptor;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

/**
 * Swarm Slave.
 */
public class DockerSwarmSlave extends DockerSlave {

    public DockerSwarmSlave(DockerCloud dockerCloud,
                            InspectContainerResponse inspect,
                            String slaveName,
                            String nodeDescription,
                            ComputerLauncher launcher,
                            String containerId,
                            DockerTemplate dockerTemplate,
                            String cloudId) throws IOException, Descriptor.FormException {
        super(makeSlaveName(slaveName, JenkinsUtils.getHostnameFromBinding(inspect)),
                nodeDescription,
                launcher,
                containerId,
                dockerTemplate,
                cloudId);
    }

    private static String makeSlaveName(String slaveName, String hostName) {
        if(!Strings.isNullOrEmpty(hostName) ) {
            return slaveName + "-" + hostName;
        }
        else {
            return slaveName;
        }
    }

}
