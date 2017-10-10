package com.nirima.jenkins.plugins.docker.launcher;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.Ports;
import com.google.common.base.Preconditions;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.plugins.sshslaves.SSHConnector;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import com.google.common.annotations.Beta;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
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
    public InetSocketAddress getAddressForSSHD(DockerCloud cloud, InspectContainerResponse ir) {
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
            String url = cloud.getDockerHost().getUri();
            host = getDockerHostFromCloud(cloud);

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

    private String getDockerHostFromCloud(DockerCloud cloud) {
        String url;
        String host;
        url = cloud.getDockerHost().getUri();
        String dockerHostname = cloud.getDockerHostname();
        if (dockerHostname != null && !dockerHostname.trim().isEmpty()) {
            return dockerHostname;
        } else {
            return URI.create(url).getHost();
        }
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

    @Extension
    public static final class DescriptorImpl extends Descriptor<DockerComputerLauncher> {

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            return DockerTemplateBase.DescriptorImpl.doFillCredentialsIdItems(context);
        }

        @Override
        public String getDisplayName() {
            return "Docker SSH computer launcher";
        }
    }

}
