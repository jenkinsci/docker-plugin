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
                                String volumesString,
                                String volumesFrom,
                                String environmentsString,
                                String hostname,
                                String user,
                                String extraGroups,
                                Integer memoryLimit,
                                Integer memorySwap,
                                Long cpuPeriod,
                                Long cpuQuota,
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
                volumesString,
                volumesFrom,
                environmentsString,
                hostname,
                user,
                extraGroups,
                memoryLimit,
                memorySwap,
                cpuPeriod,
                cpuQuota,
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
