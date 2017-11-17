package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.nirima.jenkins.plugins.docker.launcher.DockerComputerLauncher;
import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.RetentionStrategy;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;
import io.jenkins.docker.connector.DockerComputerConnector;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


public class DockerTemplate implements Describable<DockerTemplate> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerTemplate.class.getName());

    private int configVersion = 2;

    private final String labelString;

    private DockerComputerConnector connector;

    @Deprecated
    private transient DockerComputerLauncher launcher;

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
    public DockerTemplate(@Nonnull DockerTemplateBase dockerTemplateBase,
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

    public DockerRegistryEndpoint getRegistry() {
        return dockerTemplateBase.getRegistry();
    }

    public CreateContainerCmd fillContainerConfig(CreateContainerCmd containerConfig) {
        return dockerTemplateBase.fillContainerConfig(containerConfig);
    }

    // --

    public String getFullImageId() {
        return dockerTemplateBase.getFullImageId();
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

    public RetentionStrategy getRetentionStrategyCopy() {
        return retentionStrategy;
    }


    @DataBoundSetter
    public void setConnector(DockerComputerConnector connector) {
        this.connector = connector;
    }

    public DockerComputerConnector getConnector() {
        return connector;
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

            try {
                labelSet = Label.parse(labelString); // fails sometimes under debugger
            } catch (Throwable t) {
                LOGGER.error("Can't parse labels: ", t);
            }

            if (connector == null && launcher != null) {
                connector = launcher.convertToConnector();
            }
        } catch (Throwable t) {
            LOGGER.error("Can't convert old values to new (double conversion?): ", t);
        }
        return this;
    }

    @Restricted(NoExternalUse.class)
    public DockerTemplate cloneWithLabel(String label) {
        final DockerTemplate template = new DockerTemplate(dockerTemplateBase, label, remoteFs, remoteFsMapping, "1", nodeProperties);
        template.setConnector(connector);
        template.setMode(Node.Mode.EXCLUSIVE);
        template.setNumExecutors(1);
        template.setPullStrategy(pullStrategy);
        template.setRemoveVolumes(removeVolumes);
        template.setRetentionStrategy(retentionStrategy);
        return template;
    }

    @Override
    public String toString() {
        return "DockerTemplate{" +
                "configVersion=" + configVersion +
                ", labelString='" + labelString + '\'' +
                ", connector=" + connector +
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
        return Objects.toStringHelper(this)
                .add("image", dockerTemplateBase.getImage())
                .toString();
    }

    @Override
    public Descriptor<DockerTemplate> getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptor(getClass());
    }

    void pullImage(DockerAPI api) throws IOException, InterruptedException {

        String image = getFullImageId();
        final DockerClient client = api.getClient();

        if (pullStrategy.shouldPullImage(client, image)) {
            // TODO create a FlyWeightTask so end-user get visibility on pull operation progress
            LOGGER.info("Pulling image '{}'. This may take awhile...", image);

            long startTime = System.currentTimeMillis();

            PullImageCmd cmd =  client.pullImageCmd(image);
            final DockerRegistryEndpoint registry = getRegistry();
            DockerCloud.setRegistryAuthentication(cmd, registry, Jenkins.getInstance());
            cmd.exec(new PullImageResultCallback()).awaitCompletion();

            try {
                client.inspectImageCmd(image).exec();
            } catch (NotFoundException e) {
                throw new DockerClientException("Could not pull image: " + image, e);
            }

            long pullTime = System.currentTimeMillis() - startTime;
            LOGGER.info("Finished pulling image '{}', took {} ms", image, pullTime);
        }

    }

    @Restricted(NoExternalUse.class)
    public Node provisionNode(TaskListener listener, DockerAPI api) throws IOException, Descriptor.FormException, InterruptedException {

        final DockerClient client = api.getClient();
        final DockerComputerConnector connector = getConnector();
        pullImage(api);

        LOGGER.info("Trying to run container for {}", getImage());
        CreateContainerCmd cmd = client.createContainerCmd(getImage());
        fillContainerConfig(cmd);

        connector.beforeContainerCreated(api, this, cmd);

        String containerId = cmd.exec().getId();

        try {
            connector.beforeContainerStarted(api, this, containerId);

            client.startContainerCmd(containerId).exec();

            connector.afterContainerStarted(api, this, containerId);
        } catch (DockerException e) {
            // if something went wrong, cleanup aborted container
            client.removeContainerCmd(containerId).withForce(true).exec();
            throw e;
        }

        final ComputerLauncher launcher = connector.createLauncher(api, containerId, this, listener);
        DockerTransientNode node = new DockerTransientNode(containerId, remoteFs, launcher);
        node.setNodeDescription("Docker Agent [" + getImage() + " on "+ api.getDockerHost().getUri() + "]");
        node.setMode(mode);
        node.setLabelString(labelString);
        node.setRetentionStrategy(retentionStrategy);
        node.setNodeProperties(nodeProperties);
        node.setRemoveVolumes(removeVolumes);
        node.setDockerAPI(api);
        return node;
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

        /**
         * Get a list of all {@link NodePropertyDescriptor}s we can use to define DockerSlave NodeProperties.
         */
        public List<NodePropertyDescriptor> getNodePropertyDescriptors() {

            // Copy/paste hudson.model.Slave.SlaveDescriptor.nodePropertyDescriptors marked as @Restricted for reasons I don't get
            List<NodePropertyDescriptor> result = new ArrayList<NodePropertyDescriptor>();
            Collection<NodePropertyDescriptor> list =
                    (Collection) Jenkins.getInstance().getDescriptorList(NodeProperty.class);
            for (NodePropertyDescriptor npd : DescriptorVisibilityFilter.applyType(DockerTransientNode.class, list)) {
                if (npd.isApplicable(DockerTransientNode.class)) {
                    result.add(npd);
                }
            }


            final Iterator<NodePropertyDescriptor> iterator = result.iterator();
            while (iterator.hasNext()) {
                final NodePropertyDescriptor de = iterator.next();
                // see https://issues.jenkins-ci.org/browse/JENKINS-47697
                if ("org.jenkinsci.plugins.matrixauth.AuthorizationMatrixNodeProperty".equals(de.getKlass().toJavaClass().getName())) {
                    iterator.remove();
                }
            }
            return result;
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
