package com.nirima.jenkins.plugins.docker;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.AncestorInPath;

import java.io.IOException;

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
                                String lxcConfString,
                                String hostname,
                                Integer memoryLimit,
                                Integer memorySwap,
                                Integer cpuShares,
                                String bindPorts,
                                boolean bindAllPorts,
                                boolean privileged,
                                boolean tty,
                                String macAddress) {
        super(image,
                pullCredentialsId,
                dnsString,
                network,
                dockerCommand,
                volumesString,
                volumesFrom,
                environmentsString,
                lxcConfString,
                hostname,
                memoryLimit,
                memorySwap,
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

        public ListBoxModel doFillPullCredentialsIdItems(@AncestorInPath Item item) {
            final DockerRegistryEndpoint.DescriptorImpl descriptor =
                    (DockerRegistryEndpoint.DescriptorImpl)
                            Jenkins.getInstance().getDescriptorOrDie(DockerRegistryEndpoint.class);
            return descriptor.doFillCredentialsIdItems(item);
        }


    }
}
