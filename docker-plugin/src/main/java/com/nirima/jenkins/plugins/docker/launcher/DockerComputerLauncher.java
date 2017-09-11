package com.nirima.jenkins.plugins.docker.launcher;


import com.github.dockerjava.api.command.InspectContainerResponse;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import hudson.model.AbstractDescribableImpl;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import com.google.common.annotations.Beta;


/**
 * Crappy wrapper... On one hand we need store UI configuration,
 * on other have valid configured launcher that different for host/port/etc for any slave.
 * <p/>
 * like {@link DelegatingComputerLauncher}
 */
@Beta
public abstract class DockerComputerLauncher extends AbstractDescribableImpl<DockerComputerLauncher> {
    protected ComputerLauncher launcher;

    /**
     * Wait until slave is up and ready for connection.
     */
    public boolean waitUp(String cloudId, DockerTemplate dockerTemplate, InspectContainerResponse containerInspect) {
        if (!containerInspect.getState().getRunning()) {
            throw new IllegalStateException("Container '" + containerInspect.getId() + "' is not running!");
        }

        return true;
    }

    public ComputerLauncher getLauncher() {
        if (launcher == null) {
            throw new IllegalStateException("Launcher must not be null");
        }

        return launcher;
    }

    public void setLauncher(ComputerLauncher launcher) {
        this.launcher = launcher;
    }

}
