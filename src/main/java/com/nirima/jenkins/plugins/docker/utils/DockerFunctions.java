package com.nirima.jenkins.plugins.docker.utils;

import hudson.model.Descriptor;
import io.jenkins.docker.connector.DockerComputerConnector;
import jenkins.model.Jenkins;

import java.util.List;

/**
 * UI helper class.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerFunctions {

    public static List<Descriptor<DockerComputerConnector>> getDockerComputerConnectorDescriptors() {
        return Jenkins.getInstance().getDescriptorList(DockerComputerConnector.class);
    }
}
