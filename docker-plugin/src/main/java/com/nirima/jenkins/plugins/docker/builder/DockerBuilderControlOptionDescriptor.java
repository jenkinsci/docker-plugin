package com.nirima.jenkins.plugins.docker.builder;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * Created by magnayn on 30/01/2014.
 */
public abstract class DockerBuilderControlOptionDescriptor extends Descriptor<DockerBuilderControlOption>
{
    public static DescriptorExtensionList<DockerBuilderControlOption,DockerBuilderControlOptionDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(DockerBuilderControlOption.class);
    }
}
