package com.nirima.jenkins.plugins.docker;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerException;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.PortBinding;
import com.trilead.ssh2.Connection;

import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.util.ListBoxModel;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundSetter;


public class DockerTemplate extends DockerTemplateBase implements Describable<DockerTemplate> {
    private static final Logger LOGGER = Logger.getLogger(DockerTemplate.class.getName());


    public final String labelString;

    // SSH settings
    /**
     * The id of the credentials to use.
     */
    public final String credentialsId;

    /**
     * Minutes before terminating an idle slave
     */
    public final String idleTerminationMinutes;

    /**
     * Minutes before SSHLauncher times out on launch
     */
    public final String sshLaunchTimeoutMinutes;

    /**
     * Field jvmOptions.
     */
    public final String jvmOptions;

    /**
     * Field javaPath.
     */
    public final String javaPath;

    /**
     * Field prefixStartSlaveCmd.
     */
    public final String prefixStartSlaveCmd;

    /**
     *  Field suffixStartSlaveCmd.
     */
    public final String suffixStartSlaveCmd;

    /**
     *  Field remoteFSMapping.
     */
    public final String remoteFsMapping;

    public final String remoteFs; // = "/home/jenkins";

    public final int instanceCap;

    private Node.Mode mode = Node.Mode.EXCLUSIVE;

    private RetentionStrategy retentionStrategy = new OnceRetentionStrategy(0);

    private transient /*almost final*/ Set<LabelAtom> labelSet;

    public transient DockerCloud parent;


    @DataBoundConstructor
    public DockerTemplate(String image,
                          String labelString,
                          RetentionStrategy retentionStrategy,
                          String remoteFs,
                          String remoteFsMapping,
                          String credentialsId,
                          String idleTerminationMinutes,
                          String sshLaunchTimeoutMinutes,
                          String jvmOptions,
                          String javaPath,
                          Integer memoryLimit,
                          Integer cpuShares,
                          String prefixStartSlaveCmd,
                          String suffixStartSlaveCmd,
                          String instanceCapStr,
                          String dnsString,
                          String dockerCommand,
                          String volumesString,
                          String volumesFrom,
                          String environmentsString,
                          String lxcConfString,
                          String hostname,
                          String bindPorts,
                          boolean bindAllPorts,
                          boolean privileged,
                          boolean tty

    ) {
        super(image, dnsString,dockerCommand,volumesString,volumesFrom,environmentsString,lxcConfString,hostname, memoryLimit, cpuShares,
                Objects.firstNonNull(bindPorts, "0.0.0.0:22"), bindAllPorts,
                privileged, tty);


        this.labelString = Util.fixNull(labelString);
        this.retentionStrategy = retentionStrategy;
        this.credentialsId = credentialsId;
        this.idleTerminationMinutes = idleTerminationMinutes;
        this.sshLaunchTimeoutMinutes = sshLaunchTimeoutMinutes;
        this.jvmOptions = jvmOptions;
        this.javaPath = javaPath;
        this.prefixStartSlaveCmd = prefixStartSlaveCmd;
        this.suffixStartSlaveCmd = suffixStartSlaveCmd;
        this.remoteFs =  Strings.isNullOrEmpty(remoteFs) ? "/home/jenkins" : remoteFs;
        this.remoteFsMapping = remoteFsMapping;

        if (instanceCapStr.equals("")) {
            this.instanceCap = Integer.MAX_VALUE;
        } else {
            this.instanceCap = Integer.parseInt(instanceCapStr);
        }

        readResolve();
    }

    @DataBoundSetter
    public void setMode(Node.Mode mode) {
        this.mode = mode;
    }

    public Node.Mode getMode() {
        return mode;
    }

    public String getInstanceCapStr() {
        if (instanceCap==Integer.MAX_VALUE) {
            return "";
        } else {
            return String.valueOf(instanceCap);
        }
    }

    public String getDnsString() {
        return Joiner.on(" ").join(dnsHosts);
    }

    public String getVolumesString() {
	return Joiner.on(" ").join(volumes);
    }

    public String getVolumesFrom() {
        return volumesFrom;
    }

    public String getRemoteFsMapping() {
        return remoteFsMapping;
    }

