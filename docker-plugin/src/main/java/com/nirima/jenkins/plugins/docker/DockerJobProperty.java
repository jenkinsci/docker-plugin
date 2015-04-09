package com.nirima.jenkins.plugins.docker;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;


public class DockerJobProperty extends JobProperty<AbstractProject<?, ?>> {

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
        public String getDisplayName() {
            return "Docker Job Properties";
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }

        @Override
        public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            DockerJobProperty dockerJobProperty;

            if (req.hasParameter("hasDockerContainer")) {
                dockerJobProperty = req.bindJSON(DockerJobProperty.class, formData);
            } else {
                dockerJobProperty = null;
            }

            return dockerJobProperty;
        }
    }
}
