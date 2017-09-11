package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.google.common.base.Strings;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;
import hudson.model.Descriptor;
import hudson.slaves.ComputerLauncher;

import java.io.IOException;

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
