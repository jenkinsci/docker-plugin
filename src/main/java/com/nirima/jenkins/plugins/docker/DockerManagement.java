package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.DockerClient;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import io.jenkins.docker.client.DockerAPI;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerProxy;

/**
 * Manage the docker images. Docker page under "Manage Jenkins" page.
 */
@Extension
public class DockerManagement extends ManagementLink implements StaplerProxy, Describable<DockerManagement>, Saveable {

    @Override
    public String getIconFileName() {
        return "symbol-logo-docker plugin-ionicons-api";
    }

    @Override
    public String getUrlName() {
        return "docker-plugin";
    }

    @Override
    public String getDisplayName() {
        return Messages.displayName();
    }

    @Override
    public String getDescription() {
        return Messages.pluginDescription();
    }

    public static DockerManagement get() {
        return ManagementLink.all().get(DockerManagement.class);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
    }

    @Override
    public void save() throws IOException {}

    /**
     * Descriptor is only used for UI form bindings.
     */
    @Extension
    public static final class DescriptorImpl extends Descriptor<DockerManagement> {

        @Override
        public String getDisplayName() {
            return DockerManagement.class.getSimpleName(); // unused
        }
    }

    public DockerManagementServer getServer(String serverName) {
        return new DockerManagementServer(serverName);
    }

    @Override
    public Object getTarget() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return this;
    }

    public Collection<String> getServerNames() {
        return JenkinsUtils.getServerNames();
    }

    public static class ServerDetail {
        final DockerCloud cloud;

        public ServerDetail(DockerCloud cloud) {
            this.cloud = cloud;
        }

        public String getName() {
            return cloud.getDisplayName();
        }

        public String getActiveHosts() {
            try {
                final DockerAPI dockerApi = cloud.getDockerApi();
                final List<?> containers;
                try (final DockerClient client = dockerApi.getClient()) {
                    containers = client.listContainersCmd().exec();
                }
                return "(" + containers.size() + ")";
            } catch (Exception ex) {
                return "Error: " + ex;
            }
        }
    }

    public Collection<ServerDetail> getServers() {
        return DockerCloud.instances().stream().map(ServerDetail::new).collect(Collectors.toList());
    }
}
