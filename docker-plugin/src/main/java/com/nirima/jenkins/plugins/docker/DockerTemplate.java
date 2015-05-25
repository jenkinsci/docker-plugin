package com.nirima.jenkins.plugins.docker;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerException;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.api.model.VolumesFrom;
import com.nirima.jenkins.plugins.docker.strategy.DockerCloudRetentionStrategy;
import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import com.trilead.ssh2.Connection;

import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import shaded.com.google.common.base.Objects;
import shaded.com.google.common.base.Strings;
import shaded.com.google.common.collect.ImmutableList;

import javax.ws.rs.ProcessingException;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


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
     * @deprecated  migrated to retention strategy?
     */
    @Deprecated
    private String idleTerminationMinutes;

    /**
     * Minutes before SSHLauncher times out on launch
     */
    @Deprecated
    private String sshLaunchTimeoutMinutes;

    /**
     * Field jvmOptions.
     */
    @Deprecated
    private String jvmOptions;

    /**
     * Field javaPath.
     */
    @Deprecated
    private String javaPath;

    /**
     * Field prefixStartSlaveCmd.
     */
    @Deprecated
    private String prefixStartSlaveCmd;

    /**
     *  Field suffixStartSlaveCmd.
     */
    @Deprecated
    public String suffixStartSlaveCmd;

    private DockerComputer launcher;

    /**
     *  Field remoteFSMapping.
     */
    public final String remoteFsMapping;

    public String remoteFs = "/home/jenkins";

    public final int instanceCap;

    private Node.Mode mode = Node.Mode.NORMAL;

    private RetentionStrategy retentionStrategy = new DockerOnceRetentionStrategy(0);

    private transient /*almost final*/ Set<LabelAtom> labelSet;

    public transient DockerCloud parent;

    private int numExecutors = 1;

    @DataBoundConstructor
    public DockerTemplate(String image,
                          String labelString,
                          String remoteFs,
                          String remoteFsMapping,
                          String credentialsId,
                          Integer memoryLimit,
                          Integer cpuShares,
                          String instanceCapStr,
                          String dnsString,
                          String dockerCommand,
                          String volumesString,
                          String volumesFromString,
                          String environmentsString,
                          String lxcConfString,
                          String hostname,
                          String bindPorts,
                          boolean bindAllPorts,
                          boolean privileged,
                          boolean tty,
                          String macAddress
    ) {
        super(image, dnsString,dockerCommand,volumesString,volumesFromString,environmentsString,lxcConfString,hostname, memoryLimit, cpuShares,
                Objects.firstNonNull(bindPorts, "0.0.0.0:22"), bindAllPorts,
                privileged, tty, macAddress);


        this.labelString = Util.fixNull(labelString);
        this.credentialsId = credentialsId;
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

    /**
     * Experimental option allows set number of executors
     */
    @DataBoundSetter
    public void setNumExecutors(int numExecutors) {
        this.numExecutors = numExecutors;
    }

    public int getNumExecutors() {
        if (getRetentionStrategy() instanceof DockerOnceRetentionStrategy) {
            return 1; // works only with one executor!
        }

        return numExecutors;
    }

    @DataBoundSetter
    public void setRetentionStrategy(RetentionStrategy retentionStrategy) {
        this.retentionStrategy = retentionStrategy;
    }

    public RetentionStrategy getRetentionStrategy() {
        return retentionStrategy;
    }

    @DataBoundSetter
    public void setLauncher(ComputerLauncher launcher) {
        this.launcher = launcher;
    }

    public ComputerLauncher getLauncher() {
        return launcher;
    }

    public String getInstanceCapStr() {
        if (instanceCap==Integer.MAX_VALUE) {
            return "";
        } else {
            return String.valueOf(instanceCap);
        }
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

    public DockerCloud getParent() {
        return parent;
    }

    /**
     * @deprecated migrated to retention strategy?
     */
    @Deprecated
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

        logger.println("Launching " + getImage() );

        List<? extends NodeProperty<?>> nodeProperties = new ArrayList<>();

        String containerId = provisionNew();
        DockerClient client = getParent().connect();
        InspectContainerResponse containerInspectResponse = null;
        try {
            containerInspectResponse = client.inspectContainerCmd(containerId).exec();
        } catch(ProcessingException ex) {
            client.removeContainerCmd(containerId).withForce(true).exec();
            throw ex;
        }

//        ComputerLauncher launcher = new DockerComputerSSHLauncher(this, containerInspectResponse);
        getLauncher().make;
//        launcher.se
        // Build a description up:
        String nodeDescription = "Docker Node [" + getImage() + " on ";
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
                remoteFs, getNumExecutors(), getMode(), labelString,
                launcher, getRetentionStrategy(), nodeProperties);

    }

    public String provisionNew() throws DockerException {
        DockerClient dockerClient = getParent().connect();
        return provisionNew(dockerClient);
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


    /**
     * Provide a sensible default - templates are for slaves, and you're mostly going
     * to want port 22 exposed.
     */
    @Override
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
        public FormValidation doCheckNumExecutors(@QueryParameter int numExecutors) {
            if (numExecutors > 1) {
                return FormValidation.warning("Experimental, see help");
            } else if (numExecutors < 1) {
                return FormValidation.error("Must be > 0");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckVolumesString(@QueryParameter String volumesString) {
            try {
                final String[] strings = splitAndFilterEmpty(volumesString, "\n");
                for (String s : strings) {
                    if (s.equals("/")) {
                        return FormValidation.error("Invalid volume: path can't be '/'");
                    }

                    final String[] group = s.split(":");
                    if (group.length > 3) {
                        return FormValidation.error("Wrong syntax: " + s);
                    } else if (group.length == 2 || group.length == 3) {
                        if (group[1].equals("/")) {
                            return FormValidation.error("Invalid bind mount: destination can't be '/'");
                        }
                        Bind.parse(s);
                    } else if (group.length == 1) {
                        new Volume(s);
                    } else {
                        return FormValidation.error("Wrong line: " + s);
                    }
                }
            } catch (Throwable t) {
                return FormValidation.error(t.getMessage());
            }

            return FormValidation.ok();

        }

        public FormValidation doCheckVolumesFromString(@QueryParameter String volumesFromString) {
            try {
                final String[] strings = splitAndFilterEmpty(volumesFromString, "\n");
                for (String volFrom : strings) {
                    VolumesFrom.parse(volFrom);
                }
            } catch (Throwable t) {
                return FormValidation.error(t.getMessage());
            }

            return FormValidation.ok();
        }

        @Restricted(DoNotUse.class)
        public List<Descriptor<RetentionStrategy<?>>> getDockerRetentionStrategies() {
            List<Descriptor<RetentionStrategy<?>>> strategies = new ArrayList<>();
            strategies.add(DockerOnceRetentionStrategy.DESCRIPTOR);
            strategies.add(DockerCloudRetentionStrategy.DESCRIPTOR);
            strategies.addAll(RetentionStrategy.all());
            return strategies;
        }

        @Override
        public String getDisplayName() {
            return "Docker Template";
        }

        public Class getDockerTemplateBase(){
            return DockerTemplateBase.class;
        }

        public static ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {
            return new SSHUserListBoxModel().withMatching(
                    SSHAuthenticator.matcher(Connection.class),
                    CredentialsProvider.lookupCredentials(
                            StandardUsernameCredentials.class,
                            context,
                            ACL.SYSTEM,
                            SSHLauncher.SSH_SCHEME)
            );
        }

        public static List<Descriptor<ComputerLauncher>> getDockerComputerLauncherDescriptors() {
            List<Descriptor<ComputerLauncher>> r = new ArrayList<Descriptor<ComputerLauncher>>();
            for (Descriptor<ComputerLauncher> d : Functions.getComputerLauncherDescriptors()) {
                if (DockerComputerLauncher.class.isAssignableFrom(d.clazz)) {
                    r.add(d);
                }
            }
            return r;
        }

//        public List<DockerComputerLauncher> getDockerComputerLaunchers() {
//            final ArrayList<DockerComputerLauncher> dockerComputerLaunchers = new ArrayList<DockerComputerLauncher>();
//            dockerComputerLaunchers.add()
//        }


    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("image", getImage())
                .add("parent", parent)
                .toString();
    }
}
