package io.jenkins.docker.pipeline;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.ListBoxModel;
import io.jenkins.docker.connector.DockerComputerConnector;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgent;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

@SuppressWarnings("unchecked") // TODO DeclarativeAgent.getDescriptor problem
public class DockerAgent extends DeclarativeAgent<DockerAgent> {

    private static final long serialVersionUID = 1;

    private final String image;
    private String dockerHost;
    private String credentialsId;
    private String remoteFs;

    @SuppressFBWarnings(value = "SE_BAD_FIELD", justification = "Checked in setter")
    private DockerComputerConnector connector;

    @DataBoundConstructor
    public DockerAgent(String image) {
        this.image = image;
    }

    public String getImage() {
        return image;
    }

    public String getDockerHost() {
        return dockerHost;
    }

    @DataBoundSetter
    public void setDockerHost(String dockerHost) {
        this.dockerHost = Util.fixEmpty(dockerHost);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = Util.fixEmpty(credentialsId);
    }

    public String getRemoteFs() {
        return remoteFs;
    }

    @DataBoundSetter
    public void setRemoteFs(String remoteFs) {
        this.remoteFs = Util.fixEmpty(remoteFs);
    }

    public DockerComputerConnector getConnector() {
        if (connector == null) {
            return null;
        }
        DockerNodeStepExecution.assertIsSerializableDockerComputerConnector(connector);
        return connector;
    }

    @DataBoundSetter
    public void setConnector(DockerComputerConnector connector) {
        if (connector == null || connector.equals(DockerNodeStepExecution.DEFAULT_CONNECTOR)) {
            this.connector = null;
        } else {
            DockerNodeStepExecution.assertIsSerializableDockerComputerConnector(connector);
            this.connector = connector;
        }
    }

    public Map<String, Object> getAsArgs() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("image", image);
        if (dockerHost != null) {
            args.put("dockerHost", dockerHost);
        }
        if (credentialsId != null) {
            args.put("credentialsId", credentialsId);
        }
        if (remoteFs != null) {
            args.put("remoteFs", remoteFs);
        }
        if (connector != null) {
            args.put("connector", connector);
        }
        return args;
    }

    @Symbol("dockerContainer")
    @Extension
    public static class DescriptorImpl extends DeclarativeAgentDescriptor<DockerAgent> {

        @Override
        public String getDisplayName() {
            return "Start a Docker container with a new agent (⚠️ Experimental)";
        }

        @SuppressWarnings("lgtm[jenkins/no-permission-check]") // done in DockerServerEndpoint
        @POST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String uri) {
            return ExtensionList.lookupSingleton(DockerServerEndpoint.DescriptorImpl.class)
                    .doFillCredentialsIdItems(item, uri);
        }

        public List<Descriptor<? extends DockerComputerConnector>> getAcceptableConnectorDescriptors() {
            return ExtensionList.lookupSingleton(DockerNodeStep.DescriptorImpl.class)
                    .getAcceptableConnectorDescriptors();
        }
    }
}
