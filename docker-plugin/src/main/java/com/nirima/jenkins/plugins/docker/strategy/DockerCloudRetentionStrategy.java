package com.nirima.jenkins.plugins.docker.strategy;

import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.RetentionStrategy;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link CloudRetentionStrategy} that has Descriptor and UI with description
 */
public class DockerCloudRetentionStrategy extends CloudRetentionStrategy {
    private int idleMinutes = 0;

    @DataBoundConstructor
    public DockerCloudRetentionStrategy(int idleMinutes) {
        super(idleMinutes);
        this.idleMinutes = idleMinutes;
    }

    public int getIdleMinutes() {
        return idleMinutes;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    @Restricted(NoExternalUse.class)
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    public static final class DescriptorImpl extends hudson.model.Descriptor<RetentionStrategy<?>> {
        @Override
        public String getDisplayName() {
            return "Docker Cloud Retention Strategy";
        }
    }
}
