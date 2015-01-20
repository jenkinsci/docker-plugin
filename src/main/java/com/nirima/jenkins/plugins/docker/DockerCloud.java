package com.nirima.jenkins.plugins.docker;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;

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

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
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
    private static HashMap<String, Integer> provisioningAmis = new HashMap<String, Integer>();

    @DataBoundConstructor
    public DockerCloud(String name, List<? extends DockerTemplate> templates, String serverUrl, String containerCapStr, int connectTimeout, int readTimeout, String credentialsId, String version) {
        super(name);

        Preconditions.checkNotNull(serverUrl);
        this.version = version;
        this.credentialsId = credentialsId;
        this.serverUrl = serverUrl;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        if( templates != null )
            this.templates = new ArrayList<DockerTemplate>(templates);
        else
            this.templates = new ArrayList<DockerTemplate>();

        if(containerCapStr.equals("")) {
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
        DockerClientConfig.DockerClientConfigBuilder config = DockerClientConfig
            .createDefaultConfigBuilder()
            .withUri(serverUrl);

        if( !Strings.isNullOrEmpty(version)) {
            config.withVersion(version);
        }

        addCredentials(config, credentialsId);


        // TODO?
        // .withLogging(DockerClient.Logging.SLF4J);

        //if (connectTimeout > 0)
        //    builder.connectTimeout(connectTimeout * 1000);

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
            CredentialsProvider
                .lookupCredentials(Credentials.class, Jenkins.getInstance(),
                                   ACL.SYSTEM
                                   ),
            CredentialsMatchers.withId(credentialsId)
        );
    }

    private DockerClient buildConnection() {
        LOGGER.log(Level.FINE, "Building connection to docker host " + name + " URL " + serverUrl);

        return DockerClientBuilder.getInstance(getDockerClientConfig()).build();
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

            LOGGER.log(Level.INFO, "Excess workload after pending Spot instances: " + excessWorkload);

            List<NodeProvisioner.PlannedNode> r = new ArrayList<NodeProvisioner.PlannedNode>();

            final DockerTemplate t = getTemplate(label);

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
                                    Jenkins.getInstance().addNode(slave);
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
            LOGGER.log(Level.WARNING,"Failed to count the # of live instances on Docker",e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean canProvision(Label label) {
        return getTemplate(label)!=null;
    }

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
    public int countCurrentDockerSlaves(String ami) throws Exception {
        final DockerClient dockerClient = connect();

        List<Container> containers = dockerClient.listContainersCmd().exec();

        if (ami == null)
            return containers.size();

        String theFilter = "{\"RepoTags\":[\"" + ami + "\"]}";

        List<Image> images = dockerClient.listImagesCmd()
            .withFilters(theFilter)
            .exec();


        LOGGER.log(Level.INFO, "Images found: " + images);

        if (images.size() == 0) {
            LOGGER.log(Level.INFO, "Pulling image " + ami + " since one was not found.  This may take awhile...");
            //Identifier amiId = Identifier.fromCompoundString(ami);
            InputStream imageStream = dockerClient.pullImageCmd(ami).exec();
            int streamValue = 0;
            while (streamValue != -1) {
                streamValue = imageStream.read();
            }
            imageStream.close();
            LOGGER.log(Level.INFO, "Finished pulling image " + ami);
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
        if( amiCap == 0 )
            return true;

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
                LOGGER.log(Level.INFO, "Total container cap of " + containerCap +
                        " reached, not provisioning.");
                return false;      // maxed out
            }

            if (estimatedAmiSlaves >= amiCap) {
                LOGGER.log(Level.INFO, "AMI Instance cap of " + amiCap +
                        " reached for ami " + ami +
                        ", not provisioning.");
                return false;      // maxed out
            }

            LOGGER.log(Level.INFO,
                    "Provisioning for AMI " + ami + "; " +
                            "Estimated number of total slaves: "
                            + String.valueOf(estimatedTotalSlaves) + "; " +
                            "Estimated number of slaves for ami "
                            + ami + ": "
                            + String.valueOf(estimatedAmiSlaves)
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
                @QueryParameter URL serverUrl,
                @QueryParameter String credentialsId,
                @QueryParameter String version
                ) throws IOException, ServletException, DockerException {

            DockerClientConfig.DockerClientConfigBuilder config = DockerClientConfig
                .createDefaultConfigBuilder()
                .withUri(serverUrl.toString());

            if( !Strings.isNullOrEmpty(version)) {
                config.withVersion(version);
            }

            addCredentials(config, credentialsId);

            DockerClient dc = DockerClientBuilder.getInstance(config.build()).build();

            Version v = dc.versionCmd().exec();

            return FormValidation.ok("Version = " + v.getVersion());
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
