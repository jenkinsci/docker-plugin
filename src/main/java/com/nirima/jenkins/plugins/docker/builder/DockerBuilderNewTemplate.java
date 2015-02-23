package com.nirima.jenkins.plugins.docker.builder;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.ItemGroup;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.slaves.Cloud;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.AncestorInPath;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.trilead.ssh2.Connection;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerCloud;

import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;



/**
 * Created by Jocelyn De La Rosa on 14/05/2014.
 */
public class DockerBuilderNewTemplate extends Builder implements Serializable {
    private static final Logger LOGGER = Logger.getLogger(DockerBuilderNewTemplate.class.getName());

    public final String image;
    public final String labelString;
    public final String remoteFsMapping;
    public final String remoteFs;
    public final String credentialsId;
    public final String idleTerminationMinutes;
    public final String sshLaunchTimeoutMinutes;
    public final String jvmOptions;
    public final String javaPath;
    public final String prefixStartSlaveCmd;
    public final String suffixStartSlaveCmd;
    public final String instanceCapStr;
    public final String dnsString;
    public final String dockerCommand;
    public final String volumesString;
    public final String volumesFrom;
    public final String environmentsString;
    public final String lxcConfString;
    public final String bindPorts;
    public final boolean bindAllPorts;
    public final boolean privileged;
    public final String hostname;

    @DataBoundConstructor
    public DockerBuilderNewTemplate(String image, String labelString, String remoteFs, String remoteFsMapping,
                                              String credentialsId, String idleTerminationMinutes,
                                              String sshLaunchTimeoutMinutes,
                                              String jvmOptions, String javaPath,
                                              String prefixStartSlaveCmd, String suffixStartSlaveCmd,
                                              String instanceCapStr, String dnsString,
                                              String dockerCommand,
                                              String volumesString, String volumesFrom,
                                              String environmentsString,
                                              String lxcConfString,
                                              String hostname,
                                              String bindPorts,
                                              boolean bindAllPorts,
                                              boolean privileged) {

        this.image = image;
        this.labelString = labelString;
        this.remoteFs = remoteFs;
        this.remoteFsMapping = remoteFsMapping;
        this.credentialsId = credentialsId;
        this.idleTerminationMinutes = idleTerminationMinutes;
        this.sshLaunchTimeoutMinutes = sshLaunchTimeoutMinutes;
        this.jvmOptions = jvmOptions;
        this.javaPath = javaPath;
        this.prefixStartSlaveCmd = prefixStartSlaveCmd;
        this.suffixStartSlaveCmd = suffixStartSlaveCmd;
        this.instanceCapStr = instanceCapStr;
        this.dnsString = dnsString;
        this.dockerCommand = dockerCommand;
        this.volumesString = volumesString;
        this.volumesFrom = volumesFrom;
        this.environmentsString = environmentsString;
        this.lxcConfString = lxcConfString;
        this.bindPorts = bindPorts;
        this.bindAllPorts = bindAllPorts;
        this.privileged = privileged;
        this.hostname = hostname;
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

            return new SSHUserListBoxModel().withMatching(SSHAuthenticator.matcher(Connection.class),
                    CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context,
                            ACL.SYSTEM, SSHLauncher.SSH_SCHEME));
        }
    }


    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        for (Cloud c : Jenkins.getInstance().clouds) {
            if (c instanceof DockerCloud && ((DockerCloud) c).getTemplate(image) == null) {
                LOGGER.log(Level.INFO, "Adding new template « "+image+" » to cloud " + ((DockerCloud) c).name);
                DockerTemplate t = new DockerTemplate(image, labelString, remoteFs, remoteFsMapping, 
                        credentialsId, idleTerminationMinutes,
                        sshLaunchTimeoutMinutes,
                        jvmOptions, javaPath,
                        prefixStartSlaveCmd,
                        suffixStartSlaveCmd, instanceCapStr,
                        dnsString, dockerCommand,
                        volumesString, volumesFrom, environmentsString, lxcConfString, hostname, bindPorts, bindAllPorts, privileged);
                ((DockerCloud) c).addTemplate(t);
            }
        }

        return true;
    }
}
