package com.nirima.jenkins.plugins.docker.launcher;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.slaves.JNLPLauncher;
import io.jenkins.docker.connector.DockerComputerConnector;
import io.jenkins.docker.connector.DockerComputerJNLPConnector;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.annotations.Beta;

/**
 * JNLP launcher. Doesn't require open ports on docker host.
 * <p/>
 * Steps:
 * - runs container with nop command
 * - as launch action executes jnlp connection to master
 *
 * @author Kanstantsin Shautsou
 */
@Deprecated
public class DockerComputerJNLPLauncher extends DockerComputerLauncher {

    protected JNLPLauncher jnlpLauncher;

    protected String user;

    public DockerComputerConnector convertToConnector() {
        final DockerComputerJNLPConnector connector = new DockerComputerJNLPConnector(jnlpLauncher);
        connector.setUser(user);
        return connector;
    }

}
