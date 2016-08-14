package com.nirima.jenkins.plugins.docker;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

/**
 * Represents an own section to specify a Docker Template in job configuration.
 * 
 * @author Ingo Rissmann
 */
public class DockerJobTemplateProperty implements Describable<DockerJobTemplateProperty> {
    public final String cloudname;
    public final DockerTemplate template;

    @DataBoundConstructor
    public DockerJobTemplateProperty(String cloudname, DockerTemplate template) {
        this.cloudname = cloudname;
        this.template = template;
    }

    @Exported
    public String getCloudname() {
        return cloudname;
    }

    @Exported
    public DockerTemplate getTemplate() {
        return template;
    }

    @Override
    public Descriptor<DockerJobTemplateProperty> getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptor(DockerJobTemplateProperty.class);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerJobTemplateProperty> {

        @Override
        public String getDisplayName() {
            return "Docker Job Image Property";
        }

        /**
         * Validates the given name of the Docker Cloud. It checks against the
         * global defined Docker clouds.
         * 
         * @param cloudname
         *            Name of the cloud to use.
         * @return Returns an error message if the given <code>cloudname</code>
         *         is not defined in the global Docker configuration.
         */
        public FormValidation doCheckCloudname(@QueryParameter String cloudname) {
            if (DockerCloud.getCloudByName(cloudname) == null) {
                return FormValidation.error("Cloud doesn't exists.", cloudname);
            }

            return FormValidation.ok();
        }
    }
}
