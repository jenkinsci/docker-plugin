package com.nirima.jenkins.plugins.docker.launcher;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkins.docker.connector.DockerComputerAttachConnector;
import io.jenkins.docker.connector.DockerComputerConnector;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Deprecated
public class AttachedDockerComputerLauncher extends DockerComputerLauncher {

    public DockerComputerConnector convertToConnector() {
        return new DockerComputerAttachConnector();
    }
}
