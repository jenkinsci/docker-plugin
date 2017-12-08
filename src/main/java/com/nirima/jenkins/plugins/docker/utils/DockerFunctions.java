package com.nirima.jenkins.plugins.docker.utils;

import com.nirima.jenkins.plugins.docker.strategy.DockerCloudRetentionStrategy;
import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import hudson.model.Descriptor;
import hudson.slaves.RetentionStrategy;
import io.jenkins.docker.connector.DockerComputerConnector;
import jenkins.model.Jenkins;

import java.util.ArrayList;
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
