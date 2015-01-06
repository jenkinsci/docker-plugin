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
    public final boolean tagOnFailure;
    public final String additionalTag;
    public final boolean pushOnSuccess;
    public final boolean cleanImages;

    @DataBoundConstructor
    public DockerJobProperty(
            boolean tagOnCompletion,
            boolean tagOnFailure,
            String additionalTag,
            boolean pushOnSuccess, boolean cleanImages)
    {
        this.tagOnCompletion = tagOnCompletion;
        this.tagOnFailure = tagOnFailure;
        this.additionalTag = additionalTag;
        this.pushOnSuccess = pushOnSuccess;
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
    public boolean isTagOnCompletion() {
        return tagOnCompletion;
    }
    
    @Exported
    public boolean isTagOnFailure() {
        return tagOnFailure;
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
                    (Boolean)formData.get("tagOnFailure"),
                    (String)formData.get("additionalTag"),
                    (Boolean)formData.get("pushOnSuccess"),
                    (Boolean)formData.get("cleanImages"));
        }
    }
}