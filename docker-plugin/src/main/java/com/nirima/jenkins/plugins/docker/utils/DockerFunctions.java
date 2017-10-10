package com.nirima.jenkins.plugins.docker.utils;

import com.nirima.jenkins.plugins.docker.launcher.AttachedDockerComputerLauncher;
import com.nirima.jenkins.plugins.docker.launcher.DockerComputerJNLPLauncher;
import com.nirima.jenkins.plugins.docker.launcher.DockerComputerLauncher;
import com.nirima.jenkins.plugins.docker.launcher.DockerComputerSSHLauncher;
import com.nirima.jenkins.plugins.docker.strategy.DockerCloudRetentionStrategy;
import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import hudson.model.Descriptor;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.List;

/**
 * UI helper class.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerFunctions {

    public static List<Descriptor<DockerComputerLauncher>> getDockerComputerLauncherDescriptors() {
        return Jenkins.getActiveInstance().getDescriptorList(DockerComputerLauncher.class);
    }

    public static List<Descriptor<RetentionStrategy<?>>> getDockerRetentionStrategyDescriptors() {
        List<Descriptor<RetentionStrategy<?>>> strategies = new ArrayList<>();

        strategies.add(Jenkins.getActiveInstance().getDescriptor(DockerOnceRetentionStrategy.class));
        strategies.add(Jenkins.getActiveInstance().getDescriptor(DockerCloudRetentionStrategy.class));

        return strategies;
    }
}
