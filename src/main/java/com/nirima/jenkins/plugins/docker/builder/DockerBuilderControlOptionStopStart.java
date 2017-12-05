package com.nirima.jenkins.plugins.docker.builder;

/**
 * @author magnayn
 */
public abstract class DockerBuilderControlOptionStopStart extends DockerBuilderControlCloudOption {

    public final String containerId;

    public DockerBuilderControlOptionStopStart(String cloudId, String containerId) {
        super(cloudId);
        this.containerId = containerId;
    }
}
