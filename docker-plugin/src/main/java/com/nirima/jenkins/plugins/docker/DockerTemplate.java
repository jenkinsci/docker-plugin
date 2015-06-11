package com.nirima.jenkins.plugins.docker;

import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.plugins.sshslaves.SSHConnector;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import shaded.com.google.common.base.MoreObjects;
import shaded.com.google.common.base.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;


public class DockerTemplate extends DockerTemplateBackwardCompatibility implements Describable<DockerTemplate> {
    private static final Logger LOGGER = Logger.getLogger(DockerTemplate.class.getName());

    private int configVersion;

    private final String labelString;

    private DockerComputerLauncher launcher;

    /**
     *  Field remoteFSMapping.
     */
    public final String remoteFsMapping;

    public String remoteFs = "/home/jenkins";

    public final int instanceCap;

    private Node.Mode mode = Node.Mode.NORMAL;

    private RetentionStrategy retentionStrategy = new DockerOnceRetentionStrategy(0);

    private int numExecutors = 1;

    private DockerTemplateBase dockerTemplateBase;

    private transient /*almost final*/ Set<LabelAtom> labelSet;
    public transient DockerCloud parent;

    @DataBoundConstructor
    public DockerTemplate(DockerTemplateBase dockerTemplateBase,
                          String labelString,
                          String remoteFs,
                          String remoteFsMapping,
                          String instanceCapStr
    ) {
        this.dockerTemplateBase = dockerTemplateBase;
        this.labelString = Util.fixNull(labelString);
        this.remoteFs =  Strings.isNullOrEmpty(remoteFs) ? "/home/jenkins" : remoteFs;
        this.remoteFsMapping = remoteFsMapping;

        if (instanceCapStr.equals("")) {
            this.instanceCap = Integer.MAX_VALUE;
        } else {
            this.instanceCap = Integer.parseInt(instanceCapStr);
        }

        readResolve();
    }

    public DockerTemplateBase getDockerTemplateBase() {
        return dockerTemplateBase;
    }

    public String getLabelString() {
        return labelString;
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
    public void setLauncher(DockerComputerLauncher launcher) {
        this.launcher = launcher;
    }

    public DockerComputerLauncher getLauncher() {
        return launcher;
    }

    public String getRemoteFs() {
        return remoteFs;
    }

    public String getInstanceCapStr() {
        if (instanceCap==Integer.MAX_VALUE) {
            return "";
        } else {
            return String.valueOf(instanceCap);
        }
    }

    public int getInstanceCap() {
        return instanceCap;
    }

    public String getRemoteFsMapping() {
        return remoteFsMapping;
    }

    public Set<LabelAtom> getLabelSet(){
        return labelSet;
    }

    /**
     * Initializes data structure that we don't persist.
     */
    protected Object readResolve() {

        if (configVersion < 1) {
            // migrate launcher
            final SSHConnector sshConnector = new SSHConnector(22, credentialsId, jvmOptions, javaPath,
                    prefixStartSlaveCmd, suffixStartSlaveCmd, getSSHLaunchTimeoutMinutes() * 60);
            this.launcher = new DockerComputerSSHLauncher(sshConnector);


            // migrate dockerTemplate
//            this.dockerTemplateBase = new DockerTemplateBase(image, dnsString, dockerCommand, volumes) i want sleep...

            configVersion = 1;
        }

        labelSet = Label.parse(labelString); // fails sometimes under debugger

        return this;
    }

    public DockerCloud getParent() {
        return parent;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("image", dockerTemplateBase.getImage())
                .add("parent", parent)
                .toString();
    }

    public Descriptor<DockerTemplate> getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptor(getClass());
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

        @Override
        public String getDisplayName() {
            return "Docker Template";
        }

        public Class getDockerTemplateBase(){
            return DockerTemplateBase.class;
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
    }
}
