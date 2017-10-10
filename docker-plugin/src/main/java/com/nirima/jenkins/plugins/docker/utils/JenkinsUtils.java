package com.nirima.jenkins.plugins.docker.utils;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerSlave;

import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.model.Run;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;

import static hudson.plugins.sshslaves.SSHLauncher.lookupSystemCredentials;

/**
 * Utilities to fetch things out of jenkins environment.
 */
public class JenkinsUtils {
    private static final Logger LOG = LoggerFactory.getLogger(JenkinsUtils.class);
    private static String _id;

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

    public static String getInstanceId() {
        try {
            if( _id == null ) {
                _id = Util.getDigestOf(
                        new ByteArrayInputStream(InstanceIdentity.get().getPublic().getEncoded()));
            }
        } catch (IOException e) {
            LOG.error("Could not get Jenkins instance ID.");
            _id = "";
        }
        return _id;
    }
}
