package com.nirima.jenkins.plugins.docker;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * A simple template storage.
 */
public class DockerSimpleTemplate extends DockerTemplateBase implements Describable<DockerSimpleTemplate> {
    public DockerSimpleTemplate(String image,
                                String dnsString,
                                String dockerCommand,
                                String volumesString,
                                String volumesFrom,
                                String lxcConfString,
                                String hostname,
                                String bindPorts,
                                boolean bindAllPorts,
                                boolean privileged) {
        super(image,
                dnsString,
                dockerCommand,
                volumesString,
                volumesFrom,
                lxcConfString,
                hostname,
                bindPorts,
                bindAllPorts,
                privileged);
    }

    public Descriptor<DockerSimpleTemplate> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<DockerSimpleTemplate> {

        @Override
        public String getDisplayName() {
            return "Docker Template";
        }

    }
}
