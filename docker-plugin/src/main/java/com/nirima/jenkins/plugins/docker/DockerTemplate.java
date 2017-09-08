package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.core.NameParser;
import com.nirima.jenkins.plugins.docker.launcher.DockerComputerJNLPLauncher;
import com.nirima.jenkins.plugins.docker.launcher.DockerComputerLauncher;
import com.nirima.jenkins.plugins.docker.launcher.DockerComputerSSHLauncher;
import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.RetentionStrategy;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import io.jenkins.docker.DockerSlaveProvisioner;
import io.jenkins.docker.JNLPDockerSlaveProvisioner;
import io.jenkins.docker.SSHDockerSlaveProvisioner;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import shaded.com.google.common.base.MoreObjects;
import shaded.com.google.common.base.Strings;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


public class DockerTemplate extends DockerTemplateBackwardCompatibility implements Describable<DockerTemplate> {
    private static final Logger LOGGER = Logger.getLogger(DockerTemplate.class.getName());

    private int configVersion = 2;

    private final String labelString;

    private DockerComputerLauncher launcher;

    public final String remoteFsMapping;

    public String remoteFs = "/home/jenkins";

    public final int instanceCap;

    private Node.Mode mode = Node.Mode.NORMAL;

    private RetentionStrategy retentionStrategy = new DockerOnceRetentionStrategy(10);

    private int numExecutors = 1;

    private DockerTemplateBase dockerTemplateBase;

    private boolean removeVolumes;

    private transient /*almost final*/ Set<LabelAtom> labelSet;

    private @CheckForNull DockerImagePullStrategy pullStrategy = DockerImagePullStrategy.PULL_LATEST;
        
    private DescribableList<NodeProperty<?>, NodePropertyDescriptor> nodeProperties = 
        new DescribableList<NodeProperty<?>, NodePropertyDescriptor>(Jenkins.getInstance());


    /**
     * fully default
     */
    public DockerTemplate() {
        this.labelString = "";
        this.remoteFsMapping = Jenkins.getInstance().getRootDir().getAbsolutePath();
        this.instanceCap = 1;
    }

    @DataBoundConstructor
    public DockerTemplate(DockerTemplateBase dockerTemplateBase,
                          String labelString,
                          String remoteFs,
                          String remoteFsMapping,
                          String instanceCapStr,
                          List<? extends NodeProperty<?>> nodeProperties
    ) {
        this.dockerTemplateBase = dockerTemplateBase;
        this.labelString = Util.fixNull(labelString);
        this.remoteFs = Strings.isNullOrEmpty(remoteFs) ? "/home/jenkins" : remoteFs;
        this.remoteFsMapping = remoteFsMapping;

        if (instanceCapStr.equals("")) {
            this.instanceCap = Integer.MAX_VALUE;
        } else {
            this.instanceCap = Integer.parseInt(instanceCapStr);
        }

        labelSet = Label.parse(labelString);
        
        this.nodeProperties.clear();
        if (nodeProperties != null) {
            this.nodeProperties.addAll(nodeProperties);
        }
    }

    /**
     * Contains all available arguments
     * @throws IOException 
     */
    @Restricted(value = NoExternalUse.class)
    public DockerTemplate(DockerTemplateBase dockerTemplateBase,
                          String labelString,
                          String remoteFs,
                          String remoteFsMapping,
                          String instanceCapStr,
                          List<? extends NodeProperty<?>> nodeProperties,
                          Node.Mode mode,
                          int numExecutors,
                          DockerComputerLauncher launcher,
                          RetentionStrategy retentionStrategy,
                          boolean removeVolumes,
                          DockerImagePullStrategy pullStrategy) {
        this(dockerTemplateBase,
                labelString,
                remoteFs,
                remoteFsMapping,
                instanceCapStr,
                nodeProperties);
        setMode(mode);
        setNumExecutors(numExecutors);
        setLauncher(launcher);
        setRetentionStrategy(retentionStrategy);
        setRemoveVolumes(removeVolumes);
        setPullStrategy(pullStrategy);
    }

    public DockerSlaveProvisioner getProvisioner(DockerCloud cloud) {
        if (launcher instanceof DockerComputerJNLPLauncher) {
            return new JNLPDockerSlaveProvisioner(cloud, this, cloud.getClient(), (DockerComputerJNLPLauncher) launcher);
        } else {
            return new SSHDockerSlaveProvisioner(cloud, this, cloud.getClient(), (DockerComputerSSHLauncher) launcher);
        }
    }

    // -- DockerTemplateBase mixin

    public static String[] filterStringArray(String[] arr) {
        return DockerTemplateBase.filterStringArray(arr);
    }

    public String getImage() {
        return dockerTemplateBase.getImage();
    }

    public String getDnsString() {
        return dockerTemplateBase.getDnsString();
    }

    @CheckForNull
    public String[] getVolumes() {
        return dockerTemplateBase.getVolumes();
    }

    public String getVolumesString() {
        return dockerTemplateBase.getVolumesString();
    }

    @Deprecated
    public String getVolumesFrom() {
        return dockerTemplateBase.getVolumesFrom();
    }

    public String[] getVolumesFrom2() {
        return dockerTemplateBase.getVolumesFrom2();
    }

    public String getVolumesFromString() {
        return dockerTemplateBase.getVolumesFromString();
    }

    @CheckForNull
    public String getMacAddress() {
        return dockerTemplateBase.getMacAddress();
    }

    public String getDisplayName() {
        return dockerTemplateBase.getDisplayName();
    }

