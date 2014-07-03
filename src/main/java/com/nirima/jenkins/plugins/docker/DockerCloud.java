package com.nirima.jenkins.plugins.docker;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;
import com.nirima.docker.client.DockerException;
import com.nirima.docker.client.model.Container;
import com.nirima.docker.client.model.ContainerInspectResponse;
import com.nirima.docker.client.model.ImageInspectResponse;
import com.nirima.docker.client.model.Version;
import com.nirima.docker.client.model.Image;
import com.nirima.docker.client.DockerClient;
import hudson.Extension;
import hudson.model.*;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
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

    private final int connectTimeout;
    private final int readTimeout;


    private transient DockerClient connection;

    /* Track the count per-AMI identifiers for AMIs currently being
     * provisioned, but not necessarily reported yet by docker.
     */
    private static HashMap<String, Integer> provisioningAmis = new HashMap<String, Integer>();

    @DataBoundConstructor
    public DockerCloud(String name, List<? extends DockerTemplate> templates, String serverUrl, String containerCapStr, int connectTimeout, int readTimeout) {
        super(name);

        Preconditions.checkNotNull(serverUrl);

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
     * @return Docker client.
     */
    public synchronized DockerClient connect() {

        LOGGER.log(Level.FINE, "Building connection to docker host " + name + " URL " + serverUrl);

        if (connection == null) {

            connection = DockerClient.builder()
                    .withUrl(serverUrl)
                    .withLogging(DockerClient.Logging.SLF4J)
                    .connectTimeout(connectTimeout * 1000)
                    .readTimeout(readTimeout*1000)
                    .build();
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
     * Counts the number of instances in Docker currently running that are using the specifed image.
     *
     * @param ami If AMI is left null, then all instances are counted.
     * <p>
     * This includes those instances that may be started outside Hudson.
     */
    public int countCurrentDockerSlaves(String ami) throws Exception {
        final DockerClient dockerClient = connect();

        List<Container> containers = dockerClient.containers().finder().allContainers(false).list();

        if (ami == null)
            return containers.size();

        List<Image> images = dockerClient.images().finder().allImages(true).filter(ami).list();
        LOGGER.log(Level.INFO, "Images found: " + images);

        if (images.size() == 0) {
            LOGGER.log(Level.INFO, "Pulling image " + ami + " since one was not found.  This may take awhile...");
            InputStream imageStream = dockerClient.createPullCommand().image(ami).withTag("latest").execute();
            int streamValue = 0;
            while (streamValue != -1) {
                streamValue = imageStream.read();
            }
            imageStream.close();
            LOGGER.log(Level.INFO, "Finished pulling image " + ami);
        }

        final ImageInspectResponse ir = dockerClient.image(ami).inspect();

        Collection<Container> matching = Collections2.filter(containers, new Predicate<Container>() {
            public boolean apply(@Nullable Container container) {
                ContainerInspectResponse cis = dockerClient.container(container.getId()).inspect();
                return (cis.getImage().equalsIgnoreCase(ir.getId()));
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
                @QueryParameter URL serverUrl
                ) throws IOException, ServletException, DockerException {

            DockerClient dc = DockerClient.builder().withUrl(serverUrl.toString()).build();

            Version version = dc.system().version();

            if( version.getVersionComponents()[0] < 1 )
                return FormValidation.error("Docker host is " + version.getVersion() + " which is not supported.");

            return FormValidation.ok("Version = " + version.getVersion());
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
