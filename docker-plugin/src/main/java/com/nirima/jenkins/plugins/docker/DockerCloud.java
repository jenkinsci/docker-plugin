package com.nirima.jenkins.plugins.docker;

import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.github.dockerjava.jaxrs.DockerCmdExecFactoryImpl;
import shaded.com.google.common.base.Objects;
import shaded.com.google.common.base.Preconditions;
import shaded.com.google.common.base.Predicate;
import shaded.com.google.common.base.Strings;
import shaded.com.google.common.base.Throwables;
import shaded.com.google.common.collect.Collections2;
import shaded.com.google.common.collect.Iterables;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserListBoxModel;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.AbstractIdCredentialsListBoxModel;
import com.cloudbees.plugins.credentials.common.CertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.HostnamePortRequirement;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerException;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Version;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.KeystoreSSLConfig;
import com.github.dockerjava.core.NameParser;
import com.trilead.ssh2.Connection;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.*;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.HashMap;

/**
 * Created by magnayn on 08/01/2014.
 */
public class DockerCloud extends Cloud {

    private static final Logger LOGGER = Logger.getLogger(DockerCloud.class.getName());

    public static final String CLOUD_ID_PREFIX = "docker-";

    public final List<DockerTemplate> templates;
    public final String serverUrl;
    public final int containerCap;

    public final int connectTimeout;
    public final int readTimeout;
    public final String version;
    public final String credentialsId;

    private transient DockerClient connection;

    /* Track the count per-AMI identifiers for AMIs currently being
     * provisioned, but not necessarily reported yet by docker.
     */
    private static final HashMap<String, Integer> provisioningAmis = new HashMap<String, Integer>();

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
        if (templates != null)
            this.templates = new ArrayList<DockerTemplate>(templates);
        else
            this.templates = Collections.emptyList();

        if (containerCapStr.equals("")) {
            this.containerCap = Integer.MAX_VALUE;
        } else {
            this.containerCap = Integer.parseInt(containerCapStr);
        }

