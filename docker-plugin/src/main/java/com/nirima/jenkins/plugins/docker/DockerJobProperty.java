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
import shaded.com.google.common.base.Strings;


public class DockerJobProperty extends hudson.model.JobProperty<AbstractProject<?, ?>> {
    
    /**
     * Tag on completion (commit).
     */
    public final boolean tagOnCompletion;
    public final boolean pushOnSuccess;
    public final boolean cleanImages;

    /**
     * Keep containers running after successful builds
     */
    public final boolean remainsRunning;

    /**
     * Author of the image
     */
    public final String imageAuthor;

    /**
     * Whether to tag the committed image as 'latest'
     */
    public final boolean tagLatest;

    /**
     * Whether to tag the committed image with the build number
     */
    public final boolean tagBuildNumber;

    /**
     * Repository name to use for the image, including domain/namespace
     */
    public final String repositoryName;

    /** 
     * List of tags, delimited by commas or semi-colons
     */
    public final String imageTags;

    /**
     * Kept for backwards compatibility with existing data
     */
    public final String additionalTag;


    @DataBoundConstructor
    public DockerJobProperty(
            boolean tagOnCompletion, 
            boolean pushOnSuccess, 
            boolean cleanImages,
            boolean remainsRunning,
            String imageAuthor,
            boolean tagLatest,
            boolean tagBuildNumber,
            String repositoryName,
            String imageTags ) 
    {
        this.additionalTag = "";
        this.tagOnCompletion = tagOnCompletion;
        this.pushOnSuccess = pushOnSuccess;
        this.cleanImages = cleanImages;
        this.remainsRunning = remainsRunning;
        this.imageAuthor = imageAuthor;
        this.tagLatest = tagLatest;
        this.tagBuildNumber = tagBuildNumber;
        this.repositoryName = repositoryName;
        this.imageTags = imageTags;
        
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

    @Exported
    public boolean isRemainsRunning() {
        return remainsRunning;
    }

    @Exported
    public String getImageAuthor() {
        return imageAuthor;
    }

    @Exported
    public boolean isTagLatest() {
        return tagLatest;
    }

    @Exported
    public boolean isTagBuildNumber() {
        return tagBuildNumber;
    }

    @Exported 
    public String getRepositoryName() {
        return repositoryName;
    }

    @Exported 
    public String getImageTags() {
        /*
         * Adds the additionalTag string here to maintain backward compatibility.
         * Remove refs in stable?
         */
        if(!Strings.isNullOrEmpty(additionalTag)) {
            return additionalTag + "," + imageTags;
        } else {
            return imageTags;
        }
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
        public DockerJobProperty newInstance(StaplerRequest sr, JSONObject formData) 
        throws hudson.model.Descriptor.FormException {

            return new DockerJobProperty(
                    (Boolean)formData.get("tagOnCompletion"),
                    (Boolean)formData.get("pushOnSuccess"),
                    (Boolean)formData.get("cleanImages"),
                    (Boolean)formData.get("remainsRunning"),
                    (String)formData.get("imageAuthor"),
                    (Boolean)formData.get("tagLatest"),
                    (Boolean)formData.get("tagBuildNumber"),
                    (String)formData.get("repositoryName"),
                    (String)formData.get("imageTags")
                    );
        }
    }
}
