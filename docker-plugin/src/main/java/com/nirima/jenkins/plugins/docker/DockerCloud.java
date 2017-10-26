package com.nirima.jenkins.plugins.docker;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Version;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.nirima.jenkins.plugins.docker.client.ClientBuilderForPlugin;
import com.nirima.jenkins.plugins.docker.client.ClientConfigBuilderForPlugin;
import com.nirima.jenkins.plugins.docker.client.DockerCmdExecConfig;
import com.nirima.jenkins.plugins.docker.client.DockerCmdExecConfigBuilderForPlugin;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.docker.connector.DockerComputerConnector;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import org.apache.commons.codec.binary.Base64;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryToken;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;

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

    private List<DockerTemplate> templates;
    private transient HashMap<Long, DockerTemplate> jobTemplates;

    private DockerServerEndpoint dockerHost;

    @Deprecated
    private transient String serverUrl;
    @Deprecated
    public  transient String credentialsId;

    private int connectTimeout;
    public final int readTimeout;
    public final String version;
    public final String dockerHostname;

    private transient DockerClient connection;

    /**
     * Total max allowed number of containers
     */
    private int containerCap = 100;

    /**
     * Is this cloud actually a swarm?
     */
    private transient Boolean _isSwarm;

    /**
     * Is this cloud running Joyent Triton?
     */
    private transient Boolean _isTriton;

    /**
     * Track the count per image name for images currently being
     * provisioned, but not necessarily reported yet by docker.
     */
    private static final HashMap<String, Integer> provisionedImages = new HashMap<>();

    /**
     * Indicate if docker host used to run container is exposed inside container as DOCKER_HOST environment variable
     */
    private Boolean exposeDockerHost;


    @DataBoundConstructor
    public DockerCloud(String name,
                       List<? extends DockerTemplate> templates,
                       DockerServerEndpoint dockerHost,
                       int containerCap,
                       int connectTimeout,
                       int readTimeout,
                       String version,
                       String dockerHostname) {
        super(name);
        this.version = version;
        this.dockerHost = dockerHost;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.dockerHostname = dockerHostname;

        if (templates != null) {
            this.templates = new ArrayList<>(templates);
        } else {
            this.templates = new ArrayList<>();
        }

        setContainerCap(containerCap);
    }

    @Deprecated
    public DockerCloud(String name,
                       List<? extends DockerTemplate> templates,
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
                       List<? extends DockerTemplate> templates,
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


    public int getConnectTimeout() {
        return connectTimeout;
    }


    public DockerServerEndpoint getDockerHost() {
        return dockerHost;
    }

    @Deprecated
    public String getServerUrl() {
        return getDockerHost().getUri();
    }

    public String getDockerHostname() {
        return dockerHostname;
    }

    /**
     * @deprecated use {@link #getContainerCap()}
     */
    @Deprecated
    public String getContainerCapStr() {
        if (containerCap == Integer.MAX_VALUE) {
            return "";
        } else {
            return String.valueOf(containerCap);
        }
    }

    public int getContainerCap() {
        return containerCap;
    }

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
     * Connects to Docker.
     *
     * @return Docker client.
     */
    public synchronized DockerClient getClient() {
        if (connection == null) {
            final DockerClientConfig clientConfig = ClientConfigBuilderForPlugin.dockerClientConfig()
                    .forCloud(this)
                    .build();

            final DockerCmdExecConfig execConfig = DockerCmdExecConfigBuilderForPlugin.builder()
                    .forCloud(this)
                    .build();

            connection = ClientBuilderForPlugin.builder()
                    .withDockerClientConfig(clientConfig)
                    .withDockerCmdExecConfig(execConfig)
                    .build();
        }

        return connection;
    }

    /**
     * Decrease the count of slaves being "provisioned".
     */
    private void decrementAmiSlaveProvision(String ami) {
        synchronized (provisionedImages) {
            int currentProvisioning;
            try {
                currentProvisioning = provisionedImages.get(ami);
            } catch (NullPointerException npe) {
                return;
            }
            provisionedImages.put(ami, Math.max(currentProvisioning - 1, 0));
        }
    }

    @Override
    public synchronized Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        try {
            LOGGER.info("Asked to provision {} slave(s) for: {}", excessWorkload, label);

            List<NodeProvisioner.PlannedNode> r = new ArrayList<>();

            final List<DockerTemplate> templates = getTemplates(label);

            while (excessWorkload > 0 && !templates.isEmpty()) {
                final DockerTemplate t = templates.get(0); // get first

                LOGGER.info("Will provision '{}', for label: '{}', in cloud: '{}'",
                        t.getDockerTemplateBase().getImage(), label, getDisplayName());

                try {
                    if (!addProvisionedSlave(t)) {
                        templates.remove(t);
                        continue;
                    }
                } catch (Exception e) {
                    LOGGER.warn("Bad template '{}' in cloud '{}': '{}'. Trying next template...",
                            t.getDockerTemplateBase().getImage(), getDisplayName(), e.getMessage(), e);
                    templates.remove(t);
                    continue;
                }

                r.add(new NodeProvisioner.PlannedNode(
                        t.getDockerTemplateBase().getDisplayName(),
                        Computer.threadPoolForRemoting.submit(new Callable<Node>() {
                            public Node call() throws Exception {
                                try {
                                    // TODO where can we log provisioning progress ?
                                    return provisionFromTemplate(t, TaskListener.NULL);
                                } catch (Exception ex) {
                                    LOGGER.error("Error in provisioning; template='{}' for cloud='{}'",
                                            t, getDisplayName(), ex);
                                    throw Throwables.propagate(ex);
                                } finally {
                                    decrementAmiSlaveProvision(t.getDockerTemplateBase().getImage());
                                }
                            }
                        }),
                        t.getNumExecutors()));

                excessWorkload -= t.getNumExecutors();
            }

            return r;
        } catch (Exception e) {
            LOGGER.error("Exception while provisioning for label: '{}', cloud='{}'", label, getDisplayName(), e);
            return Collections.emptyList();
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

    private void pullImage(DockerTemplate template) throws IOException, InterruptedException {

        String image = template.getFullImageId();
        final DockerClient client = getClient();

        if (template.getPullStrategy().shouldPullImage(client, image)) {
            // TODO create a FlyWeightTask so end-user get visibility on pull operation progress
            LOGGER.info("Pulling image '{}'. This may take awhile...", image);

            long startTime = System.currentTimeMillis();

            PullImageCmd cmd =  client.pullImageCmd(image);
            final DockerRegistryEndpoint registry = template.getRegistry();
            setRegistryAuthentication(cmd, registry, Jenkins.getInstance());
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

    private DockerSlave provisionFromTemplate(DockerTemplate template, TaskListener listener) throws IOException, Descriptor.FormException, InterruptedException {

        final DockerClient client = getClient();
        final DockerComputerConnector connector = template.getConnector();
        pullImage(template);

        LOGGER.info("Trying to run container for {}", template.getImage());
        CreateContainerCmd cmd = client.createContainerCmd(template.getImage());
        template.fillContainerConfig(cmd);

        connector.beforeContainerCreated(this, template, cmd);

        String containerId = cmd.exec().getId();

        try {
            connector.beforeContainerStarted(this, template, containerId);

            client.startContainerCmd(containerId).exec();

            connector.afterContainerStarted(this, template, containerId);
        } catch (DockerException e) {
            // if something went wrong, cleanup aborted container
            client.removeContainerCmd(containerId).withForce(true).exec();
            throw e;
        }

        DockerSlave slave = new DockerSlave(template, containerId,
                name + '-' + containerId.substring(0,12),
                "Docker Agent [" + template.getImage() + " on "+ name + "]",
                template.getRemoteFs(),
                template.getNumExecutors(),
                template.getMode(),
                template.getLabelString(),
                connector.launch(this, containerId, template, listener),
                template.getRetentionStrategyCopy(),
                template.getNodeProperties());

        slave.setContainerId(containerId);
        slave.setCloudId(name);
        return slave;
    }

    @Override
    public boolean canProvision(Label label) {
        return getTemplate(label) != null;
    }

    @CheckForNull
    public DockerTemplate getTemplate(String template) {
        for (DockerTemplate t : templates) {
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
        templates.add(t);
    }

    /**
     * Adds a template which is temporary provided and bound to a specific job.
     * 
     * @param jobId Unique id (per master) of the job to which the template is bound.
     * @param template The template to bound to a specific job.
     */
    public synchronized void addJobTemplate(long jobId, DockerTemplate template) {
    	jobTemplates.put(jobId, template);
    }

    /**
     * Removes a template which is bound to a specific job.
     * 
     * @param jobId Id of the job.
     */
    public synchronized void removeJobTemplate(long jobId) {
    	if (jobTemplates.remove(jobId) == null) {
    		LOGGER.warn("Couldn't remove template for job with id: {}", jobId);
    	}
    }

    public List<DockerTemplate> getTemplates() {
    	List<DockerTemplate> t = new ArrayList<DockerTemplate>(templates);
    	t.addAll(getJobTemplates().values());
        return t;
    }

    /**
     * Multiple amis can have the same label.
     *
     * @return Templates matched to requested label assuming slave Mode
     */
    public List<DockerTemplate> getTemplates(Label label) {
        ArrayList<DockerTemplate> dockerTemplates = new ArrayList<>();

        for (DockerTemplate t : templates) {
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
    private HashMap<Long, DockerTemplate> getJobTemplates() {
        if (jobTemplates == null) {
            jobTemplates = new HashMap<Long, DockerTemplate>();
        }
        
        return jobTemplates;
    }

    /**
     * Remove Docker template
     */
    public synchronized void removeTemplate(DockerTemplate t) {
        templates.remove(t);
    }

    /**
     * Counts the number of instances in Docker currently running that are using the specified image.
     *
     * @param imageName If null, then all instances are counted.
     *            <p/>
     *            This includes those instances that may be started outside Hudson.
     */
    public int countCurrentDockerSlaves(final String imageName) throws Exception {
        int count = 0;
        List<Container> containers = getClient().listContainersCmd().exec();

        if (imageName == null) {
            count = containers.size();
        } else {
            for (Container container : containers) {
                String containerImage = container.getImage();
                if (containerImage.equals(imageName)) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Check not too many already running.
     */
    private synchronized boolean addProvisionedSlave(DockerTemplate t) throws Exception {
        String ami = t.getDockerTemplateBase().getImage();
        int amiCap = t.instanceCap;

        int estimatedTotalSlaves = countCurrentDockerSlaves(null);
        int estimatedAmiSlaves = countCurrentDockerSlaves(ami);

        synchronized (provisionedImages) {
            int currentProvisioning = 0;
            if (provisionedImages.containsKey(ami)) {
                currentProvisioning = provisionedImages.get(ami);
            }

            for (int amiCount : provisionedImages.values()) {
                estimatedTotalSlaves += amiCount;
            }

            estimatedAmiSlaves += currentProvisioning;

            if (estimatedTotalSlaves >= getContainerCap()) {
                LOGGER.info("Not Provisioning '{}'; Server '{}' full with '{}' container(s)", ami, name, getContainerCap());
                return false;      // maxed out
            }

            if (amiCap != 0 && estimatedAmiSlaves >= amiCap) {
                LOGGER.info("Not Provisioning '{}'. Instance limit of '{}' reached on server '{}'", ami, amiCap, name);
                return false;      // maxed out
            }

            LOGGER.info("Provisioning '{}' number '{}' on '{}'; Total containers: '{}'",
                    ami, estimatedAmiSlaves, name, estimatedTotalSlaves);

            provisionedImages.put(ami, currentProvisioning + 1);
            return true;
        }
    }

    public static DockerCloud getCloudByName(String name) {
        return (DockerCloud) Jenkins.getInstance().getCloud(name);
    }

    public Object readResolve() {
        //Xstream is not calling readResolve() for nested Describable's
        for (DockerTemplate template : getTemplates()) {
            template.readResolve();
        }

        if (dockerHost == null) {
            serverUrl = sanitizeUrl(serverUrl);
            // migration to docker-commons
            dockerHost = new DockerServerEndpoint(serverUrl, credentialsId);
        }

        return this;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", name)
                .add("serverUrl", serverUrl)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DockerCloud that = (DockerCloud) o;

        if (containerCap != that.containerCap) return false;
        if (connectTimeout != that.connectTimeout) return false;
        if (readTimeout != that.readTimeout) return false;
        if (templates != null ? !templates.equals(that.templates) : that.templates != null) return false;
        if (!dockerHost.equals(that.dockerHost))return false;
        if (version != null ? !version.equals(that.version) : that.version != null) return false;
        return true;
    }

    /* package */ boolean isSwarm() {
        Version remoteVersion = getClient().versionCmd().exec();
        // Cache the return.
        if( _isSwarm == null ) {
            _isSwarm = remoteVersion.getVersion().startsWith("swarm");
        }
        return _isSwarm;
    }

    public boolean isTriton() {
        Version remoteVersion = getClient().versionCmd().exec();

        if( _isTriton == null ) {
            _isTriton = remoteVersion.getOperatingSystem().equals("solaris");
        }
        return _isTriton;
    }

    public boolean isExposeDockerHost() {
        // if null (i.e migration from previous installation) consider true for backward compatibility
        return exposeDockerHost != null ? exposeDockerHost : true;
    }

    @DataBoundSetter
    public void setExposeDockerHost(boolean exposeDockerHost) {
        this.exposeDockerHost = exposeDockerHost;
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


    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        @Override
        public String getDisplayName() {
            return "Docker";
        }

        public FormValidation doTestConnection(
                @QueryParameter String uri,
                @QueryParameter String credentialsId,
                @QueryParameter String version,
                @QueryParameter Integer readTimeout,
                @QueryParameter Integer connectTimeout
        ) throws IOException, ServletException, DockerException {
            try {
                final DockerClientConfig clientConfig = ClientConfigBuilderForPlugin.dockerClientConfig()
                        .forServer(uri, version)
                        .withCredentials(credentialsId)
                        .build();

                final DockerCmdExecConfig execConfig = DockerCmdExecConfigBuilderForPlugin.builder()
                        .withReadTimeout(readTimeout)
                        .withConnectTimeout(connectTimeout)
                        .build();

                DockerClient dc = ClientBuilderForPlugin.builder()
                        .withDockerClientConfig(clientConfig)
                        .withDockerCmdExecConfig(execConfig)
                        .build();

                Version verResult = dc.versionCmd().exec();

                return FormValidation.ok("Version = " + verResult.getVersion() + ", API Version = " + verResult.getApiVersion());
            } catch (Exception e) {
                return FormValidation.error(e, e.getMessage());
            }
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context, @QueryParameter String value) {
            AccessControlled ac = (context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance());
            if (!ac.hasPermission(Jenkins.ADMINISTER)) {
                return new StandardListBoxModel().includeCurrentValue(value);
            }

            return new StandardListBoxModel().includeAs(
                    ACL.SYSTEM, context, DockerServerCredentials.class,
                    Collections.<DomainRequirement>emptyList());
        }
    }


    // unfortunately there's no common interface for Registry related Docker-java commands

    @Restricted(NoExternalUse.class)
    public static void setRegistryAuthentication(PullImageCmd cmd, DockerRegistryEndpoint registry, ItemGroup context) throws IOException {
        if (registry != null && registry.getCredentialsId() != null) {
            AuthConfig auth = getAuthConfig(registry, context);
            cmd.withAuthConfig(auth);
        }
    }

    @Restricted(NoExternalUse.class)
    public static void setRegistryAuthentication(PushImageCmd cmd, DockerRegistryEndpoint registry, ItemGroup context) throws IOException {
        if (registry != null && registry.getCredentialsId() != null) {
            AuthConfig auth = getAuthConfig(registry, context);
            cmd.withAuthConfig(auth);
        }
    }

    @Restricted(NoExternalUse.class)
    public static AuthConfig getAuthConfig(DockerRegistryEndpoint registry, ItemGroup context) throws IOException {
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
        return auth;
    }
}
