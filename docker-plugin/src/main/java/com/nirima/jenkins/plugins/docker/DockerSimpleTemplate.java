package com.nirima.jenkins.plugins.docker;

import hudson.Extension;
import jenkins.model.Jenkins;

/**
 * A simple template storage.
 */
public class DockerSimpleTemplate extends DockerTemplateBase {
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

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptor(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends DockerTemplateBase.DescriptorImpl {

        @Override
        public String getDisplayName() {
            return "Docker Template";
        }

    }
}
