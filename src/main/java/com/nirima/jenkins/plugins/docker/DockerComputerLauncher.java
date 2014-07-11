package com.nirima.jenkins.plugins.docker;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.google.common.base.Preconditions;
import com.nirima.docker.client.model.ContainerInspectResponse;
import com.nirima.jenkins.plugins.docker.utils.RetryingComputerLauncher;
import hudson.Extension;
import hudson.model.*;
import hudson.plugins.sshslaves.SSHConnector;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import hudson.model.TaskListener;
import jenkins.model.Jenkins;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link hudson.slaves.ComputerLauncher} for Docker that waits for the instance
 * to really come up before proceeding to the real user-specified
 * {@link hudson.slaves.ComputerLauncher}.
 */
public class DockerComputerLauncher extends DelegatingComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(DockerComputerLauncher.class.getName());

    public DockerComputerLauncher(DockerTemplate template, ContainerInspectResponse containerInspectResponse) {
        super(makeLauncher(template, containerInspectResponse));
    }

    private static ComputerLauncher makeLauncher(DockerTemplate template, ContainerInspectResponse containerInspectResponse) {
        SSHLauncher sshLauncher = getSSHLauncher(containerInspectResponse, template);

        LOGGER.log(Level.INFO, "Keep retrying........");
        return new RetryingComputerLauncher(sshLauncher);
    }

    private static SSHLauncher getSSHLauncher(ContainerInspectResponse detail, DockerTemplate template) {
        Preconditions.checkNotNull(template);
        Preconditions.checkNotNull(detail);

        try {
            int port = Integer.parseInt(detail.getNetworkSettings().ports.getAllPorts().get("22").getHostPort());

            URL hostUrl = new URL(template.getParent().serverUrl);
            String host = hostUrl.getHost();

            LOGGER.log(Level.INFO, "Creating slave SSH launcher for " + host + ":" + port);
            StandardUsernameCredentials credentials = SSHLauncher.lookupSystemCredentials(template.credentialsId);

            LOGGER.log(Level.INFO, "StandardUsernameCredentials: " + template.credentialsId);
            LOGGER.log(Level.INFO, "StandardUsernameCredentials.getUsername: " + credentials.getUsername());
            LOGGER.log(Level.INFO, "StandardUsernameCredentials.getDescription: " + credentials.getDescription());

            return new SSHLauncher(host, port, credentials,
                    template.jvmOptions,
                    template.javaPath,
                    template.prefixStartSlaveCmd,
                    template.suffixStartSlaveCmd, 60);

        } catch (NullPointerException ex) {
            throw new RuntimeException("No mapped port 22 in host for SSL. Config=" + detail);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Malformed URL for host " + template);
        }
    }

}
