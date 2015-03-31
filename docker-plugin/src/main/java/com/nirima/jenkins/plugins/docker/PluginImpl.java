package com.nirima.jenkins.plugins.docker;

import shaded.com.google.common.base.Predicate;
import shaded.com.google.common.collect.Collections2;
import shaded.com.google.common.collect.Iterables;
import hudson.Plugin;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Items;
import hudson.model.Run;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by magnayn on 22/02/2014.
 */
public class PluginImpl extends Plugin {

    private static final Logger logger = LoggerFactory.getLogger(PluginImpl.class);


    /**
     * What to call this plug-in to humans.
     */
    public static final String DISPLAY_NAME = "Docker Plugin";

    private static PluginImpl instance;

    /**
     * the default server name.
     */
    public static final String DEFAULT_SERVER_NAME = "defaultServer";

    /**
     * Constructor.
     */
    public PluginImpl() {
        instance = this;
    }

    /**
     * Returns this singleton instance.
     *
     * @return the singleton.
     */
    public static PluginImpl getInstance() {
        return instance;
    }

    /**
     * Get the list of Docker servers.
     *
     * @return the list as a LinkedList of DockerCloud
     */
    public synchronized Collection<DockerCloud> getServers() {

        Collection clouds = Collections2.filter(Jenkins.getInstance().clouds,
                new Predicate<Cloud>() {
                    public boolean apply(@Nullable Cloud input) {
                        return input instanceof DockerCloud;
                    }
                });

        return (Collection<DockerCloud>)clouds;
    }

    public DockerCloud getServer(final String serverName) {

        return Iterables.find(getServers(), new Predicate<DockerCloud>() {
            public boolean apply(@Nullable DockerCloud input) {
                return serverName.equals(input.getDisplayName());
            }
        });
    }

    @Override
    public void start() throws Exception {
       super.start();
    }

    @Override
    public void load() throws IOException {
        super.load();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
    }


}
