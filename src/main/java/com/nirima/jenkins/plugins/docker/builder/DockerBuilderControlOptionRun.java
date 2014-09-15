package com.nirima.jenkins.plugins.docker.builder;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.nirima.docker.client.DockerClient;
import com.nirima.docker.client.DockerException;
import com.nirima.docker.client.model.Identifier;
import com.nirima.jenkins.plugins.docker.DockerSimpleTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import com.trilead.ssh2.Connection;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Describable;
import hudson.model.ItemGroup;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by magnayn on 30/01/2014.
 */
public class DockerBuilderControlOptionRun extends DockerBuilderControlCloudOption {

    public final String image;
    public final String dnsString;
    public final String dockerCommand;
    public final String volumesString;
    public final String volumesFrom;
    public final String lxcConfString;
    public final boolean privileged;
    public final String hostname;
    public final String bindPorts;
    public final boolean bindAllPorts;

    @DataBoundConstructor
    public DockerBuilderControlOptionRun( String cloudName,
            String image,
            String lxcConfString,
            String dnsString,
            String dockerCommand,
            String volumesString, String volumesFrom,
            String hostname,
            String bindPorts,
            boolean bindAllPorts,
            boolean privileged) {
        super(cloudName);
        this.image = image;

        this.lxcConfString = lxcConfString;
        this.dnsString = dnsString;
        this.dockerCommand = dockerCommand;
        this.volumesString = volumesString;
        this.volumesFrom = volumesFrom;
        this.privileged = privileged;
        this.hostname = hostname;
        this.bindPorts = bindPorts;
        this.bindAllPorts = bindAllPorts;
    }

    @Override
    public void execute(AbstractBuild<?, ?> build) throws DockerException, IOException {
        DockerClient client = getClient(build);

        InputStream result = client.createPullCommand()
                .image( Identifier.fromCompoundString(image))
                .execute();

        String res = IOUtils.toString(result);
        System.out.println(res);

        DockerTemplateBase template = new DockerSimpleTemplate(image,
                dnsString, dockerCommand,
                volumesString, volumesFrom, lxcConfString, hostname, bindPorts, bindAllPorts, privileged);

        String containerId = template.provisionNew(client).getId();

        LOGGER.info("Started container " + containerId);
        getLaunchAction(build).started(client, containerId);
    }

    @Extension
    public static final class DescriptorImpl extends DockerBuilderControlOptionDescriptor  {
        @Override
        public String getDisplayName() {
            return "Run Container";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {

            return new SSHUserListBoxModel().withMatching(SSHAuthenticator.matcher(Connection.class),
                    CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context,
                            ACL.SYSTEM, SSHLauncher.SSH_SCHEME));
        }
    }



}
