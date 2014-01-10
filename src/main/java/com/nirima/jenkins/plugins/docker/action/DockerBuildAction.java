package com.nirima.jenkins.plugins.docker.action;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.BuildBadgeAction;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import java.io.Serializable;

/**
 * Created by magnayn on 10/01/2014.
 */
public class DockerBuildAction implements Action, Serializable, Cloneable, Describable<DockerBuildAction> {

    public final String containerHost;
    public final String containerId;

    public final String taggedId;

    public DockerBuildAction(String containerHost, String containerId, String taggedId) {
        this.containerHost = containerHost;
        this.containerId = containerId;
        this.taggedId = taggedId;
    }

    public String getIconFileName() {
        return "/plugin/docker-plugin/images/24x24/docker.png";
    }

    public String getDisplayName() {
        return "Built on Docker";
    }

    public String getUrlName() {
        return "docker";
    }

    public Descriptor<DockerBuildAction> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Just for assisting form related stuff.
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<DockerBuildAction> {
        public String getDisplayName() {
            return "Docker";
        }
    }
}
