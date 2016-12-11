package com.nirima.jenkins.plugins.docker;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.*;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.github.dockerjava.api.DockerClient;

import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Version;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.NameParser;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.nirima.jenkins.plugins.docker.client.ClientBuilderForPlugin;
import com.nirima.jenkins.plugins.docker.client.ClientConfigBuilderForPlugin;
import com.nirima.jenkins.plugins.docker.client.DockerCmdExecConfig;
import com.nirima.jenkins.plugins.docker.client.DockerCmdExecConfigBuilderForPlugin;
import com.nirima.jenkins.plugins.docker.launcher.DockerComputerLauncher;
import com.nirima.jenkins.plugins.docker.utils.DockerDirectoryCredentials;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.*;
import hudson.security.ACL;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shaded.com.google.common.base.MoreObjects;
import shaded.com.google.common.base.Preconditions;
import shaded.com.google.common.base.Predicate;
import shaded.com.google.common.base.Throwables;
import shaded.com.google.common.collect.Iterables;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

import static org.bouncycastle.crypto.tls.ConnectionEnd.client;

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
    private String serverUrl;
    private int connectTimeout;
    public final int readTimeout;
    public final String version;
    public final String credentialsId;
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
        super(name);
        Preconditions.checkNotNull(serverUrl);
        this.version = version;
        this.credentialsId = credentialsId;
        this.serverUrl = sanitizeUrl(serverUrl);
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.dockerHostname = dockerHostname;

        if (templates != null) {
            this.templates = new ArrayList<>(templates);
        } else {
            this.templates = Collections.emptyList();
        }

        if (containerCapStr.equals("")) {
            setContainerCap(Integer.MAX_VALUE);
        } else {
            setContainerCap(Integer.parseInt(containerCapStr));
        }
    }

    @DataBoundConstructor
    public DockerCloud(String name,
                       List<? extends DockerTemplate> templates,
                       String serverUrl,
                       int containerCap,
                       int connectTimeout,
                       int readTimeout,
                       String credentialsId,
                       String version,
                       String dockerHostname) {
        super(name);
        Preconditions.checkNotNull(serverUrl);
        this.version = version;
        this.credentialsId = credentialsId;
        this.serverUrl = sanitizeUrl(serverUrl);
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.dockerHostname = dockerHostname;

        if (templates != null) {
            this.templates = new ArrayList<>(templates);
        } else {
            this.templates = Collections.emptyList();
        }

        setContainerCap(containerCap);
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public String getServerUrl() {
        return serverUrl;
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
            LOGGER.info("Asked to provision {} slave(s) for: {}", new Object[]{excessWorkload, label});

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
                                            return provisionWithWait(t);
                                        } catch (Exception ex) {
                                            LOGGER.error("Error in provisioning; template='{}' for cloud='{}'",
                                                    t, getDisplayName(), ex);
                                            throw Throwables.propagate(ex);
                                        } finally {
                                            decrementAmiSlaveProvision(t.getDockerTemplateBase().getImage());
                                        }
                                    }
                                }),
                                t.getNumExecutors())
                );

                excessWorkload -= t.getNumExecutors();
            }

            return r;
        } catch (Exception e) {
            LOGGER.error("Exception while provisioning for label: '{}', cloud='{}'", label, getDisplayName(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Run docker container
     */
    public static String runContainer(DockerTemplate dockerTemplate,
                                      DockerClient dockerClient,
                                      DockerComputerLauncher launcher)
            throws DockerException, IOException {
        final DockerTemplateBase dockerTemplateBase = dockerTemplate.getDockerTemplateBase();
        CreateContainerCmd containerConfig = dockerClient.createContainerCmd(dockerTemplateBase.getImage());

        dockerTemplateBase.fillContainerConfig(containerConfig);

        // contribute launcher specific options
        if (launcher != null) {
            launcher.appendContainerConfig(dockerTemplate, containerConfig, dockerClient);
        }

        // create
        CreateContainerResponse response = containerConfig.exec();
        String containerId = response.getId();

        // start
        StartContainerCmd startCommand = dockerClient.startContainerCmd(containerId);
        startCommand.exec();

        return containerId;
    }

    /**
     * for publishers/builders. Simply runs container in docker cloud
     */
    public static String runContainer(DockerTemplateBase dockerTemplateBase,
                                      DockerClient dockerClient,
                                      DockerComputerLauncher launcher) {
        CreateContainerCmd containerConfig = dockerClient.createContainerCmd(dockerTemplateBase.getImage());

        dockerTemplateBase.fillContainerConfig(containerConfig);

//        // contribute launcher specific options
//        if (launcher != null) {
//            launcher.appendContainerConfig(dockerTemplateBase, containerConfig);
//        }

        // create
        CreateContainerResponse response = containerConfig.exec();
        String containerId = response.getId();

        // start
        StartContainerCmd startCommand = dockerClient.startContainerCmd(containerId);
        startCommand.exec();

        return containerId;
    }

    private void pullImage(DockerTemplate dockerTemplate)  throws IOException {
        final String imageName = dockerTemplate.getDockerTemplateBase().getImage();

        List<Image> images = getClient().listImagesCmd().exec();

        NameParser.ReposTag repostag = NameParser.parseRepositoryTag(imageName);
        // if image was specified without tag, then treat as latest
        final String fullImageName = repostag.repos + ":" + (repostag.tag.isEmpty() ? "latest" : repostag.tag);

        boolean imageExists = Iterables.any(images, new Predicate<Image>() {
            @Override
            public boolean apply(Image image) {
                if (image == null || image.getRepoTags() == null) {
                    return false;
                } else {
                return Arrays.asList(image.getRepoTags()).contains(fullImageName);
            }
            }
        });

        boolean pull = imageExists ?
                dockerTemplate.getPullStrategy().pullIfExists(imageName) :
                dockerTemplate.getPullStrategy().pullIfNotExists(imageName);

        if (pull) {
            LOGGER.info("Pulling image '{}' {}. This may take awhile...", imageName,
                    imageExists ? "again" : "since one was not found");

            long startTime = System.currentTimeMillis();

            PullImageCmd imgCmd =  getClient().pullImageCmd(imageName);
            AuthConfig authConfig = JenkinsUtils.getAuthConfigFor(imageName);
            if( authConfig != null ) {
                imgCmd.withAuthConfig(authConfig);
            }
            PullImageResultCallback cmd = imgCmd.exec(new PullImageResultCallback());

            // Work-around for API issue in docker.
            if( DockerPluginConfiguration.get().getPullFix()) {
                try {
                    cmd.awaitCompletion();
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interruption whilst pulling",e);
                }
            } else {
                cmd.awaitSuccess();
            }
            long pullTime = System.currentTimeMillis() - startTime;
            LOGGER.info("Finished pulling image '{}', took {} ms", imageName, pullTime);
        }
    }

    private DockerSlave provisionWithWait(DockerTemplate dockerTemplate) throws IOException, Descriptor.FormException {
        pullImage(dockerTemplate);

        LOGGER.info("Trying to run container for {}", dockerTemplate.getDockerTemplateBase().getImage());
        final String containerId = runContainer(dockerTemplate, getClient(), dockerTemplate.getLauncher());

        InspectContainerResponse ir;
        try {
            ir = getClient().inspectContainerCmd(containerId).exec();
        } catch (DockerException ex) {
            getClient().removeContainerCmd(containerId).withForce(true).exec();
            throw ex;
        }

        // Build a description up:
        String nodeDescription = "Docker Node [" + dockerTemplate.getDockerTemplateBase().getImage() + " on ";
        try {
            nodeDescription += getDisplayName();
        } catch (Exception ex) {
            nodeDescription += "???";
        }
        nodeDescription += "]";

        String slaveName = containerId.substring(0, 12);

        try {
            slaveName = getDisplayName() + "-" + slaveName;
        } catch (Exception ex) {
            LOGGER.warn("Error fetching cloud name");
        }

        dockerTemplate.getLauncher().waitUp(getDisplayName(), dockerTemplate, ir);

        final ComputerLauncher launcher = dockerTemplate.getLauncher().getPreparedLauncher(getDisplayName(), dockerTemplate, ir);

        if( isSwarm() ) {
            return new DockerSwarmSlave(this, ir, slaveName, nodeDescription, launcher, containerId, dockerTemplate, getDisplayName());
        } else {
            return new DockerSlave(slaveName, nodeDescription, launcher, containerId, dockerTemplate, getDisplayName());
        }
    }

    @Override
    public boolean canProvision(Label label) {
        return getTemplate(label) != null;
    }

    @CheckForNull
    public DockerTemplate getTemplate(String template) {
        for (DockerTemplate t : templates) {
            if (t.getDockerTemplateBase().getImage().equals(template)) {
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
        // This change will bite a lot of people otherwise.
        serverUrl = sanitizeUrl(serverUrl);

        return this;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
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
        if (serverUrl != null ? !serverUrl.equals(that.serverUrl) : that.serverUrl != null) return false;
        if (version != null ? !version.equals(that.version) : that.version != null) return false;
        if (credentialsId != null ? !credentialsId.equals(that.credentialsId) : that.credentialsId != null)
            return false;
        return !(connection != null ? !connection.equals(that.connection) : that.connection != null);

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


    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        @Override
        public String getDisplayName() {
            return "Docker";
        }

        public FormValidation doTestConnection(
                @QueryParameter String serverUrl,
                @QueryParameter String credentialsId,
                @QueryParameter String version,
                @QueryParameter Integer readTimeout,
                @QueryParameter Integer connectTimeout
        ) throws IOException, ServletException, DockerException {
            try {
                final DockerClientConfig clientConfig = ClientConfigBuilderForPlugin.dockerClientConfig()
                        .forServer(serverUrl, version)
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
                Info infoResult = dc.infoCmd().exec();

                return FormValidation.ok("Version = " + verResult.getVersion() + ", API Version = " + verResult.getApiVersion() + ", OS type = " + infoResult.getOsType());
            } catch (Exception e) {
                return FormValidation.error(e, e.getMessage());
            }
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {

            List<StandardCertificateCredentials> credentials = CredentialsProvider.lookupCredentials(StandardCertificateCredentials.class, context, ACL.SYSTEM,Collections.<DomainRequirement>emptyList());
            List<DockerDirectoryCredentials> c2 = CredentialsProvider.lookupCredentials(DockerDirectoryCredentials.class, context, ACL.SYSTEM,Collections.<DomainRequirement>emptyList());

            return new StandardListBoxModel()
                    .withEmptySelection()
                    .withMatching(CredentialsMatchers.always(), credentials)
                    .withMatching(CredentialsMatchers.always(), c2);
        }


    }

}
