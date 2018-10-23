package com.nirima.jenkins.plugins.docker;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Version;
import com.google.common.base.Throwables;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import org.apache.commons.codec.binary.Base64;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryToken;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.firstOrNull;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.withId;

/**
 * Docker Cloud configuration. Contains connection configuration,
 * {@link DockerTemplate} contains configuration for running docker image.
 *
 * @author magnayn
 */
public class DockerCloud extends Cloud {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerCloud.class);

    /**
     * Default value for {@link #getEffectiveErrorDurationInMilliseconds()}
     * used when {@link #errorDuration} is null.
     */
    @Restricted(NoExternalUse.class)
    private static final int ERROR_DURATION_DEFAULT_SECONDS = 300; // 5min

    @CheckForNull
    private List<DockerTemplate> templates;
    @CheckForNull
    private transient Map<Long, DockerTemplate> jobTemplates;

    @Deprecated
    private transient DockerServerEndpoint dockerHost;
    @Deprecated
    private transient String serverUrl;
    @Deprecated
    public  transient String credentialsId;
    @Deprecated
    private transient int connectTimeout;
    @Deprecated
    private transient int readTimeout;
    @Deprecated
    private transient String version;
    @Deprecated
    private transient String dockerHostname;

    private DockerAPI dockerApi;

    /**
     * Total max allowed number of containers
     */
    private int containerCap = 100;

    /**
     * Is this cloud running Joyent Triton?
     */
    private transient Boolean _isTriton;

    /**
     * Track the count per image name for images currently being
     * provisioned, but not necessarily reported yet by docker.
     */
    @Restricted(NoExternalUse.class)
    static final Map<String, Map<String, Integer>> CONTAINERS_IN_PROGRESS = new HashMap<>();

    /**
     * Indicate if docker host used to run container is exposed inside container as DOCKER_HOST environment variable
     */
    private boolean exposeDockerHost;

    private @CheckForNull DockerDisabled disabled;

    /** Length of time, in seconds, that {@link #disabled} should auto-disable for if we encounter an error. */
    private @CheckForNull Integer errorDuration;

    @DataBoundConstructor
    public DockerCloud(String name,
                       DockerAPI dockerApi,
                       List<DockerTemplate> templates) {

        super(name);
        this.dockerApi = dockerApi;
        this.templates = templates;
    }

    @Deprecated
    public DockerCloud(String name,
                       List<DockerTemplate> templates,
                       DockerServerEndpoint dockerHost,
                       int containerCap,
                       int connectTimeout,
                       int readTimeout,
                       String version,
                       String dockerHostname) {
        this(name, new DockerAPI(dockerHost, connectTimeout, readTimeout, version, dockerHostname), templates);
        setContainerCap(containerCap);
    }

    @Deprecated
    public DockerCloud(String name,
                       List<DockerTemplate> templates,
                       String serverUrl,
                       int containerCap,
                       int connectTimeout,
                       int readTimeout,
                       String credentialsId,
                       String version,
                       String dockerHostname) {
        this(name, templates, new DockerServerEndpoint(serverUrl, credentialsId), containerCap, connectTimeout, readTimeout, version, dockerHostname);
    }

    @Deprecated
    public DockerCloud(String name,
                       List<DockerTemplate> templates,
                       String serverUrl,
                       String containerCapStr,
                       int connectTimeout,
                       int readTimeout,
                       String credentialsId,
                       String version,
                       String dockerHostname) {
        this(name, templates, serverUrl,
                containerCapStr.equals("") ? Integer.MAX_VALUE : Integer.parseInt(containerCapStr),
                connectTimeout, readTimeout, credentialsId, version, dockerHostname);
    }


    public DockerAPI getDockerApi() {
        return dockerApi;
    }

    @Deprecated
    public int getConnectTimeout() {
        return dockerApi.getConnectTimeout();
    }


    @Deprecated
    public DockerServerEndpoint getDockerHost() {
        return dockerApi.getDockerHost();
    }

    @Deprecated
    public String getServerUrl() {
        return getDockerHost().getUri();
    }

    @Deprecated
    public String getDockerHostname() {
        return dockerApi.getHostname();
    }

    /**
     * @deprecated use {@link #getContainerCap()}
     */
    @Deprecated
    public String getContainerCapStr() {
        if (containerCap == Integer.MAX_VALUE) {
            return "";
        }
        return String.valueOf(containerCap);
    }

    public int getContainerCap() {
        return containerCap;
    }

    @DataBoundSetter
    public void setContainerCap(int containerCap) {
        this.containerCap = containerCap;
    }

    protected String sanitizeUrl(String url) {
        if( url == null )
            return null;
        return url.replace("http:", "tcp:")
                  .replace("https:","tcp:");
    }

    /**
     * Connects to Docker. <em>NOTE:</em> This should not be used for any
     * long-running operations as the client it returns is not protected from
     * closure.
     * 
     * @deprecated Use {@link #getDockerApi()} and then
     *             {@link DockerAPI#getClient()} to get the client, followed by
     *             a call to {@link DockerClient#close()}.
     * @return Docker client.
     */
    @Deprecated
    public DockerClient getClient() {
        try {
            // get a client
            final DockerClient client = dockerApi.getClient();
            // now release it so it'll be cached for a duration but will be
            // reaped when the cache duration expires without anyone having to
            // call anything else to make this happen.
            client.close();
            return client;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Decrease the count of slaves being "provisioned".
     */
    void decrementContainersInProgress(DockerTemplate template) {
        adjustContainersInProgress(this, template, -1);
    }

    /**
     * Increase the count of slaves being "provisioned".
     */
    void incrementContainersInProgress(DockerTemplate template) {
        adjustContainersInProgress(this, template, +1);
    }

    private static void adjustContainersInProgress(DockerCloud cloud, DockerTemplate template, int adjustment) {
        final String cloudId = cloud.name;
        final String templateId = template.getImage();
        synchronized (CONTAINERS_IN_PROGRESS) {
            Map<String, Integer> mapForThisCloud = CONTAINERS_IN_PROGRESS.get(cloudId);
            if (mapForThisCloud == null) {
                mapForThisCloud = new HashMap<>();
                CONTAINERS_IN_PROGRESS.put(cloudId, mapForThisCloud);
            }
            final Integer oldValue = mapForThisCloud.get(templateId);
            final int oldNumber = oldValue == null ? 0 : oldValue;
            final int newNumber = oldNumber + adjustment;
            if (newNumber != 0) {
                mapForThisCloud.put(templateId, newNumber);
            } else {
                mapForThisCloud.remove(templateId);
                if (mapForThisCloud.isEmpty()) {
                    CONTAINERS_IN_PROGRESS.remove(cloudId);
                }
            }
        }
    }

    int countContainersInProgress(DockerTemplate template) {
        final String cloudId = super.name;
        final String templateId = template.getImage();
        synchronized (CONTAINERS_IN_PROGRESS) {
            final Map<String, Integer> allInProgressOrNull = CONTAINERS_IN_PROGRESS.get(cloudId);
            final Integer templateInProgressOrNull = allInProgressOrNull == null
                    ? null
                    : allInProgressOrNull.get(templateId);
            final int templateInProgress = templateInProgressOrNull == null ? 0 : templateInProgressOrNull;
            return templateInProgress;
        }
    }

    int countContainersInProgress() {
        final String cloudId = this.name;
        synchronized (CONTAINERS_IN_PROGRESS) {
            final Map<String, Integer> allInProgressOrNull = CONTAINERS_IN_PROGRESS.get(cloudId);
            int totalInProgressForCloud = 0;
            if (allInProgressOrNull != null) {
                for (int count : allInProgressOrNull.values()) {
                    totalInProgressForCloud += count;
                }
            }
            return totalInProgressForCloud;
        }
    }

    @Override
    public synchronized Collection<NodeProvisioner.PlannedNode> provision(final Label label, final int numberOfExecutorsRequired) {
        if( getDisabled().isDisabled() ) {
            return Collections.emptyList();
        }
        try {
            LOGGER.info("Asked to provision {} slave(s) for: {}", numberOfExecutorsRequired, label);

            final List<NodeProvisioner.PlannedNode> r = new ArrayList<>();
            final List<DockerTemplate> templates = getTemplates(label);
            int remainingWorkload = numberOfExecutorsRequired;

            // Take account of the executors that will result from the containers which we
            // are already committed to starting but which have yet to be given to Jenkins
            for ( final DockerTemplate t : templates ) {
                final int numberOfContainersInProgress = countContainersInProgress(t);
                final int numberOfExecutorsInProgress = t.getNumExecutors() * numberOfContainersInProgress;
                remainingWorkload -= numberOfExecutorsInProgress;
            }
            if ( remainingWorkload != numberOfExecutorsRequired ) {
                final int numberOfExecutorsInProgress = numberOfExecutorsRequired - remainingWorkload;
                if( remainingWorkload<=0 ) {
                    LOGGER.info("Not provisioning additional slaves for {}; we have {} executors being started already", label, numberOfExecutorsInProgress);
                } else {
                    LOGGER.info("Only provisioning {} slaves for {}; we have {} executors being started already", remainingWorkload, label, numberOfExecutorsInProgress);
                }
            }

            while (remainingWorkload > 0 && !templates.isEmpty()) {
                final DockerTemplate t = templates.get(0); // get first

                final boolean thereIsCapacityToProvisionFromThisTemplate = canAddProvisionedSlave(t);
                if (!thereIsCapacityToProvisionFromThisTemplate) {
                    templates.remove(t);
                    continue;
                }
                LOGGER.info("Will provision '{}', for label: '{}', in cloud: '{}'",
                        t.getImage(), label, getDisplayName());

                final CompletableFuture<Node> plannedNode = new CompletableFuture<>();
                r.add(new NodeProvisioner.PlannedNode(t.getDisplayName(), plannedNode, t.getNumExecutors()));

                final Runnable taskToCreateNewSlave = new Runnable() {
                    @Override
                    public void run() {
                        DockerTransientNode slave = null;
                        try {
                            // TODO where can we log provisioning progress ?
                            final DockerAPI api = DockerCloud.this.getDockerApi();
                            slave = t.provisionNode(api, TaskListener.NULL);
                            slave.setCloudId(DockerCloud.this.name);
                            plannedNode.complete(slave);

                            // On provisioning completion, let's trigger NodeProvisioner
                            robustlyAddNodeToJenkins(slave);

                        } catch (Exception ex) {
                            LOGGER.error("Error in provisioning; template='{}' for cloud='{}'",
                                    t, getDisplayName(), ex);
                            plannedNode.completeExceptionally(ex);
                            if (slave != null) {
                                slave.terminate(LOGGER);
                            }
                            throw Throwables.propagate(ex);
                        } finally {
                            decrementContainersInProgress(t);
                        }
                    }
                };
                boolean taskToCreateSlaveHasBeenQueuedSoItWillDoTheDecrement = false;
                incrementContainersInProgress(t);
                try {
                    Computer.threadPoolForRemoting.submit(taskToCreateNewSlave);
                    taskToCreateSlaveHasBeenQueuedSoItWillDoTheDecrement = true;
                } finally {
                    if (!taskToCreateSlaveHasBeenQueuedSoItWillDoTheDecrement) {
                        decrementContainersInProgress(t);
                    }
                }

                remainingWorkload -= t.getNumExecutors();
            }

            return r;
        } catch (Exception e) {
            LOGGER.error("Exception while provisioning for label: '{}', cloud='{}'", label, getDisplayName(), e);
            final long milliseconds = getEffectiveErrorDurationInMilliseconds();
            if (milliseconds > 0L) {
                final DockerDisabled reasonForDisablement = getDisabled();
                reasonForDisablement.disableBySystem("Cloud provisioning failure", milliseconds, e);
                setDisabled(reasonForDisablement);
            }
            return Collections.emptyList();
        }
    }

    /**
     * Workaround for Jenkins core issue in Nodes.java. There's a line there
     * saying "<i>TODO there is a theoretical race whereby the node instance is
     * updated/removed after lock release</i>". When we're busy adding nodes
     * this is not merely "theoretical"!
     * 
     * @see <a href=
     *      "https://github.com/jenkinsci/jenkins/blob/d2276c3c9b16fd46a3912ab8d58c418e67d8ce3e/core/src/main/java/jenkins/model/Nodes.java#L141">
     *      Nodes.java</a>
     * 
     * @param slave
     *            The slave to be added to Jenkins
     * @throws IOException
     *             if it all failed horribly every time we tried.
     */
    private static void robustlyAddNodeToJenkins(DockerTransientNode slave) throws IOException {
        // don't retry getInstance - fail immediately if that fails.
        final Jenkins jenkins = Jenkins.getInstance();
        final int maxAttempts = 10;
        for (int attempt = 1;; attempt++) {
            try {
                // addNode can fail at random due to a race condition.
                jenkins.addNode(slave);
                return;
            } catch (IOException | RuntimeException ex) {
                if (attempt > maxAttempts) {
                    throw ex;
                }
                final long delayInMilliseconds = 10L * attempt;
                try {
                    Thread.sleep(delayInMilliseconds);
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
        }
    }

    /**
     * for publishers/builders. Simply runs container in docker cloud
     */
    public static String runContainer(DockerTemplateBase dockerTemplateBase,
                                      DockerClient dockerClient) {
        CreateContainerCmd containerConfig = dockerClient.createContainerCmd(dockerTemplateBase.getImage());

        dockerTemplateBase.fillContainerConfig(containerConfig);

        // create
        CreateContainerResponse response = containerConfig.exec();
        String containerId = response.getId();

        // start
        StartContainerCmd startCommand = dockerClient.startContainerCmd(containerId);
        startCommand.exec();

        return containerId;
    }

    @Override
    public boolean canProvision(Label label) {
        if( getDisabled().isDisabled() ) {
            return false;
        }
        return getTemplate(label) != null;
    }

    @CheckForNull
    public DockerTemplate getTemplate(String template) {
        for (DockerTemplate t : getTemplates()) {
            if (t.getImage().equals(template)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Gets first {@link DockerTemplate} that has the matching {@link Label}.
     */
    @CheckForNull
    public DockerTemplate getTemplate(Label label) {
        List<DockerTemplate> templates = getTemplates(label);
        if (!templates.isEmpty()) {
            return templates.get(0);
        }

        return null;
    }

    /**
     * Add a new template to the cloud
     */
    public synchronized void addTemplate(DockerTemplate t) {
        if ( templates == null ) {
            templates = new ArrayList<>();
        }
        templates.add(t);
    }

    /**
     * Adds a template which is temporary provided and bound to a specific job.
     * 
     * @param jobId Unique id (per master) of the job to which the template is bound.
     * @param template The template to bound to a specific job.
     */
    public synchronized void addJobTemplate(long jobId, DockerTemplate template) {
        getJobTemplates().put(jobId, template);
    }

    /**
     * Removes a template which is bound to a specific job.
     * 
     * @param jobId Id of the job.
     */
    public synchronized void removeJobTemplate(long jobId) {
        if (getJobTemplates().remove(jobId) == null) {
            LOGGER.warn("Couldn't remove template for job with id: {}", jobId);
        }
    }

    public List<DockerTemplate> getTemplates() {
        return templates == null ? Collections.EMPTY_LIST : templates;
    }

    /**
     * Multiple amis can have the same label.
     *
     * @return Templates matched to requested label assuming slave Mode
     */
    public List<DockerTemplate> getTemplates(Label label) {
        final List<DockerTemplate> dockerTemplates = new ArrayList<>();

        for (DockerTemplate t : getTemplates()) {
            if ( t.getDisabled().isDisabled() ) {
                continue; // pretend it doesn't exist
            }
            if (label == null && t.getMode() == Node.Mode.NORMAL) {
                dockerTemplates.add(t);
            }

            if (label != null && label.matches(t.getLabelSet())) {
                dockerTemplates.add(t);
            }
        }

        // add temporary templates matched to requested label
        for (DockerTemplate template : getJobTemplates().values()) {
            if (label != null && label.matches(template.getLabelSet())) {
                dockerTemplates.add(template);
            }
        }

        return dockerTemplates;
    }

    /**
     * Private method to ensure that the map of job specific templates is initialized.
     * 
     * @return The map of job specific templates.
     */
    private Map<Long, DockerTemplate> getJobTemplates() {
        if (jobTemplates == null) {
            jobTemplates = new HashMap<>();
        }

        return jobTemplates;
    }

    /**
     * Remove Docker template
     */
    public synchronized void removeTemplate(DockerTemplate t) {
        if ( templates != null ) {
            templates.remove(t);
        }
    }

    /**
     * Counts the number of instances currently running in Docker that are using
     * the specified image.
     * <p>
     * <b>WARNING:</b> This method can be slow so it should be called sparingly.
     *
     * @param imageName
     *            If null, then all instances belonging to this Jenkins instance
     *            are counted. Otherwise, only those started with the specified
     *            image are counted.
     */
    public int countContainersInDocker(final String imageName) throws Exception {
        final Map<String, String> labelFilter = new HashMap<>();
        labelFilter.put(DockerContainerLabelKeys.JENKINS_INSTANCE_ID,
                DockerTemplateBase.getJenkinsInstanceIdForContainerLabel());
        if (imageName != null) {
            labelFilter.put(DockerContainerLabelKeys.CONTAINER_IMAGE, imageName);
        }
        final List<?> containers;
        try(final DockerClient client = dockerApi.getClient()) {
            containers = client.listContainersCmd().withLabelFilter(labelFilter).exec();
        }
        final int count = containers.size();
        return count;
    }

    /**
     * Check not too many already running.
     */
    private boolean canAddProvisionedSlave(DockerTemplate t) throws Exception {
        final String templateImage = t.getImage();
        final int templateContainerCap = t.instanceCap;
        final int cloudContainerCap = getContainerCap();

        final boolean haveCloudContainerCap = cloudContainerCap > 0 && cloudContainerCap != Integer.MAX_VALUE;
        final boolean haveTemplateContainerCap = templateContainerCap > 0 && templateContainerCap != Integer.MAX_VALUE;
        final int estimatedTotalSlaves;
        if (haveCloudContainerCap) {
            final int totalContainersInCloud = countContainersInDocker(null);
            final int containersInProgress = countContainersInProgress();
            estimatedTotalSlaves = totalContainersInCloud + containersInProgress;
            if (estimatedTotalSlaves >= cloudContainerCap) {
                LOGGER.info("Not Provisioning '{}'; Cloud '{}' full with '{}' container(s)", templateImage, name, cloudContainerCap);
                return false;      // maxed out
            }
        } else {
            estimatedTotalSlaves = -1;
        }
        final int estimatedTemplateSlaves;
        if (haveTemplateContainerCap) {
            final int totalContainersOfThisTemplateInCloud = countContainersInDocker(templateImage);
            final int containersInProgress = countContainersInProgress(t);
            estimatedTemplateSlaves = totalContainersOfThisTemplateInCloud + containersInProgress;
            if (estimatedTemplateSlaves >= templateContainerCap) {
                LOGGER.info("Not Provisioning '{}'. Template instance limit of '{}' reached on cloud '{}'", templateImage, templateContainerCap, name);
                return false;      // maxed out
            }
        } else {
            estimatedTemplateSlaves = -1;
        }

        if (haveCloudContainerCap) {
            if (haveTemplateContainerCap) {
                LOGGER.info("Provisioning '{}' number {} (of {}) on '{}'; Total containers: {} (of {})",
                        templateImage, estimatedTemplateSlaves + 1, templateContainerCap, name,
                        estimatedTotalSlaves, cloudContainerCap);
            } else {
                LOGGER.info("Provisioning '{}' on '{}'; Total containers: {} (of {})", templateImage, name,
                        estimatedTotalSlaves, cloudContainerCap);
            }
        } else {
            if (haveTemplateContainerCap) {
                LOGGER.info("Provisioning '{}' number {} (of {}) on '{}'", templateImage,
                        estimatedTemplateSlaves + 1, templateContainerCap, name);
            } else {
                LOGGER.info("Provisioning '{}' on '{}'", templateImage, name);
            }
        }
        return true;
    }

    public static DockerCloud getCloudByName(String name) {
        return (DockerCloud) Jenkins.getInstance().getCloud(name);
    }

    public Object readResolve() {
        //Xstream is not calling readResolve() for nested Describable's
        for (DockerTemplate template : getTemplates()) {
            template.readResolve();
        }

        if (dockerHost == null && serverUrl != null) {
            serverUrl = sanitizeUrl(serverUrl);
            // migration to docker-commons
            dockerHost = new DockerServerEndpoint(serverUrl, credentialsId);
        }
        if (dockerApi == null) {
            dockerApi = new DockerAPI(dockerHost, connectTimeout, readTimeout, version, dockerHostname);
        }

        return this;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DockerCloud{");
        sb.append("name=").append(name);
        sb.append(", dockerApi=").append(dockerApi);
        sb.append(", containerCap=").append(containerCap);
        sb.append(", exposeDockerHost=").append(exposeDockerHost);
        sb.append(", disabled=").append(disabled);
        sb.append(", templates='").append(templates).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((dockerApi == null) ? 0 : dockerApi.hashCode());
        result = prime * result + containerCap;
        result = prime * result + ((templates == null) ? 0 : templates.hashCode());
        result = prime * result + (exposeDockerHost ? 1231 : 1237);
        result = prime * result + getDisabled().hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DockerCloud that = (DockerCloud) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (dockerApi != null ? !dockerApi.equals(that.dockerApi) : that.dockerApi != null) return false;
        if (containerCap != that.containerCap) return false;
        if (templates != null ? !templates.equals(that.templates) : that.templates != null) return false;
        if (exposeDockerHost != that.exposeDockerHost)return false;
        if (!getDisabled().equals(that.getDisabled())) return false;
        return true;
    }

    public boolean isTriton() {
        if( _isTriton == null ) {
            final Version remoteVersion;
            try(final DockerClient client = dockerApi.getClient()) {
                remoteVersion = client.versionCmd().exec();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            _isTriton = remoteVersion.getOperatingSystem().equals("solaris");
        }
        return _isTriton;
    }

    public boolean isExposeDockerHost() {
        // if null (i.e migration from previous installation) consider true for backward compatibility
        return exposeDockerHost;
    }

    @DataBoundSetter
    public void setExposeDockerHost(boolean exposeDockerHost) {
        this.exposeDockerHost = exposeDockerHost;
    }

    public DockerDisabled getDisabled() {
        return disabled == null ? new DockerDisabled() : disabled;
    }

    @DataBoundSetter
    public void setDisabled(DockerDisabled disabled) {
        this.disabled = disabled;
    }

    @CheckForNull
    public Integer getErrorDuration() {
        if (errorDuration != null && errorDuration < 0) {
            return null; // negative is the same as unset = use default.
        }
        return errorDuration;
    }

    @DataBoundSetter
    public void setErrorDuration(Integer errorDuration) {
        this.errorDuration = errorDuration;
    }

    /**
     * Calculates the duration (in milliseconds) we should stop for when an
     * error happens. If the user has not configured a duration then the default
     * of {@value #ERROR_DURATION_DEFAULT_SECONDS} seconds will be used.
     * 
     * @return duration, in milliseconds, to be passed to
     *         {@link DockerDisabled#disableBySystem(String, long, Throwable)}.
     */
    @Restricted(NoExternalUse.class)
    long getEffectiveErrorDurationInMilliseconds() {
        final Integer configuredDurationOrNull = getErrorDuration();
        if (configuredDurationOrNull != null) {
            return TimeUnit.SECONDS.toMillis(configuredDurationOrNull);
        }
        return TimeUnit.SECONDS.toMillis(ERROR_DURATION_DEFAULT_SECONDS);
    }

    public static List<DockerCloud> instances() {
        List<DockerCloud> instances = new ArrayList<>();
        for (Cloud cloud : Jenkins.getInstance().clouds) {
            if (cloud instanceof DockerCloud) {
                instances.add((DockerCloud) cloud);
            }

        }
        return instances;
    }

    @Restricted(NoExternalUse.class)
    static DockerCloud findCloudForTemplate(final DockerTemplate template) {
        for (DockerCloud cloud : instances()) {
            if ( cloud.hasTemplate(template) ) {
                return cloud;
            }
        }
        return null;
    }

    private boolean hasTemplate(DockerTemplate template) {
        if (getTemplates().contains(template)) {
            return true;
        }
        if (getJobTemplates().containsValue(template)) {
            return true;
        }
        return false;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        public FormValidation doCheckErrorDuration(@QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.ok("Default = %d", ERROR_DURATION_DEFAULT_SECONDS);
            }
            return FormValidation.validateNonNegativeInteger(value);
        }

        @Override
        public String getDisplayName() {
            return "Docker";
        }
    }


    // unfortunately there's no common interface for Registry related Docker-java commands

    @Restricted(NoExternalUse.class)
    public static void setRegistryAuthentication(PullImageCmd cmd, DockerRegistryEndpoint registry, ItemGroup context) {
        if (registry != null && registry.getCredentialsId() != null) {
            AuthConfig auth = getAuthConfig(registry, context);
            cmd.withAuthConfig(auth);
        }
    }

    @Restricted(NoExternalUse.class)
    public static void setRegistryAuthentication(PushImageCmd cmd, DockerRegistryEndpoint registry, ItemGroup context) {
        if (registry != null && registry.getCredentialsId() != null) {
            AuthConfig auth = getAuthConfig(registry, context);
            cmd.withAuthConfig(auth);
        }
    }

    @Restricted(NoExternalUse.class)
    public static AuthConfig getAuthConfig(DockerRegistryEndpoint registry, ItemGroup context) {
        AuthConfig auth = new AuthConfig();

        // we can't use DockerRegistryEndpoint#getToken as this one do check domainRequirement based on registry URL
        // but in some context (typically, passing registry auth for `docker build`) we just can't guess this one.

        Credentials c = firstOrNull(CredentialsProvider.lookupCredentials(
                IdCredentials.class, context, ACL.SYSTEM, Collections.EMPTY_LIST),
                withId(registry.getCredentialsId()));

        if (c == null) {
            throw new IllegalArgumentException("Invalid Credential ID " + registry.getCredentialsId());
        }

        final DockerRegistryToken t = AuthenticationTokens.convert(DockerRegistryToken.class, c);
        final String token = t.getToken();
        // What docker-commons claim to be a "token" is actually configuration storage
        // see https://github.com/docker/docker-ce/blob/v17.09.0-ce/components/cli/cli/config/configfile/file.go#L214
        // i.e base64 encoded username : password
        final String decode = new String(Base64.decodeBase64(token));
        int i = decode.indexOf(':');
        if (i > 0) {
            String username = decode.substring(0, i);
            auth.withUsername(username);
        }
        auth.withPassword(decode.substring(i+1));
        if (registry.getUrl() != null) {
            auth.withRegistryAddress(registry.getUrl());
        }
        return auth;
    }
}
