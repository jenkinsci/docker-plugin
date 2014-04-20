package com.nirima.jenkins.plugins.docker;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserListBoxModel;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Strings;

import com.nirima.docker.client.DockerException;
import com.nirima.docker.client.DockerClient;
import com.nirima.docker.client.model.*;
import com.trilead.ssh2.Connection;
import hudson.Extension;
import hudson.Util;
import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import hudson.util.ListBoxModel;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.logging.Logger;


public class DockerTemplate implements Describable<DockerTemplate> {
    private static final Logger LOGGER = Logger.getLogger(DockerTemplate.class.getName());


    public final String image;
    public final String labelString;

    // SSH settings
    /**
     * The id of the credentials to use.
     */
    public final String credentialsId;

    /**
     * Field dockerCommand
     */
    public final String dockerCommand;

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


    public final String remoteFs; // = "/home/jenkins";


    public final int instanceCap;
    public final String[] dnsHosts;

    public final boolean tagOnCompletion;

    private transient /*almost final*/ Set<LabelAtom> labelSet;
    public transient DockerCloud parent;

    public final String additionalTag;
    public final boolean pushOnSuccess;

    @DataBoundConstructor
    public DockerTemplate(String image, String labelString,
                          String remoteFs,
                          String credentialsId, String jvmOptions, String javaPath,
                          String prefixStartSlaveCmd, String suffixStartSlaveCmd,
                          boolean tagOnCompletion, String instanceCapStr, String dnsString,
                          String additionalTag, boolean pushOnSuccess, String dockerCommand

    ) {
        this.image = image;
        this.labelString = Util.fixNull(labelString);
        this.credentialsId = credentialsId;
        this.jvmOptions = jvmOptions;
        this.javaPath = javaPath;
        this.prefixStartSlaveCmd = prefixStartSlaveCmd;
        this.suffixStartSlaveCmd = suffixStartSlaveCmd;
        this.remoteFs =  Strings.isNullOrEmpty(remoteFs)?"/home/jenkins":remoteFs;
        this.tagOnCompletion = tagOnCompletion;
        this.dockerCommand = dockerCommand;

        if(instanceCapStr.equals("")) {
            this.instanceCap = Integer.MAX_VALUE;
        } else {
            this.instanceCap = Integer.parseInt(instanceCapStr);
        }

        this.dnsHosts = dnsString.split(" ");
        this.additionalTag = additionalTag;
        this.pushOnSuccess = pushOnSuccess;

        readResolve();
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

    public Descriptor<DockerTemplate> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }

    public Set<LabelAtom> getLabelSet(){
        return labelSet;
    }

    /**
     * Initializes data structure that we don't persist.
     */
    protected Object readResolve() {
        labelSet = Label.parse(labelString);
        return this;
    }

    public String getDisplayName() {
        return "Image of " + image;
    }

    public DockerCloud getParent() {
        return parent;
    }

    public DockerSlave provision(StreamTaskListener listener) throws IOException, Descriptor.FormException, DockerException {
            PrintStream logger = listener.getLogger();


        logger.println("Launching " + image );

        String nodeDescription = "Docker Node";


        int numExecutors = 1;
        Node.Mode mode = Node.Mode.EXCLUSIVE;


        RetentionStrategy retentionStrategy = new DockerRetentionStrategy();//RetentionStrategy.INSTANCE;

        List<? extends NodeProperty<?>> nodeProperties = new ArrayList();

        ContainerInspectResponse containerInspectResponse = provisionNew();
        String containerId = containerInspectResponse.getId();

        ComputerLauncher launcher = new DockerComputerLauncher(this, containerInspectResponse);

        return new DockerSlave(this, containerId,
                containerId.substring(0,12),
                nodeDescription,
                remoteFs, numExecutors, mode, labelString,
                launcher, retentionStrategy, nodeProperties);

    }

    public ContainerInspectResponse provisionNew() throws DockerException {
        DockerClient dockerClient = getParent().connect();

        ContainerConfig containerConfig = new ContainerConfig();
        containerConfig.setImage(image);

        String[] dockerCommandArray;

        if(dockerCommand != null && !dockerCommand.isEmpty()){
            dockerCommandArray = dockerCommand.split(" ");
        } else {
            //default value to preserve comptability
            dockerCommandArray = new String[]{"/usr/sbin/sshd", "-D"};
        }

        containerConfig.setCmd(dockerCommandArray);
        containerConfig.setPortSpecs(new String[]{"22/tcp"});
        //containerConfig.setPortSpecs(new String[]{"22/tcp"});
        //containerConfig.getExposedPorts().put("22/tcp",new ExposedPort());
        if( dnsHosts.length > 0 )
            containerConfig.setDns(dnsHosts);

        ContainerCreateResponse container = dockerClient.containers().create(containerConfig);

        // Launch it.. :
        // MAybe should be in computerLauncher

        Map<String, PortBinding[]> bports = new HashMap<String, PortBinding[]>();
        PortBinding binding = new PortBinding();
        binding.hostIp = "0.0.0.0";
        //binding.hostPort = ":";
        bports.put("22/tcp", new PortBinding[] { binding });

        HostConfig hostConfig = new HostConfig();
        hostConfig.setPortBindings(bports);

        dockerClient.container(container.getId()).start(hostConfig);

        String containerId = container.getId();

        return dockerClient.container(containerId).inspect();
    }

    public int getNumExecutors() {
        return 1;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<DockerTemplate> {

        @Override
        public String getDisplayName() {
            return "Docker Template";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {

            return new SSHUserListBoxModel().withMatching(SSHAuthenticator.matcher(Connection.class),
                    CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context,
                            ACL.SYSTEM, SSHLauncher.SSH_SCHEME));
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