    public Integer getMemoryLimit() {
        return dockerTemplateBase.getMemoryLimit();
    }

    public Integer getMemorySwap() {
        return dockerTemplateBase.getMemorySwap();
    }

    public Integer getCpuShares() {
        return dockerTemplateBase.getCpuShares();
    }

    public String[] getDockerCommandArray() {
        return dockerTemplateBase.getDockerCommandArray();
    }

    public Iterable<PortBinding> getPortMappings() {
        return dockerTemplateBase.getPortMappings();
    }

    public String getEnvironmentsString() {
        return dockerTemplateBase.getEnvironmentsString();
    }

    @CheckForNull
    public List<String> getExtraHosts() {
        return dockerTemplateBase.getExtraHosts();
    }

    public String getExtraHostsString() {
        return dockerTemplateBase.getExtraHostsString();
    }

    public CreateContainerCmd fillContainerConfig(CreateContainerCmd containerConfig) {
        return dockerTemplateBase.fillContainerConfig(containerConfig);
    }

    // --

    public String getFullImageId() {
        NameParser.ReposTag repostag = NameParser.parseRepositoryTag(dockerTemplateBase.getImage());
        // if image was specified without tag, then treat as latest
        return repostag.repos + ":" + (repostag.tag.isEmpty() ? "latest" : repostag.tag);
    }



    public DockerTemplateBase getDockerTemplateBase() {
        return dockerTemplateBase;
    }

    public void setDockerTemplateBase(DockerTemplateBase dockerTemplateBase) {
        this.dockerTemplateBase = dockerTemplateBase;
    }

    public boolean isRemoveVolumes() {
        return removeVolumes;
    }

    @DataBoundSetter
    public void setRemoveVolumes(boolean removeVolumes) {
        this.removeVolumes = removeVolumes;
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

    /**
     * tmp fix for terminating boolean caching
     */
    public RetentionStrategy getRetentionStrategyCopy() {
        if (retentionStrategy instanceof DockerOnceRetentionStrategy) {
            DockerOnceRetentionStrategy onceRetention = (DockerOnceRetentionStrategy) retentionStrategy;
            return new DockerOnceRetentionStrategy(onceRetention.getIdleMinutes());
        }
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
        if (instanceCap == Integer.MAX_VALUE) {
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

    public Set<LabelAtom> getLabelSet() {
        return labelSet;
    }

    public DockerImagePullStrategy getPullStrategy() {
        return pullStrategy;
    }

    @DataBoundSetter
    public void setPullStrategy(DockerImagePullStrategy pullStrategy) {
        this.pullStrategy = pullStrategy;
    }
    
    public List<? extends NodeProperty<?>> getNodeProperties() {
        return Collections.<NodeProperty<?>>unmodifiableList(nodeProperties);
    }
    
    @DataBoundSetter
    public void setNodeProperties(List<? extends NodeProperty<?>> nodeProperties) throws IOException {
        this.nodeProperties.replaceBy(nodeProperties);
    }
    
    /**
     * Xstream ignores default field values, so set them explicitly
     */
    private void configDefaults() {
        if (mode == null) {
            mode = Node.Mode.NORMAL;
        }
        if (retentionStrategy == null) {
            retentionStrategy = new DockerOnceRetentionStrategy(10);
        }
        if (pullStrategy == null) {
            pullStrategy = DockerImagePullStrategy.PULL_LATEST;
        }
        if (nodeProperties == null) {
            nodeProperties = 
                new DescribableList<NodeProperty<?>, NodePropertyDescriptor>(Jenkins.getInstance());
        }
    }

    /**
     * Initializes data structure that we don't persist.
     */
    public Object readResolve() {
        try {
            if (configVersion < 1) {
                convert1();
                configVersion = 1;
            }

            // https://github.com/jenkinsci/docker-plugin/issues/270
            if (configVersion < 2) {
                if (retentionStrategy instanceof DockerOnceRetentionStrategy) {
                    DockerOnceRetentionStrategy tmpStrategy = (DockerOnceRetentionStrategy) retentionStrategy;
                    if (tmpStrategy.getIdleMinutes() == 0) {
                        setRetentionStrategy(new DockerOnceRetentionStrategy(10));
                    }
                }
                configVersion = 2;
            } else {
               configDefaults();
            }
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Can't convert old values to new (double conversion?): ", t);
        }

        try {
            labelSet = Label.parse(labelString); // fails sometimes under debugger
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Can't parse labels: ", t);
        }

        return this;
    }

    @Override
    public String toString() {
        return "DockerTemplate{" +
                "configVersion=" + configVersion +
                ", labelString='" + labelString + '\'' +
                ", launcher=" + launcher +
                ", remoteFsMapping='" + remoteFsMapping + '\'' +
                ", remoteFs='" + remoteFs + '\'' +
                ", instanceCap=" + instanceCap +
                ", mode=" + mode +
                ", retentionStrategy=" + retentionStrategy +
                ", numExecutors=" + numExecutors +
                ", dockerTemplateBase=" + dockerTemplateBase +
                ", removeVolumes=" + removeVolumes +
                ", pullStrategy=" + pullStrategy +
                ", nodeProperties=" + nodeProperties +
                '}';
    }

    public String getShortDescription() {
        return MoreObjects.toStringHelper(this)
                .add("image", dockerTemplateBase.getImage())
                .toString();
    }

    @Override
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

        public Class getDockerTemplateBase() {
            return DockerTemplateBase.class;
        }
    }
}
