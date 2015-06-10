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
 * Crappy wrapper... On one hand we need store UI configuration,
 * on other have valid configured launcher that different for host/port/etc for any slave.
 * <p>
 * like {@link DelegatingComputerLauncher}
 */
public abstract class DockerComputerLauncher extends ComputerLauncher {
    protected transient ComputerLauncher launcher;

    // something can be redundant
//
//    protected transient DockerTemplate dockerTemplate;
//
//    /**
//     * Docker container Id that:
//     * - this launcher connecting to
//     * - DockerSlave was created for
//     * - DockerComputer is instance of
//     */
//    protected transient String containerId;

    /**
     * Return valid configured launcher that will be used for launching slave
     */
    abstract ComputerLauncher getPreparedLauncher(DockerTemplate dockerTemplate, InspectContainerResponse ir);

    /**
     * Contribute container parameters needed for launcher.
     * i.e. port for exposing, command to run, etc
     */
    abstract void appendContainerConfig(DockerTemplateBase dockerTemplate, CreateContainerCmd createContainerCmd);

    /**
     * Ensure that slave is up and ready for connection
     */
    public boolean waitUp(DockerTemplate dockerTemplate, InspectContainerResponse containerInspect) {
        if (!containerInspect.getState().isRunning()) {
            throw new IllegalStateException("Container '" + containerInspect.getId() + "' is not running!");
        }

        return true;
    }


    public ComputerLauncher getLauncher() {
        return launcher;
    }

    public void setLauncher(ComputerLauncher launcher) {
        this.launcher = launcher;
    }


    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        getLauncher().launch(computer, listener);
    }

    @Override
    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
        getLauncher().afterDisconnect(computer, listener);
    }

    @Override
    public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
        getLauncher().beforeDisconnect(computer, listener);
    }
}
