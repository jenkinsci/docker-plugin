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
public class DockerCloudRetentionStrategy extends CloudRetentionStrategy {

    private final int timeout;

    @DataBoundConstructor
    public DockerCloudRetentionStrategy(int timeout) {
        super(timeout);
        this.timeout = timeout;
    }

    // for UI binding
    public int getTimeout() {
        return timeout;
    }

    @Extension
    public static final class DescriptorImpl extends hudson.model.Descriptor<RetentionStrategy<?>> {
        @Override
        public String getDisplayName() {
            return "Remove container if unused";
        }
    }
}
