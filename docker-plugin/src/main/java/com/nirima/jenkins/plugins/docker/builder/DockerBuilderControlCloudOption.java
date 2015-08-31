package com.nirima.jenkins.plugins.docker.builder;

import shaded.com.google.common.base.Strings;

import com.github.dockerjava.api.DockerClient;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerSlave;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import jenkins.model.Jenkins;

/**
 * Abstract class for cloud based container "control" actions
 *
 * @author magnayn
 */
public abstract class DockerBuilderControlCloudOption extends DockerBuilderControlOption {
    public final String cloudName;

    protected DockerBuilderControlCloudOption(String cloudName) {
        this.cloudName = cloudName;
    }
    
    public String getCloudName() {
        return cloudName;
    }

    protected DockerCloud getCloud(AbstractBuild<?, ?> build) {
        DockerCloud cloud = null;

        Node node = build.getBuiltOn();
        if (node instanceof DockerSlave) {
            DockerSlave dockerSlave = (DockerSlave) node;
            cloud = dockerSlave.getCloud();
        }

        if (!Strings.isNullOrEmpty(cloudName)) {
            cloud = (DockerCloud) Jenkins.getInstance().getCloud(cloudName);
        }

        if (cloud == null) {
            throw new IllegalStateException("Cannot list cloud for docker action");
        }

        return cloud;
    }

    protected DockerClient getClient(AbstractBuild<?, ?> build) {
        DockerCloud cloud = getCloud(build);

        return cloud.getClient();
    }
}
