package com.nirima.jenkins.plugins.docker;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.JobPropertyDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import java.util.Map;
import java.util.regex.Pattern;


public class DockerJobProperty extends hudson.model.JobProperty<AbstractProject<?, ?>> {

    /**
     * Tag on completion (commit).
     */
    public final boolean tagOnCompletion;
    public final String additionalTag;
    public final boolean pushOnSuccess;
    public final boolean cleanImages;

    @DataBoundConstructor
    public DockerJobProperty(
            boolean tagOnCompletion,
            String additionalTag,
            boolean pushOnSuccess, boolean cleanImages)
    {
        this.tagOnCompletion = tagOnCompletion;
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
    public boolean isCleanImages() {
        return cleanImages;
    }

    @Extension
    public static final class DescriptorImpl extends JobPropertyDescriptor {
        public FormValidation doCheckAdditionalTag(@QueryParameter String additionalTag){
            if (additionalTag.trim().length() == 0){
                return FormValidation.ok(); // skip zero value
            }
            // Exclude 500 error during commit
            if ((additionalTag.length() < 2) || (additionalTag.length() > 30) ||
                    (!additionalTag.matches("[a-zA-Z_][a-zA-Z0-9_]*"))){
                return FormValidation.error("Illegal tag name: only [a-zA-Z_][a-zA-Z0-9_]* are allowed, " +
                        "minimum 2, maximum 30 in length");
            }
            return FormValidation.ok();
        }

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
                    (Boolean)formData.get("cleanImages"));
        }
    }
}