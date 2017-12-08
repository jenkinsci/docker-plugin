package com.nirima.jenkins.plugins.docker.strategy;

import hudson.Extension;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.RetentionStrategy;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link CloudRetentionStrategy} that has Descriptor and UI with description
 */
@Deprecated
public class DockerCloudRetentionStrategy {

    private transient int timeout;

    private Object readResolve() {
        return new DockerOnceRetentionStrategy(timeout);
    }
}
