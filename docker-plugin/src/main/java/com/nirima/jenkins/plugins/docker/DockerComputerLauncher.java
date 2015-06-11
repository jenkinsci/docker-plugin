package com.nirima.jenkins.plugins.docker;


import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;


/**
 * Crappy wrapper... On one hand we need store UI configuration,
 * on other have valid configured launcher that different for host/port/etc for any slave.
 * <p/>
 * like {@link DelegatingComputerLauncher}
 */
public abstract class DockerComputerLauncher extends ComputerLauncher {
    protected transient ComputerLauncher launcher;

    /**
     * Return valid configured launcher that will be used for launching slave
     */
    abstract ComputerLauncher getPreparedLauncher(DockerTemplate dockerTemplate, InspectContainerResponse ir);

    /**
     * Contribute container parameters needed for launcher.
     * i.e. port for exposing, command to run, etc.
     */
    abstract void appendContainerConfig(DockerTemplateBase dockerTemplate, CreateContainerCmd createContainerCmd);

    /**
     * Wait until slave is up and ready for connection.
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
