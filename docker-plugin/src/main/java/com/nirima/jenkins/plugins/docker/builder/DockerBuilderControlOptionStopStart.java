package com.nirima.jenkins.plugins.docker.builder;

/**
 * Created by magnayn on 30/01/2014.
 */
public abstract class DockerBuilderControlOptionStopStart extends DockerBuilderControlCloudOption {

    public final String containerId;

    public DockerBuilderControlOptionStopStart(String cloudId, String containerId) {
        super(cloudId);
        this.containerId = containerId;
    }
}
