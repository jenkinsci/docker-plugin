package com.nirima.jenkins.plugins.docker.strategy;

import hudson.slaves.CloudRetentionStrategy;

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
