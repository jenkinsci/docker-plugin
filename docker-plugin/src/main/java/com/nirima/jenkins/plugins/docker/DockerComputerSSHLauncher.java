package com.nirima.jenkins.plugins.docker;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
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
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import shaded.com.google.common.base.Preconditions;
import shaded.com.google.common.base.Strings;
import shaded.com.google.common.collect.ImmutableList;

import javax.ws.rs.ProcessingException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
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

    @Override
    void appendContainerConfig(CreateContainerCmd createCmd) {
        createCmd.withPortSpecs("22/tcp");

        String[] cmd = getDockerTemplate().getDockerCommandArray();
        if (cmd.length == 0) {
            //default value to preserve compatibility
            cmd = new String[]{"/usr/sbin/sshd", "-D"};
            createCmd.withCmd(cmd);
        }

//        /**
//         * Provide a sensible default - templates are for slaves, and you're mostly going
//         * to want port 22 exposed.
//         */
//        final Ports portBindings = createCmd.getPortBindings();
//        if (Strings.isNullOrEmpty() {
//            final ImmutableList<PortBinding> build = ImmutableList.<PortBinding>builder()
//                    .add(PortBinding.parse("0.0.0.0::22"))
//                    .build();
//
//        }
        createCmd.withPortBindings(PortBinding.parse("0.0.0.0::22"));


    }

    private SSHLauncher getSSHLauncher(InspectContainerResponse detail, DockerTemplate template)   {
        Preconditions.checkNotNull(template);
        Preconditions.checkNotNull(detail);

        try {
            // get exposed port
            ExposedPort sshPort = new ExposedPort(sshConnector.port);
            String host = null;
            Integer port = 22;

            Ports.Binding[] bindings = detail.getNetworkSettings().getPorts().getBindings().get(sshPort);

            for (Ports.Binding b : bindings) {
                port = b.getHostPort();
                host = b.getHostIp();
            }

            //get address, if docker on localhost, then use local?
            if (host == null || host.equals("0.0.0.0")) {
                URL hostUrl = new URL(template.getParent().serverUrl);
                host = hostUrl.getHost();
            }

            LOGGER.log(Level.INFO, "Waiting for open " + port + " on " + host);

            PortUtils.waitForPort(host, port);

            LOGGER.log(Level.INFO, "Creating slave SSH launcher for " + host + ":" + port);

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
        final DockerComputer dockerComputer = (DockerComputer) computer;
        final DockerCloud cloud = dockerComputer.getCloud();
        final DockerClient client = cloud.connect();
//        final StreamTaskListener listener = new StreamTaskListener(System.out, Charset.defaultCharset());
//        if (getLauncher() instanceof DockerComputerSSHLauncher) {

        PrintStream logger = listener.getLogger();

        logger.println("Running container " + getDockerTemplate().getImage());

        runContainer(this, getDockerTemplate(), client);

        dockerComputer.setContainerId(getContainerId());

        logger.println("Run container with id: " + getContainerId());

        InspectContainerResponse containerInspectResponse = null;
        try {
            containerInspectResponse = client.inspectContainerCmd(getContainerId()).exec();
        } catch (ProcessingException ex) {
            client.removeContainerCmd(getContainerId()).withForce(true).exec();
            throw ex;
        }

        logger.println("Connecting to " + getDockerTemplate().getImage() );

//        no way to set name or description because slave was already created
//        // Build a description up:
//        String nodeDescription = "Docker Node [" + getDockerTemplate().getImage() + " on ";
//        try {
//            nodeDescription += getDockerTemplate().getParent().getDisplayName();
//        } catch (Exception ex) {
//            nodeDescription += "???";
//        }
//        nodeDescription += "]";
//
//        String slaveName = containerId.substring(0, 12);
//
//        try {
//            slaveName = slaveName + "@" + getDockerTemplate().getParent().getDisplayName();
//        } catch(Exception ex) {
//            LOGGER.warning("Error fetching cloud name");
//        }

        makeLauncher(getDockerTemplate(), containerInspectResponse)
                .launch(computer, listener);
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
