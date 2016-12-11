package com.nirima.jenkins.plugins.docker.launcher;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import com.nirima.jenkins.plugins.docker.utils.PortUtils;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.plugins.sshslaves.SSHConnector;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.util.ListBoxModel;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import shaded.com.google.common.annotations.Beta;
import shaded.com.google.common.base.Preconditions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configurable SSH launcher that expected ssh port to be exposed from docker container.
 */
@Beta
public class DockerComputerSSHLauncher extends DockerComputerLauncher {
    private static final Logger LOGGER = Logger.getLogger(DockerComputerSSHLauncher.class.getName());
    // store real UI configuration
    protected final SSHConnector sshConnector;

    @DataBoundConstructor
    public DockerComputerSSHLauncher(SSHConnector sshConnector) {
        this.sshConnector = sshConnector;
    }

    public SSHConnector getSshConnector() {
        return sshConnector;
    }

    public ComputerLauncher getPreparedLauncher(String cloudId, DockerTemplate dockerTemplate, InspectContainerResponse inspect) {
        final DockerComputerSSHLauncher prepLauncher = new DockerComputerSSHLauncher(null); // don't care, we need only launcher

        prepLauncher.setLauncher(getSSHLauncher(cloudId, dockerTemplate, inspect));

        return prepLauncher;
    }

    @Override
    public void appendContainerConfig(DockerTemplate dockerTemplate, CreateContainerCmd createCmd, DockerClient client) {
        final int sshPort = getSshConnector().port;

        createCmd.withExposedPorts(new ExposedPort(sshConnector.port));

        String[] cmd = dockerTemplate.getDockerTemplateBase().getDockerCommandArray();
        if (cmd.length == 0) {
            //default value to preserve compatibility
            createCmd.withCmd("bash", "-c", "/usr/sbin/sshd -D -p " + sshPort);
        }

        createCmd.getPortBindings().add(PortBinding.parse( "" + sshPort));
    }

    @Override
    public boolean waitUp(String cloudId, DockerTemplate dockerTemplate, InspectContainerResponse containerInspect) {
        super.waitUp(cloudId, dockerTemplate, containerInspect);

        final PortUtils.ConnectionCheck connectionCheck =
                PortUtils.connectionCheck(getAddressForSSHD(cloudId, containerInspect))
                         .withRetries(60)
                         .withEveryRetryWaitFor(2, TimeUnit.SECONDS);

        return (connectionCheck.execute() && connectionCheck.useSSH().execute());
    }

    private SSHLauncher getSSHLauncher(String cloudId, DockerTemplate template, InspectContainerResponse inspect) {
        Preconditions.checkNotNull(template);
        Preconditions.checkNotNull(inspect);

        try {
            final InetSocketAddress address = getAddressForSSHD(cloudId, inspect);
            LOGGER.log(Level.INFO, "Creating slave SSH launcher for " + address);
            return new SSHLauncher(address.getHostString(), address.getPort(), sshConnector.getCredentials(),
                    sshConnector.jvmOptions,
                    sshConnector.javaPath, sshConnector.prefixStartSlaveCmd,
                    sshConnector.suffixStartSlaveCmd, sshConnector.launchTimeoutSeconds);
        } catch (NullPointerException ex) {
            throw new RuntimeException("No mapped port 22 in host for SSL. Config=" + inspect, ex);
        }
    }

    /**
     * Given an inspected container, work out where we talk to the SSH daemon.
     *
     * I.E: this is usually the port that has been exposed on the container (22) and mapped
     * to some value on the container host.
     *
     * This might - in the case where Jenkins itself is running on the same cloud - be
     * a direct connection.
     *
     */
    protected InetSocketAddress getAddressForSSHD(String cloudId, InspectContainerResponse ir) {
        // get exposed port
        ExposedPort sshPort = new ExposedPort(sshConnector.port);
        String host = null;
        Integer port = 22;

        final NetworkSettings networkSettings = ir.getNetworkSettings();
        final Ports ports = networkSettings.getPorts();
        final Map<ExposedPort, Ports.Binding[]> bindings = ports.getBindings();

        // Get the binding that goes to the port that we're interested in (e.g: 22)
        final Ports.Binding[] sshBindings = bindings.get(sshPort);

        // Find where it's mapped to
        for (Ports.Binding b : sshBindings) {

            // TODO: I don't really follow why docker-java here is capable of
            // returning a range - surely an exposed port cannot be bound to a *range*?
            String hps = b.getHostPortSpec();

            port = Integer.valueOf(hps);
            host = b.getHostIp();
        }

        //get address, if docker on localhost, then use local?
        if (host == null || host.equals("0.0.0.0")) {
            String url = DockerCloud.getCloudByName(cloudId).getServerUrl();
            host = getDockerHostFromCloud(cloudId);

            if( url.startsWith("unix") && (host == null || host.trim().isEmpty()) ) {
                // Communicating with local sockets.
                host = "0.0.0.0";
            } else {

            /* Don't use IP from DOCKER_HOST because it is invalid or we are
             * connecting to a system that supports a single host abstraction
             * like Joyent's Triton. */
                if (host == null || host.equals("0.0.0.0") || usesSingleHostAbstraction(ir)) {
                    // Try to connect to the container directly (without going through the host)
                    host = networkSettings.getIpAddress();
                    port = sshConnector.port;
                }
            }
        }

        return new InetSocketAddress(host, port);
    }

    private String getDockerHostFromCloud(String cloudId) {
        String url;
        String host;
        DockerCloud cloud = DockerCloud.getCloudByName(cloudId);
        url = cloud.getServerUrl();
        String dockerHostname = cloud.getDockerHostname();
        if (dockerHostname != null && !dockerHostname.trim().isEmpty()) {
            return dockerHostname;
        } else {
            return URI.create(url).getHost();
        }
    }

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        return DESCRIPTOR;
    }

    /**
     * <p>Checks a {@link InspectContainerResponse} object to see if the server
     * uses a single host abstraction model. If it does, then we return
     * true.</p>
     *
     * <p>A Docker single host abstraction is when an entire fleet of
     * provisional resources are presented as if they reside on a single host,
     * but in fact they do not. This allows a user to elastically provision as
     * many resources as they want without having to worry about filling up a
     * single host system.</p>
     *
     * @param inspect response from Docker API
     * @return true if the server API supports a single host abstraction
     */
    protected static boolean usesSingleHostAbstraction(
            InspectContainerResponse inspect) {
        Preconditions.checkNotNull(inspect);

        /* Fill this in with more robust logic when more servers supporting
         * this model become available (e.g. VMWare's project Bonneville and
         * Microsoft's offering). */
        return inspect.getDriver().equals("sdc");
    }

    @Restricted(NoExternalUse.class)
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    @Extension
    public static final class DescriptorImpl extends Descriptor<ComputerLauncher> {

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            return DockerTemplateBase.DescriptorImpl.doFillCredentialsIdItems(context);
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
