package com.nirima.jenkins.plugins.docker.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.dockerjava.api.command.InspectContainerResponse;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Describable;
import hudson.model.Descriptor;
import io.jenkins.docker.DockerTransientNode;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.Serializable;

/**
 * Created by magnayn on 10/01/2014.
 */
@ExportedBean
public class DockerBuildAction implements Action, Serializable, Cloneable, Describable<DockerBuildAction> {

    private String cloudId;
    private final String containerHost;
    private final String containerId;
    private String inspect;

    private String taggedId;

    public DockerBuildAction(String containerHost, String containerId, String taggedId) {
        this.containerHost = containerHost;
        this.containerId = containerId;
        this.taggedId = taggedId;
    }

    public DockerBuildAction(DockerTransientNode node) {
        this.containerHost = node.getDockerAPI().getDockerHost().getUri();
        this.containerId = node.getContainerId();
        this.cloudId = node.getCloudId();


        try {
            this.inspect = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .writeValueAsString(
                    node.getDockerAPI().getClient().inspectContainerCmd(containerId).exec());
        } catch (JsonProcessingException e) {
            this.inspect = "Failed to capture container inspection data: "+e.getMessage();
        }
    }


    public String getCloudId() {
        return cloudId;
    }

    public String getContainerHost() {
        return containerHost;
    }

    public String getContainerId() {
        return containerId;
    }

    public String getTaggedId() {
        return taggedId;
    }

    public String getInspect() {

        return inspect;
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

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(getClass());
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
