package com.nirima.jenkins.plugins.docker.builder;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.trilead.ssh2.Connection;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.ItemGroup;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.slaves.Cloud;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;


/**
 * Builder that adds template to all clouds.
 *
 * @author Jocelyn De La Rosa
 */
public class DockerBuilderNewTemplate extends Builder implements Serializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerBuilderNewTemplate.class);

    private DockerTemplate dockerTemplate;
    private int version = 1;

    @DataBoundConstructor
    public DockerBuilderNewTemplate(DockerTemplate dockerTemplate) {
        this.dockerTemplate = dockerTemplate;
    }

    public DockerTemplate getDockerTemplate() {
        return dockerTemplate;
    }

    public void setDockerTemplate(DockerTemplate dockerTemplate) {
        this.dockerTemplate = dockerTemplate;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        final PrintStream llogger = listener.getLogger();
        final String dockerImage = dockerTemplate.getDockerTemplateBase().getImage();

        // Job must run as Admin as we are changing global cloud configuration here.
        build.getACL().checkPermission(Jenkins.ADMINISTER);

        for (Cloud c : Jenkins.getInstance().clouds) {
            if (c instanceof DockerCloud && dockerImage != null) {
                DockerCloud dockerCloud = (DockerCloud) c;
                if (dockerCloud.getTemplate(dockerImage) == null) {
                    LOGGER.info("Adding new template: '{}', to cloud: '{}'", dockerImage, dockerCloud.name);
                    llogger.println("Adding new template: '" + dockerImage + "', to cloud: '" + dockerCloud.name + "'");
                    dockerCloud.addTemplate(dockerTemplate);
                }
            }
        }

        return true;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Add a new template to all docker clouds";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {

            AccessControlled ac = (context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance());
            if (!ac.hasPermission(Jenkins.ADMINISTER)) {
                return new ListBoxModel();
            }

            return new SSHUserListBoxModel().withMatching(SSHAuthenticator.matcher(Connection.class),
                    CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context,
                            ACL.SYSTEM, SSHLauncher.SSH_SCHEME));
        }
    }
}
