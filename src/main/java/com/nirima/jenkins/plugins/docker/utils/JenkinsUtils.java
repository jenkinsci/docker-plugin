package com.nirima.jenkins.plugins.docker.utils;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.model.Run;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.slaves.Cloud;
import io.jenkins.docker.DockerTransientNode;
import jenkins.model.Jenkins;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

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
        if (node instanceof DockerTransientNode) {
            return Optional.of(((DockerTransientNode) node).getCloud());
        }

        return Optional.empty();
    }

    /**
     * If the build was workflow, get the ID of that channel.
     */
    public static Optional<DockerCloud> getCloudForChannel(VirtualChannel channel) {

        if( channel instanceof Channel) {
            Channel c = (Channel)channel;
            Node node = Jenkins.getInstance().getNode( c.getName() );
            if (node instanceof DockerTransientNode) {
                return Optional.of(((DockerTransientNode) node).getCloud());
            }
        }

        return Optional.empty();
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
                    @Override
                    public boolean apply(@Nullable Cloud input) {
                        return input instanceof DockerCloud;
                    }
                });

        return clouds;
    }

    public static DockerCloud getServer(final String serverName) {

        return Iterables.find(getServers(), new Predicate<DockerCloud>() {
            @Override
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

    @Restricted(NoExternalUse.class)
    public static void setTestInstanceId(final String id) {
        _id = id;
    }

    /**
     * returns the Java system property specified by <code>key</code>. If that fails, a default value is returned instead.
     * 
     * To be replaced with jenkins.util.SystemProperties.getString() once they lift their @Restricted(NoExternalUse.class)
     * @param key the key of the system property to read.
     * @param defaultValue the default value which shall be returned in case the property is not defined.
     * @return the system property of <code>key</code>, or <code>defaultValue</code> in case the property is not defined.
     */
    @Restricted(NoExternalUse.class)
    public static String getSystemPropertyString(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    /**
     * returns the Java system property specified by <code>key</code>. If that fails, a default value is returned instead.
     * 
     * In case the value of the system property cannot be parsed properly (e.g. a character was passed, causing a
     * parsing error to occur), the default value is returned.
     * 
     * To be replaced with jenkins.util.SystemProperties.getLong() once they lift their @Restricted(NoExternalUse.class)
     * @param key the key of the system property to read.
     * @param defaultValue the default value which shall be returned in case the property is not defined.
     * @return the system property of <code>key</code>, or <code>defaultValue</code> in case the property is not defined.
     */
    @Restricted(NoExternalUse.class)
    public static Long getSystemPropertyLong(String key, Long defaultValue) {
        String value = getSystemPropertyString(key, null);
        if (value == null) {
            return defaultValue;
        }
        Long longValue = null;
        try {
            longValue = Long.decode(value);
        } catch (NumberFormatException e) {
            LOG.warn("System property {} is attempted to be read as type Long, but value '{}' cannot be parsed as a number", key, value, e);
            return defaultValue;
        }
        return longValue;
    }

    /**
     * returns the Java system property specified by <code>key</code>. If that fails, a default value is returned instead.
     * 
     * In case the value of the system property cannot be parsed properly (e.g. an invalid identifier was passed), 
     * the value <code>false</code> is returned.
     * 
     * To be replaced with jenkins.util.SystemProperties.getBoolean() once they lift their @Restricted(NoExternalUse.class)
     * @param key the key of the system property to read.
     * @param defaultValue the default value which shall be returned in case the property is not defined.
     * @return the system property of <code>key</code>, or <code>defaultValue</code> in case the property is not defined.
     */
    @Restricted(NoExternalUse.class)
    public static boolean getSystemPropertyBoolean(String key, boolean defaultValue) {
        String value = getSystemPropertyString(key, null);
        if (value == null) {
            return defaultValue;
        }
        boolean booleanValue = false;
        booleanValue = Boolean.parseBoolean(value);
        return booleanValue;
    }

}
