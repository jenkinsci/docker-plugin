package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.DockerClient;
import com.nirima.jenkins.plugins.docker.utils.Consts;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import io.jenkins.docker.client.DockerAPI;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Created by magnayn on 22/02/2014.
 */
public class DockerManagementServer implements Describable<DockerManagementServer> {
    final String name;
    final DockerCloud theCloud;

    @Override
    public Descriptor<DockerManagementServer> getDescriptor() {
        return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
    }

    public String getUrl() {
        return DockerManagement.get().getUrlName() + "/server/" + name;
    }

    public DockerManagementServer(String name) {
        this.name = name;
        theCloud = JenkinsUtils.getCloudByNameOrThrow(name);
    }

    public Collection getImages() {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return Collections.emptyList();
        }
        final DockerAPI dockerApi = theCloud.getDockerApi();
        try (final DockerClient client = dockerApi.getClient()) {
            return client.listImagesCmd().exec();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public Collection getProcesses() {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return Collections.emptyList();
        }
        final DockerAPI dockerApi = theCloud.getDockerApi();
        try (final DockerClient client = dockerApi.getClient()) {
            return client.listContainersCmd().exec();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public String asTime(Long time) {
        if (time == null) {
            return "";
        }
        long when = System.currentTimeMillis() - time;
        Date dt = new Date(when);
        return dt.toString();
    }

    public String getJsUrl(String jsName) {
        return Consts.PLUGIN_JS_URL + jsName;
    }

    @SuppressWarnings("unused")
    @RequirePOST
    public void doControlSubmit(@QueryParameter("stopId") String stopId, StaplerRequest req, StaplerResponse rsp)
            throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        final DockerAPI dockerApi = theCloud.getDockerApi();
        try (final DockerClient client = dockerApi.getClient()) {
            client.stopContainerCmd(stopId).exec();
        }
        rsp.sendRedirect(".");
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<DockerManagementServer> {
        @Override
        public String getDisplayName() {
            return "server ";
        }
    }
}
