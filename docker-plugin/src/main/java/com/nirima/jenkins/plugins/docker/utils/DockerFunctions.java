package com.nirima.jenkins.plugins.docker.utils;

import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import com.nirima.jenkins.plugins.docker.launcher.DockerComputerJNLPLauncher;
import com.nirima.jenkins.plugins.docker.launcher.DockerComputerSSHLauncher;
import com.nirima.jenkins.plugins.docker.strategy.DockerCloudRetentionStrategy;
import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import hudson.model.Descriptor;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * UI helper class.
 *
 * @author Kanstantsin Shautsou
 */
public class DockerFunctions {

    public static List<Descriptor<ComputerLauncher>> getDockerComputerLauncherDescriptors() {
        List<Descriptor<ComputerLauncher>> launchers = new ArrayList<>();

        launchers.add(DockerComputerSSHLauncher.DESCRIPTOR);
//        launchers.add(DockerComputerJNLPLauncher.DESCRIPTOR);

        return launchers;
    }

    public static List<Descriptor<RetentionStrategy<?>>> getDockerRetentionStrategyDescriptors() {
        List<Descriptor<RetentionStrategy<?>>> strategies = new ArrayList<>();

        strategies.add(DockerOnceRetentionStrategy.DESCRIPTOR);
        strategies.add(DockerCloudRetentionStrategy.DESCRIPTOR);
        strategies.addAll(RetentionStrategy.all());

        return strategies;
    }
}
