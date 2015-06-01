package com.nirima.jenkins.plugins.docker;


import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerException;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.NodeProperty;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import shaded.com.google.common.base.Preconditions;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.nirima.jenkins.plugins.docker.utils.PortUtils;
import com.nirima.jenkins.plugins.docker.utils.RetryingComputerLauncher;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;

import javax.ws.rs.ProcessingException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;


import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;


/**
 * {@link DelegatingComputerLauncher} but with setter
 */
public abstract class DockerComputerLauncher extends ComputerLauncher {
    protected DockerTemplate dockerTemplate;

    /**
     * Docker container Id that:
     * - this launcher connecting to
     * - DockerSlave was created for
     * - DockerComputer is instance of
     */
    protected String containerId;

    public void setDockerTemplate(DockerTemplate dockerTemplate) {
        this.dockerTemplate = dockerTemplate;
    }

    public DockerTemplate getDockerTemplate() {
        return dockerTemplate;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    /**
     * temp method...
     */
    abstract ComputerLauncher makeLauncher(DockerTemplate template, InspectContainerResponse containerInspectResponse);

    /**
     * Contribute container parameters needed for launcher.
     * i.e. port for exposing, command to run, etc
     */
    abstract void appendContainerConfig(CreateContainerCmd createContainerCmd);

//    @Override
//    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
//        runContainer()
//    }

    public void runContainer(DockerComputerLauncher launcher, DockerTemplateBase dockerTemplate, DockerClient dockerClient) throws DockerException {
        CreateContainerCmd containerConfig = dockerClient.createContainerCmd(dockerTemplate.getImage());

        dockerTemplate.fillContainerConfig(containerConfig);
        // contribute launcher specific options
        launcher.appendContainerConfig(containerConfig);
        // create
        CreateContainerResponse response = containerConfig.exec();
        String containerId = response.getId();
        setContainerId(containerId);
        // start
        StartContainerCmd startCommand = dockerClient.startContainerCmd(containerId);
        startCommand.exec();

//        return containerId;
    }
}
