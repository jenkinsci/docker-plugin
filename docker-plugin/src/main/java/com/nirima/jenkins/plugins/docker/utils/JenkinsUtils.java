package com.nirima.jenkins.plugins.docker.utils;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.AuthConfigurations;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.NameParser;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerPluginConfiguration;
import com.nirima.jenkins.plugins.docker.DockerRegistry;
import com.nirima.jenkins.plugins.docker.DockerSlave;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.model.Run;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;
import shaded.com.google.common.base.Optional;
import shaded.com.google.common.base.Predicate;
import shaded.com.google.common.collect.Collections2;
import shaded.com.google.common.collect.Iterables;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Map;

import static hudson.plugins.sshslaves.SSHLauncher.lookupSystemCredentials;

/**
 * Utilities to fetch things out of jenkins environment.
 */
public class JenkinsUtils {
    /**
     * If the build was on a cloud, get the ID of that cloud.
     */
    public static Optional<DockerCloud> getCloudForBuild(AbstractBuild build) {

        Node node = build.getBuiltOn();
        if (node instanceof DockerSlave) {
            DockerSlave slave = (DockerSlave) node;
            return Optional.of(slave.getCloud());
        }

        return Optional.absent();
    }

    /**
     * If the build was workflow, get the ID of that channel.
     */
    public static Optional<DockerCloud> getCloudForChannel(VirtualChannel channel) {

        if( channel instanceof Channel) {
            Channel c = (Channel)channel;
            Node node = Jenkins.getInstance().getNode( c.getName() );
            if (node instanceof DockerSlave) {
                DockerSlave slave = (DockerSlave) node;
                return Optional.of(slave.getCloud());
            }
        }

        return Optional.absent();
    }

    public static Optional<DockerCloud> getCloudThatWeBuiltOn(Run<?,?> build, Launcher launcher) {
        Optional<DockerCloud> cloud;

        // A bit unpleasant, but the getBuiltOn method is in AbstractBuild and
        // we may be a workflow run.

        if( build instanceof AbstractBuild ) {
            cloud = JenkinsUtils.getCloudForBuild((AbstractBuild)build);
        } else {
            cloud = JenkinsUtils.getCloudForChannel(launcher.getChannel());
        }

        return cloud;
    }

    /**
     * Get the list of Docker servers.
     *
     * @return the list as a LinkedList of DockerCloud
     */
    public static synchronized Collection<DockerCloud> getServers() {

        Collection clouds = Collections2.filter(Jenkins.getInstance().clouds,
                new Predicate<Cloud>() {
                    public boolean apply(@Nullable Cloud input) {
                        return input instanceof DockerCloud;
                    }
                });

        return (Collection<DockerCloud>)clouds;
    }

    public static DockerCloud getServer(final String serverName) {

        return Iterables.find(getServers(), new Predicate<DockerCloud>() {
            public boolean apply(@Nullable DockerCloud input) {
                return serverName.equals(input.getDisplayName());
            }
        });
    }

    public static String getHostnameFromBinding(InspectContainerResponse inspectContainerResponse) {
        Map<ExposedPort, Ports.Binding[]> bindings = inspectContainerResponse.getHostConfig().getPortBindings().getBindings();
        if (bindings != null && !bindings.isEmpty())  {
            Ports.Binding[] binding = bindings.values().iterator().next();
            if (binding != null && binding.length > 0) {
                String hostIp = binding[0].getHostIp();
                return getHostnameForIp(hostIp);
            }
        }

        return null;
    }

    private static String getHostnameForIp(String hospIp) {
        try {
            return InetAddress.getByName(hospIp).getHostName();
        } catch (UnknownHostException e) {
            return hospIp;
        }
    }

    public static AuthConfigurations getAuthConfigurations() {
        AuthConfigurations authConfigurations = new AuthConfigurations();

        for(DockerRegistry registry : DockerPluginConfiguration.get().getRegistryList())
        {
            AuthConfig ac = makeAuthConfig(registry);
            if( ac != null ) {
                authConfigurations.addConfig(ac);
            }
        }

        return authConfigurations;
    }

    public static AuthConfig getAuthConfigFor(String imageName) {
        // Do we have an auth config for the registry defined in this tag?
        NameParser.ReposTag reposTag = NameParser.parseRepositoryTag(imageName);
        NameParser.HostnameReposName hostnameReposName = NameParser.resolveRepositoryName(reposTag.repos);

        DockerRegistry registry = DockerPluginConfiguration.get().getRegistryByName(hostnameReposName.hostname);
        return makeAuthConfig(registry);
    }
    protected static AuthConfig makeAuthConfig(DockerRegistry registry) {
        if( registry == null )
            return null;

        Credentials credentials = lookupSystemCredentials(registry.credentialsId);

        return makeAuthConfig(credentials).withRegistryAddress(registry.registry);
    }

    protected static AuthConfig makeAuthConfig(Credentials credentials) {
        if (credentials instanceof StandardUsernamePasswordCredentials) {
            StandardUsernamePasswordCredentials usernamePasswordCredentials =
                    ((StandardUsernamePasswordCredentials) credentials);

            AuthConfig ac = new AuthConfig()
            		.withUsername( usernamePasswordCredentials.getUsername() )
            		.withPassword( usernamePasswordCredentials.getPassword().getPlainText() );

            return ac;
        }

        return null;
    }
}