        readResolve();
    }

    public String getContainerCapStr() {
        if (containerCap==Integer.MAX_VALUE) {
            return "";
        } else {
            return String.valueOf(containerCap);
        }
    }

    protected Object readResolve() {
        for (DockerTemplate t : templates)
            t.parent = this;
        return this;
    }

    /**
     * Connects to Docker.
     *
     * @return Docker client.
     */
    public synchronized DockerClient connect() {

        if (connection == null) {
            connection = buildConnection();
        }
        return connection;
    }

    public DockerClientConfig getDockerClientConfig() {
        DockerClientConfig.DockerClientConfigBuilder config = DockerClientConfig.createDefaultConfigBuilder();

        config.withUri(serverUrl);

        if (!Strings.isNullOrEmpty(version)) {
            config.withVersion(version);
        }

        addCredentials(config, credentialsId);

        if (readTimeout > 0)
            config.withReadTimeout(readTimeout * 1000);

        return config.build();
    }

    private static void addCredentials(DockerClientConfig.DockerClientConfigBuilder config,
                                       String credentialsId) {
        if( !Strings.isNullOrEmpty(credentialsId)) {
            Credentials credentials = lookupSystemCredentials(credentialsId);

            if( credentials instanceof CertificateCredentials ) {
                CertificateCredentials certificateCredentials = (CertificateCredentials)credentials;
                config.withSSLConfig( new KeystoreSSLConfig( certificateCredentials.getKeyStore(), certificateCredentials.getPassword().getPlainText() ));
            }
            else if( credentials instanceof StandardUsernamePasswordCredentials ) {
                StandardUsernamePasswordCredentials usernamePasswordCredentials = ((StandardUsernamePasswordCredentials)credentials);

                config.withUsername( usernamePasswordCredentials.getUsername() );
                config.withPassword(usernamePasswordCredentials.getPassword().getPlainText());
            }
        }
    }

    public static Credentials lookupSystemCredentials(String credentialsId) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(Credentials.class,
                        Jenkins.getInstance(),
                        ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(credentialsId)
        );
    }

    private DockerClient buildConnection() {
        LOGGER.log(Level.FINE, "Building connection to docker host \"{0}\" at: {1}", new Object[]{name,serverUrl});

        return DockerClientBuilder.getInstance(getDockerClientConfig())
                .withDockerCmdExecFactory(new DockerCmdExecFactoryImpl())
                .build();
    }

    /**
     * Decrease the count of slaves being "provisioned".
     */
    private void decrementAmiSlaveProvision(String ami) {
        synchronized (provisioningAmis) {
            int currentProvisioning;
            try {
                currentProvisioning = provisioningAmis.get(ami);
            } catch(NullPointerException npe) {
                return;
            }
            provisioningAmis.put(ami, Math.max(currentProvisioning - 1, 0));
        }
    }

    @Override
    public synchronized Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        try {

            LOGGER.log(Level.INFO, "Asked to provision {0} slave(s) for: {1}", new Object[]{excessWorkload,label});

            List<NodeProvisioner.PlannedNode> r = new ArrayList<NodeProvisioner.PlannedNode>();

            final DockerTemplate t = getTemplate(label);

            LOGGER.log(Level.INFO, "Will provision \"{0}\" for: {1}", new Object[]{t.image,label});

            while (excessWorkload>0) {

                if (!addProvisionedSlave(t.image, t.instanceCap)) {
                    break;
                }

                r.add(new NodeProvisioner.PlannedNode(t.getDisplayName(),
                        Computer.threadPoolForRemoting.submit(new Callable<Node>() {
                            public Node call() throws Exception {
                                // TODO: record the output somewhere
                                DockerSlave slave = null;
                                try {
                                    slave = t.provision(new StreamTaskListener(System.out));
                                    final Jenkins jenkins = Jenkins.getInstance();
                                    // TODO once the baseline is 1.592+ switch to Queue.withLock
                                    synchronized (jenkins.getQueue()) {
                                        jenkins.addNode(slave);
                                    }
                                    // Docker instances may have a long init script. If we declare
                                    // the provisioning complete by returning without the connect
                                    // operation, NodeProvisioner may decide that it still wants
                                    // one more instance, because it sees that (1) all the slaves
                                    // are offline (because it's still being launched) and
                                    // (2) there's no capacity provisioned yet.
                                    //
                                    // deferring the completion of provisioning until the launch
                                    // goes successful prevents this problem.
                                    slave.toComputer().connect(false).get();
                                    return slave;
                                }
                                catch(Exception ex) {
                                    LOGGER.log(Level.SEVERE, "Error in provisioning; slave=" + slave + ", template=" + t);

                                    ex.printStackTrace();
                                    throw Throwables.propagate(ex);
                                }
                                finally {
                                    decrementAmiSlaveProvision(t.image);
                                }
                            }
                        })
                        ,t.getNumExecutors()));

                excessWorkload -= t.getNumExecutors();

            }
            return r;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,"Exception while provisioning for: " + label,e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean canProvision(Label label) {
        return getTemplate(label)!=null;
    }

    @CheckForNull
    public DockerTemplate getTemplate(String template) {
        for (DockerTemplate t : templates) {
            if(t.image.equals(template)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Gets {@link DockerTemplate} that has the matching {@link Label}.
     */
    @CheckForNull
    public DockerTemplate getTemplate(Label label) {
        for (DockerTemplate t : templates) {
            if(label == null || label.matches(t.getLabelSet())) {
                return t;
            }
        }
        return null;
    }

    /**
     * Add a new template to the cloud
     */
    public void addTemplate(DockerTemplate t) {
        this.templates.add(t);
        t.parent = this;
    }

    /**
     * Remove a
     * @param t
     */
    public void removeTemplate(DockerTemplate t) {
        this.templates.remove(t);
    }

    /**
     * Counts the number of instances in Docker currently running that are using the specifed image.
     *
     * @param ami If AMI is left null, then all instances are counted.
     * <p>
     * This includes those instances that may be started outside Hudson.
     */
    public int countCurrentDockerSlaves(final String ami) throws Exception {
        final DockerClient dockerClient = connect();

        List<Container> containers = dockerClient.listContainersCmd().exec();

        if (ami == null)
            return containers.size();

        List<Image> images = dockerClient.listImagesCmd().exec();

        NameParser.ReposTag repostag = NameParser.parseRepositoryTag(ami);
        final String fullAmi = repostag.repos + ":" + (repostag.tag.isEmpty()?"latest":repostag.tag);
        boolean imageExists = Iterables.any(images, new Predicate<Image>(){
            @Override
            public boolean apply(Image image) {
                return Arrays.asList(image.getRepoTags()).contains(fullAmi);
            }
        });

        if (!imageExists) {
            LOGGER.log(Level.INFO, "Pulling image \"{0}\" since one was not found.  This may take awhile...", ami);
            //Identifier amiId = Identifier.fromCompoundString(ami);
            try (InputStream imageStream = dockerClient.pullImageCmd(ami).exec()) {
                int streamValue = 0;
                while (streamValue != -1) {
                    streamValue = imageStream.read();
                }
            }

            LOGGER.log(Level.INFO, "Finished pulling image \"{0}\"", ami);
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
     *
     */
    private synchronized boolean addProvisionedSlave(String ami, int amiCap) throws Exception {
        int estimatedTotalSlaves = countCurrentDockerSlaves(null);
        int estimatedAmiSlaves = countCurrentDockerSlaves(ami);

        synchronized (provisioningAmis) {
            int currentProvisioning;

            for (int amiCount : provisioningAmis.values()) {
                estimatedTotalSlaves += amiCount;
            }
            try {
                currentProvisioning = provisioningAmis.get(ami);
            }
            catch (NullPointerException npe) {
                currentProvisioning = 0;
            }

            estimatedAmiSlaves += currentProvisioning;

            if(estimatedTotalSlaves >= containerCap) {
                LOGGER.log(Level.INFO, "Not Provisioning \"{0}\"; Server \"{1}\" full with {2} container(s)", new Object[]{ami,name,containerCap});
                return false;      // maxed out
            }

            if (amiCap != 0 && estimatedAmiSlaves >= amiCap) {
                LOGGER.log(Level.INFO, "Not Provisioning \"{0}\"; Instance limit of {2} reached on server \"{1}\"", new Object[]{ami,name,amiCap});
                return false;      // maxed out
            }

            LOGGER.log(Level.INFO,
                    "Provisioning \"{0}\" number {2} on \"{1}\"; Total containers: {3}",
                    new Object[]{ami,name,estimatedAmiSlaves,estimatedTotalSlaves}
            );

            provisioningAmis.put(ami, currentProvisioning + 1);
            return true;
        }
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
                URL url = new URL(serverUrl); // exclude page fail by creating URL obj in try
                DockerClientConfig.DockerClientConfigBuilder config = DockerClientConfig
                        .createDefaultConfigBuilder()
                        .withUri(url.toString());

                if (!Strings.isNullOrEmpty(version)) {
                    config.withVersion(version);
                }

                addCredentials(config, credentialsId);

                DockerClient dc = DockerClientBuilder.getInstance(config.build()).withDockerCmdExecFactory(new DockerCmdExecFactoryImpl()).build();

                Version v = dc.versionCmd().exec();

                return FormValidation.ok("Version = " + v.getVersion());
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
    }

    public static class CredentialsListBoxModel
        extends AbstractIdCredentialsListBoxModel<CredentialsListBoxModel, StandardCertificateCredentials> {

        /**
         * {@inheritDoc}
         */
        @NonNull
        protected String describe(@NonNull StandardCertificateCredentials c) {
            return CredentialsNameProvider.name(c);
        }
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", name)
                .add("serverUrl", serverUrl)
                .toString();
    }
}
