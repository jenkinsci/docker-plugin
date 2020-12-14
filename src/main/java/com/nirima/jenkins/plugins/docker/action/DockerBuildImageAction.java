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

/**
 * Created by magnayn on 10/01/2014.
 */
@ExportedBean
public class DockerBuildImageAction implements Action, Serializable, Describable<DockerBuildImageAction> {

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

    public void setPull(boolean pull) {
        this.pull = pull;
    }

    public void setNoCache(boolean noCache) {
        this.noCache = noCache;
    }

    @Override
    public String getIconFileName() {
        return "/plugin/docker-plugin/images/24x24/docker.png";
    }

    @Override
    public String getDisplayName() {
        return "Docker Image Build / Publish";
    }

    @Override
    public String getUrlName() {
        return "docker";
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Just for assisting form related stuff.
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<DockerBuildImageAction> {
        @Override
        public String getDisplayName() {
            return "Docker";
        }
    }
}
