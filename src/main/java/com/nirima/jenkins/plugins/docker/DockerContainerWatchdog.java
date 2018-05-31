package com.nirima.jenkins.plugins.docker;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.nirima.jenkins.plugins.docker.utils.JenkinsUtils;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import io.jenkins.docker.DockerTransientNode;
import jenkins.model.Jenkins;
import jenkins.model.Jenkins.CloudList;

/**
 * Periodic job, which gets executed by Jenkins automatically, to ensure the
 * consistency of the containers currently running on the docker and the slaves
 * which are attached to this Jenkins instance.
 * 
 * @author eaglerainbow
 *
 */

@Extension
public class DockerContainerWatchdog extends AsyncPeriodicWork {
    protected DockerContainerWatchdog(String name) {
        super(name);
    }

    public DockerContainerWatchdog() {
        super("Docker Container Watchdog Asynchronouse Periodic Work");
    }

    private static final long RECURRENCE_PERIOD_IN_MS = 5 * 1000;
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerContainerWatchdog.class);
    
    private HashMap<String, Node> nodeMap;
    
    @Override
    public long getRecurrencePeriod() {
        // value is in ms.
        return RECURRENCE_PERIOD_IN_MS;
    }
    
    /*
     * Methods used for decloupling on unit testing
     */
    
    
    protected CloudList getAllClouds() {
        return Jenkins.getInstance().clouds;
    }
    
    protected List<Node> getAllNodes() {
        return Jenkins.getInstance().getNodes();
    }
    
    protected String getJenkinsInstanceId() {
        return JenkinsUtils.getInstanceId();
    }

    /*
     * Implementation of business logic
     */
    
    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        LOGGER.info("Docker Container Watchdog has been triggered");
        
        this.loadNodeMap();
        
        for (Cloud c : this.getAllClouds()) {
            if (!(c instanceof DockerCloud)) {
                continue;
            }
            DockerCloud dc = (DockerCloud) c;
            LOGGER.info("Checking Docker Cloud '{}'", dc.getDisplayName());
            listener.getLogger().println(String.format("Checking Docker Cloud %s", dc.getDisplayName()));
            
            this.checkCloud(dc);
        }

        LOGGER.info("Docker Container Watchdog check has been completed");
    }
    
    private void loadNodeMap() {
        this.nodeMap = new HashMap<>();
        
        for (Node n : this.getAllNodes()) {
            this.nodeMap.put(n.getNodeName(), n);
        }
        
        LOGGER.info("We currently have {} nodes assigned to this Jenkins instance, which we will check", this.nodeMap.size());
    }

    private void checkCloud(DockerCloud dc) {
        DockerClient client = dc.getDockerApi().getClient();
        
        try {
            ContainerNodeNameMapping csm = this.retrieveContainers(client);
            
            this.cleanupSuperfluousContainers(client, csm);
            
            this.checkForSuperfluousComputer(csm, dc);
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to properly close a DockerClient instance; ignoring", e);
            }
        }
        
    }

    private static class ContainerNodeNameMapping {
        private HashMap<String, String> containerIdNodeNameMap = new HashMap<>();
        private HashMap<String, Container> nodeNameContainerMap = new HashMap<>();
        
        public void registerMapping(Container container, String nodeName) {
            this.containerIdNodeNameMap.put(container.getId(), nodeName);
            this.nodeNameContainerMap.put(nodeName, container);
        }

        public String getNodeName(String containerId) {
            return this.containerIdNodeNameMap.get(containerId);
        }
        
        public Container getContainerByNodeName(String nodeName) {
            return this.nodeNameContainerMap.get(nodeName);
        }
        
        public Collection<Container> getAllContainers() {
            return this.nodeNameContainerMap.values();
        }
    }
    
    private ContainerNodeNameMapping retrieveContainers(DockerClient client) {
        /*
         * Warning!
         * We have a DockerClient which is based on an associated DockerCloud.
         * However, it is not said that each DockerCloud is also referring to a different
         * Docker instance! They may point to the same one, but - for example - just may
         * have different credentials for logging on (or so).
         * 
         * For example, Triton (which has an own implementation of the Docker API, and 
         * we are supporting this here with this plugin) will isolate the containers 
         * then between users.
         * The common community edition of Docker does NOT isolate the containers then. 
         * 
         * This means that we have to correlate each Container uniquely also the 
         * instance of DockerCloud manually.
         */
        Map<String, String> labelFilter = new HashMap<>();
        
        labelFilter.put(DockerTemplateBase.CONTAINER_LABEL_JENKINS_INSTANCE_ID, this.getJenkinsInstanceId());
        
        List<Container> containerList = client.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(labelFilter)
                .exec();
        
        ContainerNodeNameMapping result = new ContainerNodeNameMapping();

        for (Container container : containerList) {
            String containerId = container.getId();
            String status = container.getStatus();
            
            if (status == null) {
                LOGGER.warn("Container {} has a null-status and thus cannot be checked; ignoring container", containerId);
                continue;
            }
            
            InspectContainerResponse icr = client.inspectContainerCmd(containerId).exec();
            Map<String, String> containerLabels = icr.getConfig().getLabels();
            
            String containerNodeName = containerLabels.get(DockerTemplate.CONTAINER_LABEL_NODE_NAME);
            if (containerNodeName == null) {
                LOGGER.warn("Container {} is said to be created by this Jenkins instance, but does not have any node name label assigned; manual cleanup is required for this", containerId);
                continue;
            }
            
            result.registerMapping(container, containerNodeName);
        }
        
        return result;
    }

    private void cleanupSuperfluousContainers(DockerClient client, ContainerNodeNameMapping csm) {
        Collection<Container> allContainers = csm.getAllContainers();
        
        for (Container container : allContainers) {
            String nodeName = csm.getNodeName(container.getId());
            
            Node node = this.nodeMap.get(nodeName);
            if (node != null) {
                // the slave and the container still have a proper mapping => ok
                continue;
            }
            
            // this is a container, which is missing a corresponding node with us
            LOGGER.info("Container {}, which is reported to be assigned to node {}, is no longer associated (slave might be gone already?)", container.getId(), nodeName);
            LOGGER.info("Container {}'s last status is {}; it was created on {}", container.getId(), container.getStatus(), container.getCreated());
            
            
            /*
             * During startup it may happen temporarily that a container exists, but the
             * corresponding node isn't there yet.
             * That is why we have to have a grace period for pulling up containers.
             */
            // TODO see comment above
            client.removeContainerCmd(container.getId());
        }
    }
    
    private void checkForSuperfluousComputer(ContainerNodeNameMapping csm, DockerCloud cloud) {
        for (Node node : nodeMap.values()) {
            if (! (node instanceof DockerTransientNode)) {
                // this slave does not belong to us
                continue;
            }
            
            DockerTransientNode dcn = (DockerTransientNode) node;
            if (!dcn.getCloud().equals(cloud)) {
                // this node does not belong to us
                continue;
            }
            
            Container container = csm.getContainerByNodeName(dcn.getNodeName());
            if (container != null) {
                // the slave and the container still have a proper mapping => ok
                continue;
            }
            
            // the container is already gone for the slave, but the slave did not notice it yet properly
            LOGGER.info("Slave {} reports to have container {} assigned to it, but the container does not exist on docker", dcn.getNodeName(), dcn.getContainerId());
        }
    }


}