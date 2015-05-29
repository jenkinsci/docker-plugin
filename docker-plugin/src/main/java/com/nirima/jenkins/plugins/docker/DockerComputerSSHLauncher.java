package com.nirima.jenkins.plugins.docker;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.nirima.jenkins.plugins.docker.utils.PortUtils;
import com.nirima.jenkins.plugins.docker.utils.RetryingComputerLauncher;
import com.trilead.ssh2.Connection;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.plugins.sshslaves.SSHConnector;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.SlaveComputer;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import shaded.com.google.common.base.Preconditions;

import javax.ws.rs.ProcessingException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.*;

/**
 * Launcher that expects 22 ssh port to be exposed from docker container
 */
public class DockerComputerSSHLauncher extends DockerComputerLauncher {
    private static final Logger LOGGER = Logger.getLogger(DockerComputerSSHLauncher.class.getName());

    public SSHConnector sshConnector;

    protected transient DockerTemplate dockerTemplate;

    @DataBoundConstructor
    public DockerComputerSSHLauncher(SSHConnector sshConnector) {
        this.sshConnector = sshConnector;
    }

    public DockerTemplate getDockerTemplate() {
        return dockerTemplate;
    }

    public void setDockerTemplate(DockerTemplate dockerTemplate) {
        this.dockerTemplate = dockerTemplate;
    }

    public SSHConnector getSshConnector() {
        return sshConnector;
    }

    public ComputerLauncher makeLauncher(DockerTemplate template, InspectContainerResponse containerInspectResponse) {
        SSHLauncher sshLauncher = getSSHLauncher(containerInspectResponse, template);
        return new RetryingComputerLauncher(sshLauncher);
    }

    private SSHLauncher getSSHLauncher(InspectContainerResponse detail, DockerTemplate template)   {
        Preconditions.checkNotNull(template);
        Preconditions.checkNotNull(detail);

        try {
            ExposedPort sshPort = new ExposedPort(sshConnector.port);
            String host = null;
            Integer port = 22;

            Ports.Binding[] bindings = detail.getNetworkSettings().getPorts().getBindings().get(sshPort);

            for (Ports.Binding b : bindings) {
                port = b.getHostPort();
                host = b.getHostIp();
            }

            if (host == null || host.equals("0.0.0.0")) {
                URL hostUrl = new URL(template.getParent().serverUrl);
                host = hostUrl.getHost();
            }

            LOGGER.log(Level.INFO, "Creating slave SSH launcher for " + host + ":" + port);

            PortUtils.waitForPort(host, port);

            return new SSHLauncher(host, port, sshConnector.getCredentials(),
                    sshConnector.jvmOptions,
                    sshConnector.javaPath, sshConnector.prefixStartSlaveCmd,
                    sshConnector.suffixStartSlaveCmd, sshConnector.launchTimeoutSeconds);

        } catch(NullPointerException ex) {
            throw new RuntimeException("No mapped port 22 in host for SSL. Config=" + detail, ex);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Malformed URL for host " + template, e);
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptor(DockerComputerSSHLauncher.class);
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        Preconditions.checkNotNull(getDockerTemplate());

        PrintStream logger = listener.getLogger();

        logger.println("Launching " + getDockerTemplate().getImage() );

        List<? extends NodeProperty<?>> nodeProperties = new ArrayList<>();

        String containerId = getDockerTemplate().provisionNew();
        DockerClient client = getDockerTemplate().getParent().connect();
        InspectContainerResponse containerInspectResponse = null;
        try {
            containerInspectResponse = client.inspectContainerCmd(containerId).exec();
        } catch(ProcessingException ex) {
            client.removeContainerCmd(containerId).withForce(true).exec();
            throw ex;
        }

        // Build a description up:
        String nodeDescription = "Docker Node [" + getDockerTemplate().getImage() + " on ";
        try {
            nodeDescription += getDockerTemplate().getParent().getDisplayName();
        } catch (Exception ex) {
            nodeDescription += "???";
        }
        nodeDescription += "]";

        String slaveName = containerId.substring(0, 12);

        try {
            slaveName = slaveName + "@" + getDockerTemplate().getParent().getDisplayName();
        } catch(Exception ex) {
            LOGGER.warning("Error fetching cloud name");
        }

        makeLauncher(getDockerTemplate(), containerInspectResponse).launch(computer, listener);
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<ComputerLauncher> {

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            return DockerTemplate.DescriptorImpl.doFillCredentialsIdItems(context);
        }

        public Class getSshConnectorClass() {
            return SSHConnector.class;
        }

        @Override
        public String getDisplayName() {
            return "Docker SSH computer launcher";
        }
    }

}
