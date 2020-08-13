package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.command.PullImageResultCallback;
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
import hudson.util.FormValidation;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;
import io.jenkins.docker.connector.DockerComputerConnector;
import io.jenkins.docker.connector.DockerComputerJNLPConnector;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.bldToString;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.endToString;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.fixEmpty;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.startToString;

public class DockerTemplate implements Describable<DockerTemplate> {
    /**
     * The default timeout in seconds ({@value #DEFAULT_STOP_TIMEOUT}s) to wait during container shutdown
     * until it will be forcefully terminated.
     */
    public static final int DEFAULT_STOP_TIMEOUT = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerTemplate.class.getName());

    private static final UniqueIdGenerator ID_GENERATOR = new UniqueIdGenerator(36);

    /** Default value for {@link #getName()} if {@link #name} is null. */
    private static final String DEFAULT_NAME = "docker";

    private int configVersion = 2;

    private final @CheckForNull String labelString;

    private @Nonnull DockerComputerConnector connector;

    /** @deprecated Use {@link #connector} instead. */
    @Deprecated
    private transient DockerComputerLauncher launcher;

    public @CheckForNull String remoteFs;

    public final int instanceCap;

    private Node.Mode mode = Node.Mode.NORMAL;

    // for backward compatibility reason can't declare this attribute as type DockerOnceRetentionStrategy
    private RetentionStrategy retentionStrategy = new DockerOnceRetentionStrategy(10);

    private @Nonnull DockerTemplateBase dockerTemplateBase;

    private boolean removeVolumes;

    private int stopTimeout = DEFAULT_STOP_TIMEOUT;

    private @Nonnull transient /*almost final*/ Set<LabelAtom> labelSet;

    private @CheckForNull DockerImagePullStrategy pullStrategy;

    private int pullTimeout;

    private @CheckForNull List<? extends NodeProperty<?>> nodeProperties;

    private @CheckForNull DockerDisabled disabled;

    private @CheckForNull String name;

    /**
     * Default constructor; give an unusable instance.
     * 
     * @deprecated This gives an empty image name, which isn't valid.
     */
    @Deprecated
    public DockerTemplate() {
        this(new DockerTemplateBase(""), new DockerComputerJNLPConnector(), null, "1");
    }

    public DockerTemplate(@Nonnull DockerTemplateBase dockerTemplateBase,
                          @Nonnull DockerComputerConnector connector,
                          String labelString,
                          String remoteFs,
                          String instanceCapStr) {
        this(dockerTemplateBase, connector, labelString, instanceCapStr);
        setRemoteFs(remoteFs);
    }


    @DataBoundConstructor
    public DockerTemplate(@Nonnull DockerTemplateBase dockerTemplateBase,
                          @Nonnull DockerComputerConnector connector,
                          String labelString,
                          String instanceCapStr
    ) {
        this.dockerTemplateBase = dockerTemplateBase;
        this.connector = connector;
        this.labelString = Util.fixEmpty(labelString);

        if (Strings.isNullOrEmpty(instanceCapStr)) {
            this.instanceCap = Integer.MAX_VALUE;
        } else {
            this.instanceCap = Integer.parseInt(instanceCapStr);
        }

        labelSet = Label.parse(labelString);
    }

    // -- DockerTemplateBase mixin

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

    public Long getCpuPeriod() {
        return dockerTemplateBase.getCpuPeriod();
    }

    public Long getCpuQuota() {
        return dockerTemplateBase.getCpuQuota();
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

    @CheckForNull
    public List<String> getSecurityOpts() {
        return dockerTemplateBase.getSecurityOpts();
    }

    public String getSecurityOptsString() {
        return dockerTemplateBase.getSecurityOptsString();
    }

    @CheckForNull
    public List<String> getCapabilitiesToAdd() {
        return dockerTemplateBase.getCapabilitiesToAdd();
    }

    public String getCapabilitiesToAddString() {
        return dockerTemplateBase.getCapabilitiesToAddString();
    }

    @CheckForNull
    public List<String> getCapabilitiesToDrop() {
        return dockerTemplateBase.getCapabilitiesToDrop();
    }

    public String getCapabilitiesToDropString() {
        return dockerTemplateBase.getCapabilitiesToDropString();
    }

    public DockerRegistryEndpoint getRegistry() {
        return dockerTemplateBase.getRegistry();
    }

    public CreateContainerCmd fillContainerConfig(CreateContainerCmd containerConfig) {
        final CreateContainerCmd result = dockerTemplateBase.fillContainerConfig(containerConfig);
        final String templateName = getName();
        final Map<String, String> existingLabels = containerConfig.getLabels();
        final Map<String, String> labels;
        if (existingLabels == null) {
            labels = new HashMap<>();
            containerConfig.withLabels(labels);
        } else {
            labels = existingLabels;
        }
        labels.put(DockerContainerLabelKeys.REMOVE_VOLUMES, Boolean.toString(isRemoveVolumes()));
        labels.put(DockerContainerLabelKeys.TEMPLATE_NAME, templateName);
        containerConfig.withLabels(labels);
        final String nodeName = calcUnusedNodeName(templateName);
        setNodeNameInContainerConfig(result, nodeName);
        return result;
    }

    @Restricted(NoExternalUse.class) // public for tests only
    public static void setNodeNameInContainerConfig(CreateContainerCmd containerConfig, String nodeName) {
        final Map<String, String> existingLabels = containerConfig.getLabels();
        final Map<String, String> labels;
        if (existingLabels == null) {
            labels = new HashMap<>();
            containerConfig.withLabels(labels);
        } else {
            labels = existingLabels;
        }
        labels.put(DockerContainerLabelKeys.NODE_NAME, nodeName);
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
     * @throws IllegalStateException if no label was found.
     */
    @Nonnull
    public static String getNodeNameFromContainerConfig(CreateContainerCmd containerConfig) {
        final Map<String, String> labels = containerConfig.getLabels();
        final String result = labels == null ? null : labels.get(DockerContainerLabelKeys.NODE_NAME);
        if (result == null) {
            throw new IllegalStateException("Internal Error: containerConfig does not have a label "
                    + DockerContainerLabelKeys.NODE_NAME + " set");
        }
        return result;
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

    public int getStopTimeout() {
        return stopTimeout;
    }

    @DataBoundSetter
    public void setStopTimeout(int timeout) {
        this.stopTimeout = timeout;
    }

    @CheckForNull
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

    @Nonnull
    public DockerComputerConnector getConnector() {
        return connector;
    }

    @CheckForNull
    public String getRemoteFs() {
        return Util.fixEmpty(remoteFs);
    }

    @DataBoundSetter
    public void setRemoteFs(String remoteFs) {
        this.remoteFs = Util.fixEmpty(remoteFs);
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

    @Nonnull
    public Set<LabelAtom> getLabelSet() {
        return labelSet;
    }

    @Nonnull
    public DockerImagePullStrategy getPullStrategy() {
        return pullStrategy != null ? pullStrategy : DockerImagePullStrategy.PULL_LATEST;
    }

    @DataBoundSetter
    public void setPullStrategy(DockerImagePullStrategy pullStrategy) {
        if (pullStrategy == DockerImagePullStrategy.PULL_LATEST) {
            this.pullStrategy = null;
        } else {
            this.pullStrategy = pullStrategy;
        }
    }

    public int getPullTimeout() {
        return pullTimeout;
    }

    @DataBoundSetter
    public void setPullTimeout(int pullTimeout) {
        this.pullTimeout = pullTimeout;
    }

    @CheckForNull
    public List<? extends NodeProperty<?>> getNodeProperties() {
        final List<? extends NodeProperty<?>> nullOrNotEmpty = fixEmpty(nodeProperties);
        if (nullOrNotEmpty == null) {
            return null;
        }
        return Collections.unmodifiableList(nullOrNotEmpty);
    }

    @DataBoundSetter
    public void setNodeProperties(List<? extends NodeProperty<?>> nodeProperties) {
        this.nodeProperties = fixEmpty(nodeProperties);
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

    @Nonnull
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
    }

    /**
     * Initializes data structure that we don't persist.
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "This method's job is to ensure that things aren't null where they shouldn't be.")
    protected Object readResolve() {
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

            if (connector == null ) {
                if (launcher != null) {
                    connector = launcher.convertToConnector();
                } else {
                    connector = new DockerComputerJNLPConnector();
                }
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
        template.setPullStrategy(getPullStrategy());
        template.setRemoveVolumes(removeVolumes);
        template.setStopTimeout(stopTimeout);
        template.setRetentionStrategy((DockerOnceRetentionStrategy) retentionStrategy);
        template.setNodeProperties(makeCopyOfList(getNodeProperties()));
        return template;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final DockerTemplate other = (DockerTemplate) obj;
        // Maintenance note: This should include all non-transient fields.
        // Fields that are "usually unique" should go first.
        // Primitive fields should be tested before objects.
        // Computationally-expensive fields get tested last.
        // Note: If modifying this code, remember to update hashCode() and toString()
        return Objects.equals(name, other.name)
                && Objects.equals(labelString, other.labelString)
                && configVersion == other.configVersion
                && instanceCap == other.instanceCap
                && mode == other.mode
                && pullTimeout == other.pullTimeout
                && removeVolumes == other.removeVolumes
                && stopTimeout == other.stopTimeout
                && Objects.equals(connector, other.connector)
                && Objects.equals(remoteFs, other.remoteFs)
                && Objects.equals(dockerTemplateBase, other.dockerTemplateBase)
                && Objects.equals(retentionStrategy, other.retentionStrategy)
                && Objects.equals(getNodeProperties(), other.getNodeProperties())
                && getPullStrategy() == other.getPullStrategy()
                && Objects.equals(getDisabled(), other.getDisabled());
    }

    @Override
    public int hashCode() {
        // Maintenance node: This should list all the fields from the equals method,
        // preferably in the same order.
        // Note: If modifying this code, remember to update equals() and toString()
        return Objects.hash(
                name,
                labelString,
                configVersion,
                instanceCap,
                mode,
                pullTimeout,
                removeVolumes,
                stopTimeout,
                connector,
                remoteFs,
                dockerTemplateBase,
                retentionStrategy,
                getNodeProperties(),
                getPullStrategy(),
                getDisabled());
    }

    @Override
    public String toString() {
        // Maintenance node: This should list all the data we use in the equals()
        // method, but in the order the fields are declared in the class.
        // Note: If modifying this code, remember to update hashCode() and toString()
        final StringBuilder sb = startToString(this);
        bldToString(sb, "configVersion", configVersion);
        bldToString(sb, "labelString", labelString);
        bldToString(sb, "connector", connector);
        bldToString(sb, "remoteFs", remoteFs);
        bldToString(sb, "instanceCap", instanceCap);
        bldToString(sb, "mode", mode);
        bldToString(sb, "retentionStrategy", retentionStrategy);
        bldToString(sb, "dockerTemplateBase", dockerTemplateBase);
        bldToString(sb, "removeVolumes", removeVolumes);
        bldToString(sb, "stopTimeout", stopTimeout);
        bldToString(sb, "pullStrategy", getPullStrategy());
        bldToString(sb, "pullTimeout", pullTimeout);
        bldToString(sb, "nodeProperties", getNodeProperties());
        bldToString(sb, "disabled", getDisabled());
        bldToString(sb, "name", name);
        endToString(sb);
        return sb.toString();
    }

    public String getShortDescription() {
        return "DockerTemplate{image=" + dockerTemplateBase.getImage() + "}";
    }

    @Override
    public Descriptor<DockerTemplate> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }

    @Nonnull
    InspectImageResponse pullImage(DockerAPI api, TaskListener listener) throws IOException, InterruptedException {
        final String image = getFullImageId();

        final boolean shouldPullImage;
        try(final DockerClient client = api.getClient()) {
            shouldPullImage = getPullStrategy().shouldPullImage(client, image);
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
                        super.onNext(item);
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
            final String effectiveRemoteFsDir = getEffectiveRemoteFs(image);
            try(final DockerClient client = api.getClient()) {
                return doProvisionNode(api, client, effectiveRemoteFsDir, listener);
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

    @Nonnull
    private String getEffectiveRemoteFs(final InspectImageResponse image) {
        final String remoteFsOrNull = getRemoteFs();
        if (remoteFsOrNull != null) {
            return remoteFsOrNull;
        }
        final ContainerConfig containerConfig = image.getContainerConfig();
        final String containerWorkingDir = containerConfig == null ? null : containerConfig.getWorkingDir();
        if (!StringUtils.isBlank(containerWorkingDir)) {
            return containerWorkingDir;
        }
        return "/";
    }

    private DockerTransientNode doProvisionNode(final DockerAPI api, final DockerClient client,
            final String effectiveRemoteFsDir, final TaskListener listener)
            throws IOException, Descriptor.FormException, InterruptedException {
        final String ourImage = getImage(); // can't be null
        LOGGER.info("Trying to run container for image \"{}\"", ourImage);
        final DockerComputerConnector ourConnector = getConnector();

        final CreateContainerCmd cmd = client.createContainerCmd(ourImage);
        fillContainerConfig(cmd);

        ourConnector.beforeContainerCreated(api, effectiveRemoteFsDir, cmd);

        final String nodeName = getNodeNameFromContainerConfig(cmd);
        LOGGER.info("Trying to run container for node {} from image: {}", nodeName, ourImage);
        boolean finallyRemoveTheContainer = true;
        final String containerId = cmd.exec().getId();
        // if we get this far, we have created the container so,
        // if we fail to return the node, we need to ensure it's cleaned up.
        LOGGER.info("Started container ID {} for node {} from image: {}", containerId, nodeName, ourImage);

        try {
            ourConnector.beforeContainerStarted(api, effectiveRemoteFsDir, containerId);
            client.startContainerCmd(containerId).exec();
            ourConnector.afterContainerStarted(api, effectiveRemoteFsDir, containerId);

            final ComputerLauncher nodeLauncher = ourConnector.createLauncher(api, containerId, effectiveRemoteFsDir, listener);
            final DockerTransientNode node = new DockerTransientNode(nodeName, containerId, effectiveRemoteFsDir, nodeLauncher);
            node.setNodeDescription("Docker Agent [" + ourImage + " on "+ api.getDockerHost().getUri() + " ID " + containerId + "]");
            node.setMode(getMode());
            node.setLabelString(getLabelString());
            node.setRetentionStrategy(getRetentionStrategy());
            robustlySetNodeProperties(node, makeCopyOfList(getNodeProperties()));
            node.setRemoveVolumes(isRemoveVolumes());
            node.setStopTimeout(getStopTimeout());
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

    private static <T> List<T> makeCopyOfList(List<? extends T> listOrNull) {
        if (listOrNull == null) {
            return null;
        }
        final List<T> copyList = new ArrayList<>(listOrNull.size());
        for( final T originalElement : listOrNull) {
            final T copyOfElement = makeCopy(originalElement);
            copyList.add(copyOfElement);
        }
        return copyList;
    }

    private static <T> T makeCopy(final T original) {
        final String xml = Jenkins.XSTREAM.toXML(original);
        final Object copy = Jenkins.XSTREAM.fromXML(xml);
        return (T) copy;
    }

    /**
     * Returns a node name for a new node that doesn't clash with any we've
     * currently got.
     * 
     * @param templateName
     *            The template's {@link #getName()}. This is used as a prefix for
     *            the node name.
     * @return A unique unused node name suitable for use as an agent name for a
     *         agent created from this template.
     */
    private static String calcUnusedNodeName(final String templateName) {
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        while (true) {
            // make a should-be-unique ID
            final String uniqueId = ID_GENERATOR.getUniqueId();
            final String nodeName = templateName + '-' + uniqueId;
            // now check it doesn't collide with any existing agents that might
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

    @Extension
    public static final class DescriptorImpl extends Descriptor<DockerTemplate> {
        /**
         * Get a list of all {@link NodePropertyDescriptor}s we can use to define DockerSlave NodeProperties.
         */
        @SuppressWarnings("cast")
        public List<NodePropertyDescriptor> getNodePropertiesDescriptors() {
            // Copy/paste hudson.model.Slave.SlaveDescriptor.nodePropertyDescriptors marked as @Restricted for reasons I don't get
            List<NodePropertyDescriptor> result = new ArrayList<>();
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

        public FormValidation doCheckStopTimeout(@QueryParameter String value) {
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
