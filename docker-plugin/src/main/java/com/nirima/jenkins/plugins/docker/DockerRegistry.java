package com.nirima.jenkins.plugins.docker;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.*;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.nirima.jenkins.plugins.docker.utils.DockerDirectoryCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;

import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.util.Collections;
import java.util.List;


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