    public Descriptor<DockerTemplate> getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptor(getClass());
    }

    public Set<LabelAtom> getLabelSet(){
        return labelSet;
    }

    public int getSSHLaunchTimeoutMinutes() {
        if (sshLaunchTimeoutMinutes == null || sshLaunchTimeoutMinutes.trim().isEmpty()) {
            return 1;
        } else {
            try {
                return Integer.parseInt(sshLaunchTimeoutMinutes);
            } catch (NumberFormatException nfe) {
                LOGGER.log(Level.INFO, "Malformed SSH Launch Timeout value: {0}. Fallback to 1 min.", sshLaunchTimeoutMinutes);
                return 1;
            }
        }
    }
    /**
     * Initializes data structure that we don't persist.
     */
    protected Object readResolve() {
        super.readResolve();

        labelSet = Label.parse(labelString);
        return this;
    }

    public String getDisplayName() {
        return "Image of " + image;
    }

    public DockerCloud getParent() {
        return parent;
    }

    public int getIdleTerminationMinutes() {
        if (idleTerminationMinutes == null || idleTerminationMinutes.trim().isEmpty()) {
            return 0;
        } else {
            try {
                return Integer.parseInt(idleTerminationMinutes);
            } catch (NumberFormatException nfe) {
                LOGGER.log(Level.INFO, "Malformed idleTermination value: {0}. Fallback to 30.", idleTerminationMinutes);
                return 30;
            }
        }
    }

    public DockerSlave provision(StreamTaskListener listener) throws IOException, Descriptor.FormException, DockerException {
        PrintStream logger = listener.getLogger();

        logger.println("Launching " + image );

        int numExecutors = 1;

        RetentionStrategy retentionStrategy = new OnceRetentionStrategy(getIdleTerminationMinutes());

        List<? extends NodeProperty<?>> nodeProperties = new ArrayList();

        InspectContainerResponse containerInspectResponse = provisionNew();
        String containerId = containerInspectResponse.getId();

        ComputerLauncher launcher = new DockerComputerLauncher(this, containerInspectResponse);

        // Build a description up:
        String nodeDescription = "Docker Node [" + image + " on ";
        try {
            nodeDescription += getParent().getDisplayName();
        } catch (Exception ex) {
            nodeDescription += "???";
        }
        nodeDescription += "]";

        String slaveName = containerId.substring(0, 12);

        try {
            slaveName = slaveName + "@" + getParent().getDisplayName();
        } catch(Exception ex) {
            LOGGER.warning("Error fetching cloud name");
        }

        return new DockerSlave(this, containerId,
                slaveName,
                nodeDescription,
                remoteFs, numExecutors, getMode(), memoryLimit, cpuShares, labelString,
                launcher, retentionStrategy, nodeProperties);

    }

    public InspectContainerResponse provisionNew() throws DockerException {
        DockerClient dockerClient = getParent().connect();
        return provisionNew(dockerClient);
    }

    public int getNumExecutors() {
        return 1;
    }

    @Override
    public String[] getDockerCommandArray() {
        String[] cmd = super.getDockerCommandArray();

        if( cmd.length == 0 ) {
            //default value to preserve compatibility
            cmd = new String[]{"/usr/sbin/sshd", "-D"};
        }

        return cmd;
    }

    @Override
    /**
     * Provide a sensible default - templates are for slaves, and you're mostly going
     * to want port 22 exposed.
     */
    public Iterable<PortBinding> getPortMappings() {

        if(Strings.isNullOrEmpty(bindPorts) ) {
             return ImmutableList.<PortBinding>builder()
                .add(PortBinding.parse("0.0.0.0::22"))
                 .build();
        }
        return super.getPortMappings();
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<DockerTemplate> {

        @Override
        public String getDisplayName() {
            return "Docker Template";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            return new SSHUserListBoxModel().withMatching(
                    SSHAuthenticator.matcher(Connection.class),
                    CredentialsProvider.lookupCredentials(
                            StandardUsernameCredentials.class,
                            context,
                            ACL.SYSTEM,
                            SSHLauncher.SSH_SCHEME)
            );
        }

    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("image", image)
                .add("parent", parent)
                .toString();
    }
}
