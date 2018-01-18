package com.nirima.jenkins.plugins.docker;

import hudson.model.AbstractProject;
import hudson.slaves.Cloud;
import hudson.util.ListBoxModel;
import jenkins.model.OptionalJobProperty;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import hudson.Extension;

/**
 * Represents an own section to specify a Docker Template in job configuration.
 * 
 * @author Ingo Rissmann
 */
public class DockerJobTemplateProperty extends OptionalJobProperty<AbstractProject<?,?>> {
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


    @Extension
    public static class DescriptorImpl extends OptionalJobPropertyDescriptor {

        @Override
        public String getDisplayName() {
            return "Define a Docker template";
        }

        public ListBoxModel doFillCloudnameItems() {
            ListBoxModel model = new ListBoxModel();
            for (Cloud cloud : DockerCloud.instances()) {
                model.add(cloud.name);
            }
            return model;
        }
    }
}
