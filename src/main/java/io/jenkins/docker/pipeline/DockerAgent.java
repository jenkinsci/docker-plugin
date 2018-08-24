package io.jenkins.docker.pipeline;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgent;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

@SuppressWarnings("unchecked") // TODO DeclarativeAgent.getDescriptor problem
public class DockerAgent extends DeclarativeAgent<DockerAgent> {

    public final String image;

    @DataBoundConstructor
    public DockerAgent(String image) {
        this.image = image;
    }

    // TODO other properties accepted by DockerNodeStep

    @Symbol("dockerContainer")
    @Extension
    public static class DescriptorImpl extends DeclarativeAgentDescriptor<DockerAgent> {

        @Override
        public String getDisplayName() {
            return "Start a Docker container with a new agent";
        }

    }

}
