package com.nirima.jenkins.plugins.docker;


import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.github.dockerjava.api.DockerClient;
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

    public void setDockerTemplate(DockerTemplate dockerTemplate) {
        this.dockerTemplate = dockerTemplate;
    }

    public DockerTemplate getDockerTemplate() {
        return dockerTemplate;
    }

    //    protected ComputerLauncher launcher;
//
//    public ComputerLauncher getLauncher() {
//        return launcher;
//    }
//
//    public void setLauncher(ComputerLauncher launcher) {
//        this.launcher = launcher;
//    }
//
////    public abstract ComputerLauncher makeLauncher(DockerTemplate template, InspectContainerResponse containerInspectResponse);
//
//    @Override
//    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
//        getLauncher().launch(computer, listener);
//    }
//
//    @Override
//    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
//        getLauncher().afterDisconnect(computer, listener);
//    }
//
//    @Override
//    public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
//        getLauncher().beforeDisconnect(computer, listener);
//    }
}
