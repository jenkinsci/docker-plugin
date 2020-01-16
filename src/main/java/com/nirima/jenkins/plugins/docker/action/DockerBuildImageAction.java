package com.nirima.jenkins.plugins.docker.action;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.accmod.Restricted;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Created by magnayn on 10/01/2014.
 */
@ExportedBean
public class DockerBuildImageAction implements Action, Serializable, Cloneable, Describable<DockerBuildImageAction> {

    public final String containerHost;
    public final String containerId;

    /**
     * @deprecated use {@link #tags}
     */
    @Deprecated
    public final String taggedId;
    public final List<String> tags;

    public final boolean cleanupWithJenkinsJobDelete;
    public final boolean pushOnSuccess;
    public /* almost final */ boolean noCache;
    public /* almost final */ boolean pull;
    public Map<String, String> labels;

    @Deprecated
    public DockerBuildImageAction(String containerHost,
                                  String containerId,
                                  String taggedId,
                                  boolean cleanupWithJenkinsJobDelete,
                                  boolean pushOnSuccess) {
        this.containerHost = containerHost;
        this.containerId = containerId;
        this.taggedId = taggedId;
        this.cleanupWithJenkinsJobDelete = cleanupWithJenkinsJobDelete;
        this.pushOnSuccess = pushOnSuccess;
        this.tags = null;
    }

    public DockerBuildImageAction(String containerHost,
                                  String containerId,
                                  List<String> tags,
                                  boolean cleanupWithJenkinsJobDelete,
                                  boolean pushOnSuccess) {
        this.containerHost = containerHost;
        this.containerId = containerId;
        this.taggedId = null;
        this.cleanupWithJenkinsJobDelete = cleanupWithJenkinsJobDelete;
        this.pushOnSuccess = pushOnSuccess;
        this.tags = tags;
    }

    /**
     * For internal use only, use {@link #DockerBuildImageAction(String, String, List, boolean, boolean)} instead.
     */
    @Restricted(NoExternalUse.class)
    public DockerBuildImageAction(String containerHost,
                                  String containerId,
                                  List<String> tags,
                                  boolean cleanupWithJenkinsJobDelete,
                                  boolean pushOnSuccess,
                                  boolean noCache,
                                  boolean pull) {
        this(containerHost, containerId, tags, cleanupWithJenkinsJobDelete, pushOnSuccess);
        setNoCache(noCache);
        setPull(pull);
    }

    /**
     * For internal use only, use {@link #DockerBuildImageAction(String, String, List, boolean, boolean)} instead.
     */
    @Restricted(NoExternalUse.class)
    public DockerBuildImageAction(String containerHost,
                                  String containerId,
                                  List<String> tags,
                                  boolean cleanupWithJenkinsJobDelete,
                                  boolean pushOnSuccess,
                                  boolean noCache,
                                  boolean pull,
                                  Map<String, String> labels) {
        this(containerHost, containerId, tags, cleanupWithJenkinsJobDelete, pushOnSuccess, noCache, pull);
        setLabels(labels);
    }

    public void setPull(boolean pull) {
        this.pull = pull;
    }

    public void setNoCache(boolean noCache) {
        this.noCache = noCache;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public String getIconFileName() {
        return "/plugin/docker-plugin/images/24x24/docker.png";
    }

    public String getDisplayName() {
        return "Docker Image Build / Publish";
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
    public static class DescriptorImpl extends Descriptor<DockerBuildImageAction> {
        public String getDisplayName() {
            return "Docker";
        }
    }
}
