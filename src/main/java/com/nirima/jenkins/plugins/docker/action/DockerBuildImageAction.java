package com.nirima.jenkins.plugins.docker.action;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Run;
import java.io.Serializable;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.model.RunAction2;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Created by magnayn on 10/01/2014.
 */
@ExportedBean
public class DockerBuildImageAction implements RunAction2, Serializable, Describable<DockerBuildImageAction> {

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

    private transient Run<?, ?> run;

    @Deprecated
    public DockerBuildImageAction(
            String containerHost,
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

    public DockerBuildImageAction(
            String containerHost,
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

    /*
     * For internal use only, use {@link #DockerBuildImageAction(String, String, List, boolean, boolean)} instead.
     */
    @Restricted(NoExternalUse.class)
    public DockerBuildImageAction(
            String containerHost,
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

    @Restricted(DoNotUse.class) // for Jelly
    public Run<?, ?> getRun() {
        return run;
    }

    @Override
    public String getIconFileName() {
        return "symbol-logo-docker plugin-ionicons-api";
    }

    @Override
    public String getDisplayName() {
        return "Docker Image Build / Publish";
    }

    @Override
    public String getUrlName() {
        return "dockerImage";
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.get().getDescriptorOrDie(getClass());
    }

    @Override
    public void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.run = run;
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
