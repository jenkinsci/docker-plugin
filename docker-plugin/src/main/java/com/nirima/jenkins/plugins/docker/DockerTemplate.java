package com.nirima.jenkins.plugins.docker;

import com.nirima.jenkins.plugins.docker.strategy.DockerCloudRetentionStrategy;
import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import shaded.com.google.common.base.MoreObjects;
import shaded.com.google.common.base.Strings;
import shaded.com.google.common.collect.ImmutableList;

import javax.ws.rs.ProcessingException;
import java.io.IOException;
import java.io.PrintStream;
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

        labelSet = Label.parse(labelString);
    }

    public DockerTemplateBase getDockerTemplateBase() {
        return dockerTemplateBase;
    }

    public void setDockerTemplateBase(DockerTemplateBase dockerTemplateBase) {
        this.dockerTemplateBase = dockerTemplateBase;
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
    public Object readResolve() {
        if (configVersion < 1) {
            convert1();
            configVersion = 1;
        }

        labelSet = Label.parse(labelString); // fails sometimes under debugger

        return this;
    }

    @Override
    public String toString() {
        return "DockerTemplate{" +
                "labelString='" + labelString + '\'' +
                ", launcher=" + launcher +
                ", remoteFsMapping='" + remoteFsMapping + '\'' +
                ", remoteFs='" + remoteFs + '\'' +
                ", instanceCap=" + instanceCap +
                ", mode=" + mode +
                ", retentionStrategy=" + retentionStrategy +
                ", numExecutors=" + numExecutors +
                ", dockerTemplateBase=" + dockerTemplateBase +
                '}';
    }

    public String getShortDescription() {
        return MoreObjects.toStringHelper(this)
                .add("image", dockerTemplateBase.getImage())
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

        public static List<Descriptor<ComputerLauncher>> getDockerComputerLauncherDescriptors() {
            List<Descriptor<ComputerLauncher>> r = new ArrayList<>();
            for (Descriptor<ComputerLauncher> d : Functions.getComputerLauncherDescriptors()) {
                if (DockerComputerLauncher.class.isAssignableFrom(d.clazz)) {
                    r.add(d);
                }
            }
            return r;
        }
    }
}
