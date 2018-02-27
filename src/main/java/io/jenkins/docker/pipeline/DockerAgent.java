package io.jenkins.docker.pipeline;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgent;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerAgent extends DeclarativeAgent<DockerAgent> {

    private final String image;

    private String registryCredentials;

    @DataBoundConstructor
    public DockerAgent(@Nonnull String image) {
        this.image = image;
    }

    @DataBoundSetter
    public void setRegistryCredentials(String registryCredentials) {
        this.registryCredentials = registryCredentials;
    }

    @Extension(ordinal = 1000, optional = true) @Symbol("container")
    public static class DescriptorImpl extends DeclarativeAgentDescriptor<DockerAgent> {
    }

}
