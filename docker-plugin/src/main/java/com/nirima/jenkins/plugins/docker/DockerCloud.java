package com.nirima.jenkins.plugins.docker;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.AbstractIdCredentialsListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerException;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Version;
import com.github.dockerjava.core.NameParser;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.*;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shaded.com.google.common.base.*;
import shaded.com.google.common.collect.Collections2;
import shaded.com.google.common.collect.Iterables;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.ws.rs.ProcessingException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.Callable;

import static com.nirima.jenkins.plugins.docker.client.ClientBuilderForPlugin.dockerClient;

/**
 * Docker Cloud configuration. Contains connection configuration,
 * {@link DockerTemplate} contains configuration for running docker image.
 *
 * @author magnayn
 */
public class DockerCloud extends Cloud {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerCloud.class);

    private List<DockerTemplate> templates;
    public final String serverUrl;
    public final int containerCap;

    private int connectTimeout;
    public final int readTimeout;
    public final String version;
    public final String credentialsId;

    private transient DockerClient connection;

    /**
     * Track the count per-AMI identifiers for AMIs currently being
     * provisioned, but not necessarily reported yet by docker.
     */
    private static final HashMap<String, Integer> provisioningAmis = new HashMap<>();

    @DataBoundConstructor
    public DockerCloud(String name,
                       List<? extends DockerTemplate> templates,
                       String serverUrl,
                       String containerCapStr,
                       int connectTimeout,
                       int readTimeout,
                       String credentialsId,
                       String version) {
        super(name);
        Preconditions.checkNotNull(serverUrl);
        this.version = version;
        this.credentialsId = credentialsId;
        this.serverUrl = serverUrl;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;

        if (templates != null) {
            this.templates = new ArrayList<>(templates);
        } else {
            this.templates = Collections.emptyList();
        }

        if (containerCapStr.equals("")) {
            this.containerCap = Integer.MAX_VALUE;
        } else {
            this.containerCap = Integer.parseInt(containerCapStr);
        }
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public String getContainerCapStr() {
        if (containerCap == Integer.MAX_VALUE) {
            return "";
        } else {
            return String.valueOf(containerCap);
        }
    }

    /**
     * Connects to Docker.
     *
     * @return Docker client.
     */
    public synchronized DockerClient connect() {
        if (connection == null) {
            connection = dockerClient().forCloud(this);
        }

        return connection;
    }

    /**
     * Decrease the count of slaves being "provisioned".
     */
    private void decrementAmiSlaveProvision(String ami) {
        synchronized (provisioningAmis) {
            int currentProvisioning;
            try {
                currentProvisioning = provisioningAmis.get(ami);
            } catch (NullPointerException npe) {
                return;
            }
            provisioningAmis.put(ami, Math.max(currentProvisioning - 1, 0));
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

                LOGGER.info("Will provision '{}', for label: '{}'", t.getDockerTemplateBase().getImage(), label);

                try {
                    if (!addProvisionedSlave(t)) {
                        templates.remove(t);
                        continue;
                    }
                } catch (Exception e) {
                    LOGGER.warn("Bad template '{}': '{}'. Trying next template...", t.getDockerTemplateBase().getImage(), e.getMessage());
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
                                            LOGGER.error("Error in provisioning; template='{}'", t, ex);
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
            LOGGER.error("Exception while provisioning for: {}", label, e);
            return Collections.emptyList();
        }
    }


    /**
     * Run docker container
     */
    public static String runContainer(DockerTemplateBase dockerTemplateBase,
                                      DockerClient dockerClient,
                                      DockerComputerLauncher launcher) throws DockerException {
        CreateContainerCmd containerConfig = dockerClient.createContainerCmd(dockerTemplateBase.getImage());

        dockerTemplateBase.fillContainerConfig(containerConfig);

        // contribute launcher specific options
        if (launcher != null) {
            launcher.appendContainerConfig(dockerTemplateBase, containerConfig);
        }

        // create
        CreateContainerResponse response = containerConfig.exec();
        String containerId = response.getId();

        // start
        StartContainerCmd startCommand = dockerClient.startContainerCmd(containerId);
        startCommand.exec();

        return containerId;
    }

    private DockerSlave provisionWithWait(DockerTemplate dockerTemplate) throws IOException, Descriptor.FormException {
        LOGGER.info("Trying to run container for {}", dockerTemplate.getDockerTemplateBase().getImage());
        final String containerId = runContainer(dockerTemplate.getDockerTemplateBase(), connect(), dockerTemplate.getLauncher());

        InspectContainerResponse ir;
        try {
            ir = connect().inspectContainerCmd(containerId).exec();
        } catch (ProcessingException ex) {
            connect().removeContainerCmd(containerId).withForce(true).exec();
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
            slaveName = slaveName + "@" + getDisplayName();
        } catch (Exception ex) {
            LOGGER.warn("Error fetching cloud name");
        }

        dockerTemplate.getLauncher().waitUp(getDisplayName(), dockerTemplate, ir);

        final ComputerLauncher launcher = dockerTemplate.getLauncher().getPreparedLauncher(getDisplayName(), dockerTemplate, ir);

        return new DockerSlave(slaveName, nodeDescription, launcher, containerId, dockerTemplate, getDisplayName());
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

    public List<DockerTemplate> getTemplates() {
        return templates;
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

        return dockerTemplates;
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
     * @param ami If AMI is left null, then all instances are counted.
     *            <p/>
     *            This includes those instances that may be started outside Hudson.
     */
    public int countCurrentDockerSlaves(final String ami) throws Exception {
        final DockerClient dockerClient = connect();

        List<Container> containers = dockerClient.listContainersCmd().exec();

        if (ami == null)
            return containers.size();

        List<Image> images = dockerClient.listImagesCmd().exec();

        NameParser.ReposTag repostag = NameParser.parseRepositoryTag(ami);
        final String fullAmi = repostag.repos + ":" + (repostag.tag.isEmpty() ? "latest" : repostag.tag);
        boolean imageExists = Iterables.any(images, new Predicate<Image>() {
            @Override
            public boolean apply(Image image) {
                return Arrays.asList(image.getRepoTags()).contains(fullAmi);
            }
        });

        if (!imageExists) {
            LOGGER.info("Pulling image '{}' since one was not found.  This may take awhile...", ami);
            //Identifier amiId = Identifier.fromCompoundString(ami);
            try (InputStream imageStream = dockerClient.pullImageCmd(ami).exec()) {
                int streamValue = 0;
                while (streamValue != -1) {
                    streamValue = imageStream.read();
                }
            }

            LOGGER.info("Finished pulling image '{}'", ami);
        }

        final InspectImageResponse ir = dockerClient.inspectImageCmd(ami).exec();

        Collection<Container> matching = Collections2.filter(containers, new Predicate<Container>() {
            public boolean apply(@Nullable Container container) {
                InspectContainerResponse
                        cis = dockerClient.inspectContainerCmd(container.getId()).exec();
                return (cis.getImageId().equalsIgnoreCase(ir.getId()));
            }
        });
        return matching.size();
    }

    /**
     * Check not too many already running.
     */
    private synchronized boolean addProvisionedSlave(DockerTemplate t) throws Exception {
        String ami = t.getDockerTemplateBase().getImage();
        int amiCap = t.instanceCap;

        int estimatedTotalSlaves = countCurrentDockerSlaves(null);
        int estimatedAmiSlaves = countCurrentDockerSlaves(ami);

        synchronized (provisioningAmis) {
            int currentProvisioning;

            for (int amiCount : provisioningAmis.values()) {
                estimatedTotalSlaves += amiCount;
            }
            try {
                currentProvisioning = provisioningAmis.get(ami);
            } catch (NullPointerException npe) {
                currentProvisioning = 0;
            }

            estimatedAmiSlaves += currentProvisioning;

            if (estimatedTotalSlaves >= containerCap) {
                LOGGER.info("Not Provisioning '{}'; Server '{}' full with '{}' container(s)", ami, containerCap, name);
                return false;      // maxed out
            }

            if (amiCap != 0 && estimatedAmiSlaves >= amiCap) {
                LOGGER.info("Not Provisioning '{}'. Instance limit of '{}' reached on server '{}'", ami, amiCap, name);
                return false;      // maxed out
            }

            LOGGER.info("Provisioning '{}' number '{}' on '{}'; Total containers: '{}'",
                    ami, estimatedAmiSlaves, name, estimatedTotalSlaves);

            provisioningAmis.put(ami, currentProvisioning + 1);
            return true;
        }
    }

    public static DockerCloud getCloudByName(String name) {
        return (DockerCloud) Jenkins.getInstance().getCloud(name);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("serverUrl", serverUrl)
                .toString();
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
                @QueryParameter String version
        ) throws IOException, ServletException, DockerException {
            try {
                DockerClient dc = dockerClient()
                        .forServer(serverUrl, version)
                        .withCredentials(credentialsId);

                Version verResult = dc.versionCmd().exec();

                return FormValidation.ok("Version = " + verResult.getVersion());
            } catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context) {

            List<StandardCertificateCredentials> credentials = CredentialsProvider.lookupCredentials(StandardCertificateCredentials.class, context);

            return new CredentialsListBoxModel().withEmptySelection()
                    .withMatching(CredentialsMatchers.always(),
                            credentials);
        }

        public static class CredentialsListBoxModel
                extends AbstractIdCredentialsListBoxModel<CredentialsListBoxModel, StandardCertificateCredentials> {
            @NonNull
            protected String describe(@NonNull StandardCertificateCredentials c) {
                return CredentialsNameProvider.name(c);
            }
        }
    }

}
