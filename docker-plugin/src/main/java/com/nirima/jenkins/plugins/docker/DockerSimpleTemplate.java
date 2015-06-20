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
                                String environmentsString,
                                String lxcConfString,
                                String hostname,
                                Integer memoryLimit,
                                Integer cpuShares,
                                String bindPorts,
                                boolean bindAllPorts,
                                boolean privileged,
                                boolean tty,
                                String macAddress) {
        super(image,
                dnsString,
                dockerCommand,
                volumesString,
                volumesFrom,
                environmentsString,
                lxcConfString,
                hostname,
                memoryLimit,
                cpuShares,
                bindPorts,
                bindAllPorts,
                privileged,
                tty,
                macAddress);
    }

    public Descriptor<DockerSimpleTemplate> getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptor(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<DockerSimpleTemplate> {

        @Override
        public String getDisplayName() {
            return "Docker Template";
        }

    }
}
