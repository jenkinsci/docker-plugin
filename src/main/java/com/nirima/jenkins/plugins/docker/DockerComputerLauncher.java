package com.nirima.jenkins.plugins.docker;


import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.google.common.base.Preconditions;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.nirima.jenkins.plugins.docker.utils.PortUtils;
import com.nirima.jenkins.plugins.docker.utils.RetryingComputerLauncher;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;

import java.net.MalformedURLException;
import java.net.URL;


import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * {@link hudson.slaves.ComputerLauncher} for Docker that waits for the instance to really come up before proceeding to
 * the real user-specified {@link hudson.slaves.ComputerLauncher}.
 */
public class DockerComputerLauncher extends DelegatingComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(DockerComputerLauncher.class.getName());

    public DockerComputerLauncher(DockerTemplate template, InspectContainerResponse containerInspectResponse) {
        super(makeLauncher(template, containerInspectResponse));
    }

    private static ComputerLauncher makeLauncher(DockerTemplate template, InspectContainerResponse containerInspectResponse) {
        SSHLauncher sshLauncher = getSSHLauncher(containerInspectResponse, template);
        return new RetryingComputerLauncher(sshLauncher);
    }

    private static SSHLauncher getSSHLauncher(InspectContainerResponse detail, DockerTemplate template)   {
        Preconditions.checkNotNull(template);
        Preconditions.checkNotNull(detail);

        try {

            ExposedPort sshPort = new ExposedPort(22);
            int port = 22;

            Ports.Binding[] bindings = detail.getNetworkSettings().getPorts().getBindings().get(sshPort);

            for(Ports.Binding b : bindings) {
                port = b.getHostPort();
            }

            URL hostUrl = new URL(template.getParent().serverUrl);
            String host = hostUrl.getHost();

            LOGGER.log(Level.INFO, "Creating slave SSH launcher for " + host + ":" + port);
            
            PortUtils.waitForPort(host, port);

            StandardUsernameCredentials credentials = SSHLauncher.lookupSystemCredentials(template.credentialsId);

            return new SSHLauncher(host, port, credentials,  template.jvmOptions , template.javaPath, template.prefixStartSlaveCmd, template.suffixStartSlaveCmd, template.getSSHLaunchTimeoutMinutes() * 60);

        } catch(NullPointerException ex) {
            throw new RuntimeException("No mapped port 22 in host for SSL. Config=" + detail);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Malformed URL for host " + template);
        }
    }


}
