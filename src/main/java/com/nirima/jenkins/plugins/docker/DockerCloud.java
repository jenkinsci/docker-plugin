package com.nirima.jenkins.plugins.docker;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.firstOrNull;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.withId;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.bldToString;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.endToString;
import static com.nirima.jenkins.plugins.docker.utils.JenkinsUtils.startToString;

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
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedPlannedNode;
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
    public transient String credentialsId;

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
    public DockerCloud(String name, DockerAPI dockerApi, List<DockerTemplate> templates) {

        super(name);
        this.dockerApi = dockerApi;
        this.templates = templates;
    }

    @Deprecated
    public DockerCloud(
            String name,
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
    public DockerCloud(
            String name,
            List<DockerTemplate> templates,
            String serverUrl,
            int containerCap,
            int connectTimeout,
            int readTimeout,
            String credentialsId,
            String version,
            String dockerHostname) {
        this(
                name,
                templates,
                new DockerServerEndpoint(serverUrl, credentialsId),
                containerCap,
                connectTimeout,
                readTimeout,
                version,
                dockerHostname);
    }

    @Deprecated
    public DockerCloud(
            String name,
            List<DockerTemplate> templates,
            String serverUrl,
            String containerCapStr,
            int connectTimeout,
            int readTimeout,
            String credentialsId,
            String version,
            String dockerHostname) {
        this(
                name,
                templates,
                serverUrl,
                containerCapStr.equals("") ? Integer.MAX_VALUE : Integer.parseInt(containerCapStr),
                connectTimeout,
                readTimeout,
                credentialsId,
                version,
                dockerHostname);
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
     * @return {@link #getContainerCap()} as a {@link String}.
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
        if (url == null) {
            return null;
        }
        return url.replace("http:", "tcp:").replace("https:", "tcp:");
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
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Decrease the count of agents being "provisioned".
     */
    void decrementContainersInProgress(DockerTemplate template) {
        adjustContainersInProgress(this, template, -1);
    }

    /**
     * Increase the count of agents being "provisioned".
     */
    void incrementContainersInProgress(DockerTemplate template) {
        adjustContainersInProgress(this, template, +1);
    }

    private static void adjustContainersInProgress(DockerCloud cloud, DockerTemplate template, int adjustment) {
        final String cloudId = cloud.name;
        final String templateId = getTemplateId(template);
        synchronized (CONTAINERS_IN_PROGRESS) {
            Map<String, Integer> mapForThisCloud =
                    CONTAINERS_IN_PROGRESS.computeIfAbsent(cloudId, unused -> new HashMap<>());
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

    private static String getTemplateId(DockerTemplate template) {
        final String templateId = template.getImage();
        return templateId;
    }

    @Restricted(NoExternalUse.class)
    public int countContainersInProgress(DockerTemplate template) {
        final String cloudId = super.name;
        final String templateId = getTemplateId(template);
        synchronized (CONTAINERS_IN_PROGRESS) {
            final Map<String, Integer> allInProgressOrNull = CONTAINERS_IN_PROGRESS.get(cloudId);
            final Integer templateInProgressOrNull =
                    allInProgressOrNull == null ? null : allInProgressOrNull.get(templateId);
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
    public synchronized Collection<NodeProvisioner.PlannedNode> provision(
            final Label label, final int numberOfExecutorsRequired) {
        if (getDisabled().isDisabled()) {
            return Collections.emptyList();
        }
        try {
            LOGGER.debug("Asked to provision {} agent(s) for: {}", numberOfExecutorsRequired, label);

            final List<NodeProvisioner.PlannedNode> r = new ArrayList<>();
            final List<DockerTemplate> matchingTemplates = getTemplates(label);
            int remainingWorkload = numberOfExecutorsRequired;

            // Take account of the executors that will result from the containers which we
            // are already committed to starting but which have yet to be given to Jenkins
            for (final DockerTemplate t : matchingTemplates) {
                final int numberOfContainersInProgress = countContainersInProgress(t);
                final int numberOfExecutorsInProgress = t.getNumExecutors() * numberOfContainersInProgress;
                remainingWorkload -= numberOfExecutorsInProgress;
            }
            if (remainingWorkload != numberOfExecutorsRequired) {
                final int numberOfExecutorsInProgress = numberOfExecutorsRequired - remainingWorkload;
                if (remainingWorkload <= 0) {
                    LOGGER.debug(
                            "Not provisioning additional agents for {}; we have {} executors being started already",
                            label,
                            numberOfExecutorsInProgress);
                } else {
                    LOGGER.debug(
                            "Only provisioning {} agents for {}; we have {} executors being started already",
                            remainingWorkload,
                            label,
                            numberOfExecutorsInProgress);
                }
            }

            while (remainingWorkload > 0 && !matchingTemplates.isEmpty()) {
                final DockerTemplate t = matchingTemplates.get(0); // get first

                final boolean thereIsCapacityToProvisionFromThisTemplate = canAddProvisionedAgent(t);
                if (!thereIsCapacityToProvisionFromThisTemplate) {
                    matchingTemplates.remove(t);
                    continue;
                }
                LOGGER.info(
                        "Will provision '{}', for label: '{}', in cloud: '{}'", t.getImage(), label, getDisplayName());

                final ProvisioningActivity.Id id = new ProvisioningActivity.Id(
                        DockerCloud.this.name, t.getName() + " (" + t.getImage() + ")", null);
                final CompletableFuture<Node> plannedNode = new CompletableFuture<>();
                r.add(new TrackedPlannedNode(id, t.getNumExecutors(), plannedNode));

                final Runnable taskToCreateNewAgent = new Runnable() {
                    @Override
                    public void run() {
                        DockerTransientNode agent = null;
                        try {
                            // TODO where can we log provisioning progress ?
                            final DockerAPI api = DockerCloud.this.getDockerApi();
                            agent = t.provisionNode(api, TaskListener.NULL);
                            agent.setDockerAPI(api);
                            agent.setCloudId(DockerCloud.this.name);
                            agent.setProvisioningId(id);
                            plannedNode.complete(agent);

                            // On provisioning completion, let's trigger NodeProvisioner
                            agent.robustlyAddToJenkins();

                        } catch (Exception ex) {
                            LOGGER.error(
                                    "Error in provisioning; template='{}' for cloud='{}'", t, getDisplayName(), ex);
                            plannedNode.completeExceptionally(ex);
                            if (agent != null) {
                                agent.terminate(LOGGER);
                            }
                            if (ex instanceof RuntimeException) {
                                throw (RuntimeException) ex;
                            } else if (ex instanceof IOException) {
                                throw new UncheckedIOException((IOException) ex);
                            } else {
                                throw new RuntimeException(ex);
                            }
                        } finally {
                            decrementContainersInProgress(t);
                        }
                    }
                };
                boolean taskToCreateAgentHasBeenQueuedSoItWillDoTheDecrement = false;
                incrementContainersInProgress(t);
                try {
                    Computer.threadPoolForRemoting.submit(taskToCreateNewAgent);
                    taskToCreateAgentHasBeenQueuedSoItWillDoTheDecrement = true;
                } finally {
                    if (!taskToCreateAgentHasBeenQueuedSoItWillDoTheDecrement) {
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

    /*
     * for publishers/builders. Simply runs container in docker cloud
     */
    public static String runContainer(DockerTemplateBase dockerTemplateBase, DockerClient dockerClient) {
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
        if (getDisabled().isDisabled()) {
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
     *
     * @param label The label we're looking to match.
     * @return The first {@link DockerTemplate} that has the matching {@link Label}, or null if not found.
     */
    @CheckForNull
    public DockerTemplate getTemplate(Label label) {
        List<DockerTemplate> matchingTemplates = getTemplates(label);
        if (!matchingTemplates.isEmpty()) {
            return matchingTemplates.get(0);
        }

        return null;
    }

    /**
     * Add a new template to the cloud
     *
     * @param t The template to be added.
     */
    public synchronized void addTemplate(DockerTemplate t) {
        if (templates == null) {
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
        return templates == null ? Collections.emptyList() : templates;
    }

    /**
     * Multiple amis can have the same label.
     *
     * @param label The label to be matched, or null if no label was provided.
     * @return Templates matched to requested label assuming agent Mode
     */
    public List<DockerTemplate> getTemplates(Label label) {
        final List<DockerTemplate> dockerTemplates = new ArrayList<>();

        for (DockerTemplate t : getTemplates()) {
            if (t.getDisabled().isDisabled()) {
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
     *
     * @param t The template to be removed.
     */
    public synchronized void removeTemplate(DockerTemplate t) {
        if (templates != null) {
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
     * @return The number of containers.
     * @throws Exception if anything went wrong.
     */
    public int countContainersInDocker(final String imageName) throws Exception {
        final Map<String, String> labelFilter = new HashMap<>();
        labelFilter.put(
                DockerContainerLabelKeys.JENKINS_INSTANCE_ID,
                DockerTemplateBase.getJenkinsInstanceIdForContainerLabel());
        if (imageName != null) {
            labelFilter.put(DockerContainerLabelKeys.CONTAINER_IMAGE, imageName);
        }
        final List<?> containers;
        try (final DockerClient client = dockerApi.getClient()) {
            containers = client.listContainersCmd().withLabelFilter(labelFilter).exec();
        }
        final int count = containers.size();
        return count;
    }

    /**
     * Check not too many already running.
     */
    private boolean canAddProvisionedAgent(DockerTemplate t) throws Exception {
        final String templateImage = t.getImage();
        final int templateContainerCap = t.instanceCap;
        final int cloudContainerCap = getContainerCap();

        final boolean haveCloudContainerCap = cloudContainerCap > 0 && cloudContainerCap != Integer.MAX_VALUE;
        final boolean haveTemplateContainerCap = templateContainerCap > 0 && templateContainerCap != Integer.MAX_VALUE;
        final int estimatedTotalAgents;
        if (haveCloudContainerCap) {
            final int totalContainersInCloud = countContainersInDocker(null);
            final int containersInProgress = countContainersInProgress();
            estimatedTotalAgents = totalContainersInCloud + containersInProgress;
            if (estimatedTotalAgents >= cloudContainerCap) {
                LOGGER.debug(
                        "Not Provisioning '{}'; Cloud '{}' full with '{}' container(s)",
                        templateImage,
                        name,
                        cloudContainerCap);
                return false; // maxed out
            }
        } else {
            estimatedTotalAgents = -1;
        }
        final int estimatedTemplateAgents;
        if (haveTemplateContainerCap) {
            final int totalContainersOfThisTemplateInCloud = countContainersInDocker(templateImage);
            final int containersInProgress = countContainersInProgress(t);
            estimatedTemplateAgents = totalContainersOfThisTemplateInCloud + containersInProgress;
            if (estimatedTemplateAgents >= templateContainerCap) {
                LOGGER.debug(
                        "Not Provisioning '{}'. Template instance limit of '{}' reached on cloud '{}'",
                        templateImage,
                        templateContainerCap,
                        name);
                return false; // maxed out
            }
        } else {
            estimatedTemplateAgents = -1;
        }

        if (haveCloudContainerCap) {
            if (haveTemplateContainerCap) {
                LOGGER.info(
                        "Provisioning '{}' number {} (of {}) on '{}'; Total containers: {} (of {})",
                        templateImage,
                        estimatedTemplateAgents + 1,
                        templateContainerCap,
                        name,
                        estimatedTotalAgents,
                        cloudContainerCap);
            } else {
                LOGGER.info(
                        "Provisioning '{}' on '{}'; Total containers: {} (of {})",
                        templateImage,
                        name,
                        estimatedTotalAgents,
                        cloudContainerCap);
            }
        } else {
            if (haveTemplateContainerCap) {
                LOGGER.info(
                        "Provisioning '{}' number {} (of {}) on '{}'",
                        templateImage,
                        estimatedTemplateAgents + 1,
                        templateContainerCap,
                        name);
            } else {
                LOGGER.info("Provisioning '{}' on '{}'", templateImage, name);
            }
        }
        return true;
    }

    @CheckForNull
    public static DockerCloud getCloudByName(String name) {
        return (DockerCloud) Jenkins.get().getCloud(name);
    }

    protected Object readResolve() {
        // Xstream is not calling readResolve() for nested Describable's
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
        final StringBuilder sb = startToString(this);
        // Maintenance node: This should list all the data we use in the equals()
        // method, but in the order the fields are declared in the class.
        // Note: If modifying this code, remember to update hashCode() and toString()
        bldToString(sb, "name", name);
        bldToString(sb, "dockerApi", dockerApi);
        bldToString(sb, "containerCap", containerCap);
        bldToString(sb, "exposeDockerHost", exposeDockerHost);
        bldToString(sb, "disabled", getDisabled());
        bldToString(sb, "templates", templates);
        endToString(sb);
        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        // Maintenance node: This should list all the fields from the equals method,
        // preferably in the same order.
        // Note: If modifying this code, remember to update equals() and toString()
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((dockerApi == null) ? 0 : dockerApi.hashCode());
        result = prime * result + containerCap;
        result = prime * result + (exposeDockerHost ? 1231 : 1237);
        result = prime * result + getDisabled().hashCode();
        result = prime * result + ((templates == null) ? 0 : templates.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DockerCloud that = (DockerCloud) o;
        // Maintenance note: This should include all non-transient fields.
        // Fields that are "usually unique" should go first.
        // Primitive fields should be tested before objects.
        // Computationally-expensive fields get tested last.
        // Note: If modifying this code, remember to update hashCode() and toString()
        if (!Objects.equals(name, that.name)) {
            return false;
        }
        if (!Objects.equals(dockerApi, that.dockerApi)) {
            return false;
        }
        if (containerCap != that.containerCap) {
            return false;
        }
        if (exposeDockerHost != that.exposeDockerHost) {
            return false;
        }
        if (!getDisabled().equals(that.getDisabled())) {
            return false;
        }
        if (!Objects.equals(templates, that.templates)) {
            return false;
        }
        return true;
    }

    public boolean isTriton() {
        if (_isTriton == null) {
            final Version remoteVersion;
            try (final DockerClient client = dockerApi.getClient()) {
                remoteVersion = client.versionCmd().exec();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
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

    @NonNull
    public static List<DockerCloud> instances() {
        List<DockerCloud> instances = new ArrayList<>();
        for (Cloud cloud : Jenkins.get().clouds) {
            if (cloud instanceof DockerCloud) {
                instances.add((DockerCloud) cloud);
            }
        }
        return instances;
    }

    @Restricted(NoExternalUse.class)
    @CheckForNull
    static DockerCloud findCloudForTemplate(final DockerTemplate template) {
        for (DockerCloud cloud : instances()) {
            if (cloud.hasTemplate(template)) {
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

        final Credentials c = firstOrNull(
                CredentialsProvider.lookupCredentials(IdCredentials.class, context, ACL.SYSTEM, List.of()),
                withId(registry.getCredentialsId()));
        final DockerRegistryToken t = c == null ? null : AuthenticationTokens.convert(DockerRegistryToken.class, c);
        if (t == null) {
            throw new IllegalArgumentException("Invalid Credential ID " + registry.getCredentialsId());
        }
        final String token = t.getToken();
        // What docker-commons claim to be a "token" is actually configuration storage
        // see https://github.com/docker/docker-ce/blob/v17.09.0-ce/components/cli/cli/config/configfile/file.go#L214
        // i.e base64 encoded username : password
        final String decode = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
        int i = decode.indexOf(':');
        if (i > 0) {
            String username = decode.substring(0, i);
            auth.withUsername(username);
        }
        auth.withPassword(decode.substring(i + 1));
        if (registry.getUrl() != null) {
            auth.withRegistryAddress(registry.getUrl());
        }
        return auth;
    }
}
