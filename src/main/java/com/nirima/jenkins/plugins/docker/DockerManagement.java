package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.model.Container;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(DockerManagement.class);

    @Override
    public String getIconFileName() {
        return com.nirima.jenkins.plugins.docker.utils.Consts.PLUGIN_IMAGES_URL + "/48x48/docker.png";
    }

    @Override
    public String getUrlName() {
        return "docker-plugin";
    }

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


    public DescriptorImpl getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(DescriptorImpl.class);
    }

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

        public Object getTarget() {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            return this;
        }

        public Collection<String> getServerNames() {
            return Collections2.transform(JenkinsUtils.getServers(), new Function<DockerCloud, String>() {
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
                    List<Container> containers = cloud.getClient().listContainersCmd().exec();
                    return "(" + containers.size() + ")";
                } catch(Exception ex) {
                    return "Error";
                }
            }

        }

        public Collection<ServerDetail> getServers() {
            return Collections2.transform(JenkinsUtils.getServers(), new Function<DockerCloud, ServerDetail>() {
                public ServerDetail apply(@Nullable DockerCloud input) {
                    return new ServerDetail(input);
                }
            });
        }

}
