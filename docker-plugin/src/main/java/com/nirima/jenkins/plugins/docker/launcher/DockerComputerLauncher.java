package com.nirima.jenkins.plugins.docker.launcher;

import io.jenkins.docker.connector.DockerComputerConnector;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Deprecated
public abstract class DockerComputerLauncher {
    public abstract DockerComputerConnector convertToConnector();
}
