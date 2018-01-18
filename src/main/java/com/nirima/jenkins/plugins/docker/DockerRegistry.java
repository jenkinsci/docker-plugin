package com.nirima.jenkins.plugins.docker;

import hudson.model.Describable;
import hudson.model.Descriptor;

import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

@Deprecated
public class DockerRegistry  implements Describable<DockerRegistry> {
    public String registry;
    public String credentialsId;

    public DockerRegistry() {}

    @DataBoundConstructor
    public DockerRegistry(String registry, String credentialsId)
    {
        this.registry = registry;
        this.credentialsId = credentialsId;
    }

    @DataBoundSetter
    public void setRegistry(String registry) {
        this.registry = registry;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @Override
    public Descriptor<DockerRegistry> getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptor(getClass());
    }


    private Object readResolve() {
        // TODO migrate to docker-commons' DockerRegistryEndpoint
        // inspect all DockerTemplates
        return this;
    }


    @Deprecated
    public static final class DescriptorImpl extends Descriptor<DockerRegistry> {

        @Override
        public String getDisplayName() {
            return "Docker Registry";
        }
    }

}


