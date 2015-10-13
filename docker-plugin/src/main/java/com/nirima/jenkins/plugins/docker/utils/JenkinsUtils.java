package com.nirima.jenkins.plugins.docker.utils;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerSlave;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.model.Run;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;
import shaded.com.google.common.base.Optional;

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
        Optional<DockerCloud> cloud = Optional.absent();

        // A bit unpleasant, but the getBuiltOn method is in AbstractBuild and
        // we may be a workflow run.

        if( build instanceof AbstractBuild ) {
            cloud = JenkinsUtils.getCloudForBuild((AbstractBuild)build);
        } else {
            cloud = JenkinsUtils.getCloudForChannel(launcher.getChannel());
        }

        return cloud;
    }
}
