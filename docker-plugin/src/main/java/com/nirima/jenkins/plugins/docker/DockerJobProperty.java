package com.nirima.jenkins.plugins.docker;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.JobPropertyDescriptor;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import java.util.Map;


public class DockerJobProperty extends hudson.model.JobProperty<AbstractProject<?, ?>> {

    /**
     * Tag on completion (commit).
     */
    public final boolean tagOnCompletion;
    public final String additionalTag;
    public final boolean pushOnSuccess;
    public final boolean pushOnUnstable;
    public final boolean pushOnFailure;
    public final boolean cleanImages;

    @DataBoundConstructor
    public DockerJobProperty(
            boolean tagOnCompletion,
            String additionalTag,
            boolean pushOnSuccess, boolean pushOnUnstable,
            boolean pushOnFailure, boolean cleanImages)
    {
        this.tagOnCompletion = tagOnCompletion;
        this.additionalTag = additionalTag;
        this.pushOnSuccess = pushOnSuccess;
        this.pushOnUnstable = pushOnUnstable;
        this.pushOnFailure = pushOnFailure;
        this.cleanImages = cleanImages;
    }

    @Exported
    public String getAdditionalTag() {
        return additionalTag;
    }

    @Exported
    public boolean isPushOnSuccess() {
        return pushOnSuccess;
    }

    @Exported
    public boolean isPushOnUnstable() { return pushOnUnstable; }

    @Exported
    public boolean isPushOnFailure() {
        return pushOnFailure;
    }

    @Exported
    public boolean isTagOnCompletion() {
        return tagOnCompletion;
    }

    @Exported
    public boolean isCleanImages() {
        return cleanImages;
    }

    @Extension
    public static final class DescriptorImpl extends JobPropertyDescriptor {
        public String getDisplayName() {
            return "Docker Job Properties";
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }

        @Override
        public DockerJobProperty newInstance(StaplerRequest sr, JSONObject formData) throws hudson.model.Descriptor.FormException {
            return new DockerJobProperty(
                    (Boolean)formData.get("tagOnCompletion"),
                    (String)formData.get("additionalTag"),
                    (Boolean)formData.get("pushOnSuccess"),
                    (Boolean)formData.get("pushOnUnstable"),
                    (Boolean)formData.get("pushOnFailure"),
                    (Boolean)formData.get("cleanImages"));
        }
    }
}