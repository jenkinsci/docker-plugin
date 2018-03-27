package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.DockerClient;
import com.nirima.jenkins.plugins.docker.utils.Consts;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import io.jenkins.docker.client.DockerAPI;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;

/**
 * Created by magnayn on 22/02/2014.
 */
public class DockerManagementServer  implements Describable<DockerManagementServer> {
    final String name;
    final DockerCloud theCloud;

    public Descriptor<DockerManagementServer> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(DescriptorImpl.class);
    }

    public String getUrl() {
        return DockerManagement.get().getUrlName() + "/server/" + name;
    }

    public DockerManagementServer(String name) {
        this.name = name;
        theCloud = JenkinsUtils.getServer(name);
    }

    public Collection getImages(){
        final DockerAPI dockerApi = theCloud.getDockerApi();
        try(final DockerClient client = dockerApi.getClient()) {
            return client.listImagesCmd().exec();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public Collection getProcesses() {
        final DockerAPI dockerApi = theCloud.getDockerApi();
        try(final DockerClient client = dockerApi.getClient()) {
            return client.listContainersCmd().exec();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public String asTime(Long time) {
        if( time == null )
            return "";
        long when = System.currentTimeMillis() - time;
        Date dt = new Date(when);
        return dt.toString();
    }

    public String getJsUrl(String jsName) {
        return Consts.PLUGIN_JS_URL + jsName;
    }

    public void doControlSubmit(@QueryParameter("stopId") String stopId, StaplerRequest req, StaplerResponse rsp) throws ServletException,
            IOException,
            InterruptedException {
        final DockerAPI dockerApi = theCloud.getDockerApi();
        try(final DockerClient client = dockerApi.getClient()) {
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
