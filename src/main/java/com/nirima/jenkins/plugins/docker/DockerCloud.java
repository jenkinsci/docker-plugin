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
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by magnayn on 08/01/2014.
 */
public class DockerCloud extends Cloud {

    private static final Logger LOGGER = Logger.getLogger(DockerCloud.class.getName());

    public static final String CLOUD_ID_PREFIX = "docker-";

    public final List<DockerTemplate> templates;
    public final String serverUrl;

    private transient DockerClient connection;

    @DataBoundConstructor
    public DockerCloud(String name, List<? extends DockerTemplate> templates, String serverUrl, String instanceCapStr) {
        super(name);
        Preconditions.checkNotNull(serverUrl);

        this.serverUrl = serverUrl;

        if( templates != null )
            this.templates = new ArrayList<DockerTemplate>(templates);
        else
            this.templates = new ArrayList<DockerTemplate>();



        readResolve();
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

        LOGGER.info("Building connection to docker host " + name + " URL " + serverUrl);

        if (connection == null) {
            connection = DockerClient.builder()
                    .withUrl(serverUrl)
                    .build();
        }
        return connection;

    }

    @Override
    public synchronized Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        try {
            // Count number of pending executors from spot requests
        /*    for(EC2SpotSlave n : NodeIterator.nodes(EC2SpotSlave.class)){
                // If the slave is online then it is already counted by Jenkins
                // We only want to count potential additional Spot instance slaves
                if (n.getComputer().isOffline()){
                    DescribeSpotInstanceRequestsRequest dsir =
                            new DescribeSpotInstanceRequestsRequest().withSpotInstanceRequestIds(n.getSpotInstanceRequestId());

                    for(SpotInstanceRequest sir : connect().describeSpotInstanceRequests(dsir).getSpotInstanceRequests()) {
                        // Count Spot requests that are open and still have a chance to be active
                        // A request can be active and not yet registered as a slave. We check above
                        // to ensure only unregistered slaves get counted
                        if(sir.getState().equals("open") || sir.getState().equals("active")){
                            excessWorkload -= n.getNumExecutors();
                        }
                    }
                }
            }
            */
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
                                    //decrementAmiSlaveProvision(t.ami);
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
     * Check not too many already running.
     *
     */
    private synchronized boolean addProvisionedSlave(String image, int amiCap) throws Exception {
        if( amiCap == 0 )
            return true;


        final DockerClient dockerClient = connect();
        final ImageInspectResponse ir = dockerClient.image(image).inspect();

        List<Container> containers = dockerClient.containers().finder().allContainers(false).list();

        Collection<Container> matching = Collections2.filter(containers, new Predicate<Container>() {
            public boolean apply(@Nullable Container container) {
                ContainerInspectResponse cis = dockerClient.container(container.getId()).inspect();
                return (cis.getImage().equalsIgnoreCase(ir.getId()));
            }
        });

        return matching.size() < amiCap;
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