package com.nirima.jenkins.plugins.docker;

import hudson.Extension;
import hudson.model.Item;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.kohsuke.stapler.AncestorInPath;

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
                                Integer memoryLimit,
                                Integer memorySwap,
                                Integer cpuShares,
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
                memoryLimit,
                memorySwap,
                cpuShares,
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

        public ListBoxModel doFillPullCredentialsIdItems(@AncestorInPath Item item) {
            final DockerRegistryEndpoint.DescriptorImpl descriptor =
                    (DockerRegistryEndpoint.DescriptorImpl)
                            Jenkins.getInstance().getDescriptorOrDie(DockerRegistryEndpoint.class);
            return descriptor.doFillCredentialsIdItems(item);
        }


    }
}
