package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.nirima.jenkins.plugins.docker.launcher.DockerComputerLauncher;
import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import com.nirima.jenkins.plugins.docker.utils.UniqueIdGenerator;
import hudson.Extension;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Label;
import hudson.model.Node;
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
import org.apache.commons.lang.StringUtils;
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
import java.util.Map;
import java.util.Set;


public class DockerTemplate implements Describable<DockerTemplate> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerTemplate.class.getName());

    private static final UniqueIdGenerator ID_GENERATOR = new UniqueIdGenerator(36);

    /** Default value for {@link #getName()} if {@link #name} is null. */
    private static final String DEFAULT_NAME = "docker";

    private int configVersion = 2;

    private final String labelString;

    private DockerComputerConnector connector;

    @Deprecated
    private transient DockerComputerLauncher launcher;

    public String remoteFs;

    public final int instanceCap;

    private Node.Mode mode = Node.Mode.NORMAL;

    // for backward compatibility reason can't declare this attribute as type DockerOnceRetentionStrategy
    private RetentionStrategy retentionStrategy = new DockerOnceRetentionStrategy(10);

    private DockerTemplateBase dockerTemplateBase;

    private boolean removeVolumes;

    private transient /*almost final*/ Set<LabelAtom> labelSet;

    private @CheckForNull DockerImagePullStrategy pullStrategy = DockerImagePullStrategy.PULL_LATEST;

    private int pullTimeout;

    private List<? extends NodeProperty<?>> nodeProperties = Collections.EMPTY_LIST;

    private @CheckForNull DockerDisabled disabled;

    private @CheckForNull String name;

    /**
     * fully default
     */
    public DockerTemplate() {
        this.labelString = "";
        this.instanceCap = 1;
    }

    public DockerTemplate(@Nonnull DockerTemplateBase dockerTemplateBase,
                          DockerComputerConnector connector,
                          String labelString,
                          String remoteFs,
                          String instanceCapStr) {
        this(dockerTemplateBase, connector, labelString, instanceCapStr);
        setRemoteFs(remoteFs);
    }


    @DataBoundConstructor
    public DockerTemplate(@Nonnull DockerTemplateBase dockerTemplateBase,
                          DockerComputerConnector connector,
                          String labelString,
                          String instanceCapStr
    ) {
        this.dockerTemplateBase = dockerTemplateBase;
        this.connector = connector;
        this.labelString = Util.fixNull(labelString);

        if (Strings.isNullOrEmpty(instanceCapStr)) {
            this.instanceCap = Integer.MAX_VALUE;
        } else {
            this.instanceCap = Integer.parseInt(instanceCapStr);
        }

        labelSet = Label.parse(labelString);
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

    public Integer getShmSize() {
        return dockerTemplateBase.getShmSize();
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
        final CreateContainerCmd result = dockerTemplateBase.fillContainerConfig(containerConfig);
        final String templateName = getName();
        final Map<String, String> labels = result.getLabels();
        labels.put(DockerContainerLabelKeys.REMOVE_VOLUMES, Boolean.toString(isRemoveVolumes()));
        labels.put(DockerContainerLabelKeys.TEMPLATE_NAME, templateName);
        final String nodeName = calcUnusedNodeName(templateName);
        setNodeNameInContainerConfig(result, nodeName);
        return result;
    }

    @Restricted(NoExternalUse.class) // public for tests only
    public static void setNodeNameInContainerConfig(CreateContainerCmd containerConfig, String nodeName) {
        containerConfig.getLabels().put(DockerContainerLabelKeys.NODE_NAME, nodeName);
    }

    /**
     * Retrieves the {@link Node} name chosen by
     * {@link #fillContainerConfig(CreateContainerCmd)}.
     * 
     * @param containerConfig
     *            The {@link CreateContainerCmd} previously returned by
     *            {@link #fillContainerConfig(CreateContainerCmd)}.
     * @return The name that {@link Node#getNodeName()} should return for the
     *         node for the container that will be created by this command.
     */
    public static String getNodeNameFromContainerConfig(CreateContainerCmd containerConfig) {
        return containerConfig.getLabels().get(DockerContainerLabelKeys.NODE_NAME);
    }

    // --

    public String getFullImageId() {
        return dockerTemplateBase.getFullImageId();
    }



    public DockerTemplateBase getDockerTemplateBase() {
        return dockerTemplateBase;
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

    public int getNumExecutors() {
        return 1; // works only with one executor!
    }

    @DataBoundSetter
    public void setRetentionStrategy(DockerOnceRetentionStrategy retentionStrategy) {
        this.retentionStrategy = retentionStrategy;
    }

    public RetentionStrategy getRetentionStrategy() {
        return retentionStrategy;
    }

    public DockerComputerConnector getConnector() {
        return connector;
    }

    public String getRemoteFs() {
        return remoteFs;
    }

    @DataBoundSetter
    public void setRemoteFs(String remoteFs) {
        this.remoteFs = remoteFs;
    }

    public String getInstanceCapStr() {
        if (instanceCap == Integer.MAX_VALUE) {
            return "";
        }
        return String.valueOf(instanceCap);
    }

    public int getInstanceCap() {
        return instanceCap;
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

    public int getPullTimeout() {
        return pullTimeout;
    }

    @DataBoundSetter
    public void setPullTimeout(int pullTimeout) {
        this.pullTimeout = pullTimeout;
    }

    public List<? extends NodeProperty<?>> getNodeProperties() {
        return Collections.unmodifiableList(nodeProperties);
    }
    
    @DataBoundSetter
    public void setNodeProperties(List<? extends NodeProperty<?>> nodeProperties) {
        this.nodeProperties = nodeProperties;
    }

    public DockerDisabled getDisabled() {
        return disabled == null ? new DockerDisabled() : disabled;
    }

    @DataBoundSetter
    public void setDisabled(DockerDisabled disabled) {
        this.disabled = disabled;
    }

    @DataBoundSetter
    public void setName(String name) {
        // only store name if it isn't the default
        if (name == null) {
            this.name = null;
        } else {
            final String trimmedName = name.trim();
            if (trimmedName.equals(DEFAULT_NAME) || trimmedName.isEmpty()) {
                this.name = null;
            } else {
                this.name = trimmedName;
            }
        }
    }

    public String getName() {
        if( name==null || name.trim().isEmpty()) {
            return DEFAULT_NAME;
        }
        return name.trim();
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
    public DockerTemplate cloneWithLabel(String label)  {
        final DockerTemplate template = new DockerTemplate(dockerTemplateBase, connector, label, remoteFs, "1");
        template.setMode(Node.Mode.EXCLUSIVE);
        template.setPullStrategy(pullStrategy);
        template.setRemoveVolumes(removeVolumes);
        template.setRetentionStrategy((DockerOnceRetentionStrategy) retentionStrategy);
        template.setNodeProperties(nodeProperties);
        return template;
    }

    @Override
    public String toString() {
        return "DockerTemplate{" +
                "configVersion=" + configVersion +
                ", labelString='" + labelString + '\'' +
                ", connector=" + connector +
                ", remoteFs='" + remoteFs + '\'' +
                ", instanceCap=" + instanceCap +
                ", mode=" + mode +
                ", retentionStrategy=" + retentionStrategy +
                ", dockerTemplateBase=" + dockerTemplateBase +
                ", removeVolumes=" + removeVolumes +
                ", pullStrategy=" + pullStrategy +
                ", nodeProperties=" + nodeProperties +
                ", disabled=" + disabled +
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

    InspectImageResponse pullImage(DockerAPI api, TaskListener listener) throws IOException, InterruptedException {
        final String image = getFullImageId();

        final boolean shouldPullImage;
        try(final DockerClient client = api.getClient()) {
            shouldPullImage = pullStrategy.shouldPullImage(client, image);
        }
        if (shouldPullImage) {
            // TODO create a FlyWeightTask so end-user get visibility on pull operation progress
            LOGGER.info("Pulling image '{}'. This may take awhile...", image);

            long startTime = System.currentTimeMillis();

            try(final DockerClient client = api.getClient(pullTimeout)) {
                final PullImageCmd cmd =  client.pullImageCmd(image);
                final DockerRegistryEndpoint registry = getRegistry();
                DockerCloud.setRegistryAuthentication(cmd, registry, Jenkins.getInstance());
                cmd.exec(new PullImageResultCallback() {
                    @Override
                    public void onNext(PullResponseItem item) {
                        listener.getLogger().println(item.getStatus());
                    }
                }).awaitCompletion();
            }

            long pullTime = System.currentTimeMillis() - startTime;
            LOGGER.info("Finished pulling image '{}', took {} ms", image, pullTime);
        }

        final InspectImageResponse result;
        try(final DockerClient client = api.getClient()) {
            result = client.inspectImageCmd(image).exec();
        } catch (NotFoundException e) {
            throw new DockerClientException("Could not pull image: " + image, e);
        }
        return result;
    }

    @Restricted(NoExternalUse.class)
    public DockerTransientNode provisionNode(DockerAPI api, TaskListener listener) throws IOException, Descriptor.FormException, InterruptedException {
        try {
            final InspectImageResponse image = pullImage(api, listener);
            if (StringUtils.isBlank(remoteFs)) {
                remoteFs = image.getContainerConfig().getWorkingDir();
            }
            if (StringUtils.isBlank(remoteFs)) {
                remoteFs = "/";
            }

            try(final DockerClient client = api.getClient()) {
                return doProvisionNode(api, client, listener);
            }
        } catch (IOException | Descriptor.FormException | InterruptedException | RuntimeException ex) {
            final DockerCloud ourCloud = DockerCloud.findCloudForTemplate(this);
            final long milliseconds = ourCloud == null ? 0L : ourCloud.getEffectiveErrorDurationInMilliseconds();
            if (milliseconds > 0L) {
                // if anything went wrong, disable ourselves for a while
                final String reason = "Template provisioning failed.";
                final DockerDisabled reasonForDisablement = getDisabled();
                reasonForDisablement.disableBySystem(reason, milliseconds, ex);
                setDisabled(reasonForDisablement);
            }
            throw ex;
        }
    }

    private DockerTransientNode doProvisionNode(DockerAPI api, DockerClient client, TaskListener listener) throws IOException, Descriptor.FormException, InterruptedException {
        LOGGER.info("Trying to run container for {}", getImage());
        final DockerComputerConnector connector = getConnector();

        final CreateContainerCmd cmd = client.createContainerCmd(getImage());
        fillContainerConfig(cmd);

        connector.beforeContainerCreated(api, remoteFs, cmd);

        final String nodeName = getNodeNameFromContainerConfig(cmd);
        LOGGER.info("Trying to run container for node {} from image: {}", nodeName, getImage());
        boolean finallyRemoveTheContainer = true;
        final String containerId = cmd.exec().getId();
        // if we get this far, we have created the container so,
        // if we fail to return the node, we need to ensure it's cleaned up.
        LOGGER.info("Started container ID {} for node {} from image: {}", containerId, nodeName, getImage());

        try {
            connector.beforeContainerStarted(api, remoteFs, containerId);
            client.startContainerCmd(containerId).exec();
            connector.afterContainerStarted(api, remoteFs, containerId);

            final ComputerLauncher launcher = connector.createLauncher(api, containerId, remoteFs, listener);
    
            final DockerTransientNode node = new DockerTransientNode(nodeName, containerId, remoteFs, launcher);
            node.setNodeDescription("Docker Agent [" + getImage() + " on "+ api.getDockerHost().getUri() + " ID " + containerId + "]");
            node.setMode(mode);
            node.setLabelString(labelString);
            node.setRetentionStrategy(retentionStrategy);
            robustlySetNodeProperties(node, nodeProperties);
            node.setRemoveVolumes(removeVolumes);
            node.setDockerAPI(api);
            finallyRemoveTheContainer = false;
            return node;
        } finally {
            // if something went wrong, cleanup aborted container
            // while ensuring that the original exception escapes.
            if ( finallyRemoveTheContainer ) {
                try {
                    client.removeContainerCmd(containerId).withForce(true).exec();
                } catch (NotFoundException ex) {
                    LOGGER.info("Unable to remove container '" + containerId + "' as it had already gone.");
                } catch (Throwable ex) {
                    LOGGER.error("Unable to remove container '" + containerId + "' due to exception:", ex);
                }
            }
        }
    }

    /**
     * Returns a node name for a new node that doesn't clash with any we've
     * currently got.
     * 
     * @param The
     *            template's {@link #getName()}. This is used as a prefix for
     *            the node name.
     * @return A unique unused node name suitable for use as a slave name for a
     *         slave created from this template.
     */
    private static String calcUnusedNodeName(final String templateName) {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        while (true) {
            // make a should-be-unique ID
            final String uniqueId = ID_GENERATOR.getUniqueId();
            final String nodeName = templateName + '-' + uniqueId;
            // now check it doesn't collide with any existing slaves that might
            // have been made previously.
            if (jenkins == null || jenkins.getNode(nodeName) == null) {
                return nodeName;
            }
        }
    }

   /**
     * Workaround for JENKINS-51203. Retries setting node properties until we
     * either give up or we succeed. If we give up, the exception thrown will be
     * the last one encountered.
     * 
     * @param node
     *            The node whose nodeProperties are to be set.
     * @param nodeProperties
     *            The nodeProperties to be set on the node.
     * @throws IOException
     *             if it all failed horribly every time we tried.
     */
    private static void robustlySetNodeProperties(DockerTransientNode node,
            List<? extends NodeProperty<?>> nodeProperties) throws IOException {
        if (nodeProperties == null || nodeProperties.isEmpty()) {
            // no point calling setNodeProperties if we've got nothing to set.
            return;
        }
        final int maxAttempts = 10;
        for (int attempt = 1;; attempt++) {
            try {
                // setNodeProperties can fail at random
                // It does so because it's persisting all Nodes,
                // and if lots of threads all do this at once then they'll
                // collide and fail.
                node.setNodeProperties(nodeProperties);
                return;
            } catch (IOException | RuntimeException ex) {
                if (attempt > maxAttempts) {
                    throw ex;
                }
                final long delayInMilliseconds = 100L * attempt;
                try {
                    Thread.sleep(delayInMilliseconds);
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DockerTemplate template = (DockerTemplate) o;

        if (configVersion != template.configVersion) return false;
        if (instanceCap != template.instanceCap) return false;
        if (removeVolumes != template.removeVolumes) return false;
        if (!labelString.equals(template.labelString)) return false;
        if (!connector.equals(template.connector)) return false;
        if (!remoteFs.equals(template.remoteFs)) return false;
        if (mode != template.mode) return false;
        if (!retentionStrategy.equals(template.retentionStrategy)) return false;
        if (!dockerTemplateBase.equals(template.dockerTemplateBase)) return false;
        if (!pullStrategy.equals(template.pullStrategy)) return false;
        if (!nodeProperties.equals(template.nodeProperties)) return false;
        if (!getDisabled().equals(template.getDisabled())) return false;
        return dockerTemplateBase.equals(template.dockerTemplateBase);
    }

    @Override
    public int hashCode() {
        int result = configVersion;
        result = 31 * result + labelString.hashCode();
        result = 31 * result + connector.hashCode();
        result = 31 * result + remoteFs.hashCode();
        result = 31 * result + instanceCap;
        result = 31 * result + mode.hashCode();
        result = 31 * result + retentionStrategy.hashCode();
        result = 31 * result + dockerTemplateBase.hashCode();
        result = 31 * result + (removeVolumes ? 1 : 0);
        result = 31 * result + labelSet.hashCode();
        result = 31 * result + pullStrategy.hashCode();
        result = 31 * result + nodeProperties.hashCode();
        result = 31 * result + getDisabled().hashCode();
        return result;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<DockerTemplate> {
        /**
         * Get a list of all {@link NodePropertyDescriptor}s we can use to define DockerSlave NodeProperties.
         */
        public List<NodePropertyDescriptor> getNodePropertiesDescriptors() {

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

        public Descriptor getRetentionStrategyDescriptor() {
            return Jenkins.getInstance().getDescriptor(DockerOnceRetentionStrategy.class);
        }

        public FormValidation doCheckPullTimeout(@QueryParameter String value) {
            return FormValidation.validateNonNegativeInteger(value);
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
