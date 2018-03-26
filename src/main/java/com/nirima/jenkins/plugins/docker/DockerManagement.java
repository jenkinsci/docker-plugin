package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.DockerClient;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import io.jenkins.docker.client.DockerAPI;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerProxy;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;
import java.util.List;



/**
 * Manage the docker images.
 * Docker page under "Manage Jenkins" page.
 */
@Extension
public class DockerManagement extends ManagementLink implements StaplerProxy, Describable<DockerManagement>, Saveable {

    @Override
    public String getIconFileName() {
        return com.nirima.jenkins.plugins.docker.utils.Consts.PLUGIN_IMAGES_URL + "/48x48/docker.png";
    }

    @Override
    public String getUrlName() {
        return "docker-plugin";
    }

    @Override
    public String getDisplayName() {
        return Messages.DisplayName();
    }

    @Override
    public String getDescription() {
        return Messages.PluginDescription();
    }

    public static DockerManagement get() {
        return ManagementLink.all().get(DockerManagement.class);
    }


    @Override
    public DescriptorImpl getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(DescriptorImpl.class);
    }

    @Override
    public void save() throws IOException {

    }

    /**
     * Descriptor is only used for UI form bindings.
     */
    @Extension
    public static final class DescriptorImpl extends Descriptor<DockerManagement> {

        @Override
        public String getDisplayName() {
            return null; // unused
        }
    }

        public DockerManagementServer getServer(String serverName) {
            return new DockerManagementServer(serverName);
        }

        @Override
        public Object getTarget() {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            return this;
        }

        public Collection<String> getServerNames() {
            return Collections2.transform(JenkinsUtils.getServers(), new Function<DockerCloud, String>() {
                @Override
                public String apply(@Nullable DockerCloud input) {
                    return input.getDisplayName();
                }
            });
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
                    try(final DockerClient client = dockerApi.getClient()) {
                        containers = client.listContainersCmd().exec();
                    }
                    return "(" + containers.size() + ")";
                } catch(Exception ex) {
                    return "Error";
                }
            }

        }

        public Collection<ServerDetail> getServers() {
            return Collections2.transform(JenkinsUtils.getServers(), new Function<DockerCloud, ServerDetail>() {
                @Override
                public ServerDetail apply(@Nullable DockerCloud input) {
                    return new ServerDetail(input);
                }
            });
        }

}
