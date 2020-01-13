package com.nirima.jenkins.plugins.docker;

import hudson.Extension;
import jenkins.model.Jenkins;

/**
 * A simple template storage.
 */
public class DockerSimpleTemplate extends DockerTemplateBase {
    public DockerSimpleTemplate(String image,
                                String pullCredentialsId,
                                String dnsString,
                                String network,
                                String dockerCommand,
                                String mountsString,
                                String volumesString,
                                String volumesFrom,
                                String environmentsString,
                                String hostname,
                                Integer memoryLimit,
                                Integer memorySwap,
                                Integer cpuShares,
                                Integer shmSize,
                                String bindPorts,
                                boolean bindAllPorts,
                                boolean privileged,
                                boolean tty,
                                String macAddress,
                                String extraHostsString) {
        super(image,
                pullCredentialsId,
                dnsString,
                network,
                dockerCommand,
                mountsString,
                volumesString,
                volumesFrom,
                environmentsString,
                hostname,
                memoryLimit,
                memorySwap,
                cpuShares,
                shmSize,
                bindPorts,
                bindAllPorts,
                privileged,
                tty,
                macAddress,
                extraHostsString);
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
