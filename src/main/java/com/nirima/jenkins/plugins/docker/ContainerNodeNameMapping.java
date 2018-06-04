package com.nirima.jenkins.plugins.docker;

import java.util.Collection;
import java.util.HashMap;

import com.github.dockerjava.api.model.Container;

/**
 * A class which stores a two-way association between containers (their
 * identifiers) and names of nodes.
 * 
 * @author eaglerainbow
 *
 */
class ContainerNodeNameMapping {
    private HashMap<String, String> containerIdNodeNameMap = new HashMap<>();
    private HashMap<String, Container> nodeNameContainerMap = new HashMap<>();

    /**
     * adds a new mapping between a container and its name of the node, which
     * shall be persisted.
     * 
     * @param container
     *            the container which shall be added.
     * @param nodeName
     *            the name of the name which shall be mapped.
     */
    public void registerMapping(Container container, String nodeName) {
        containerIdNodeNameMap.put(container.getId(), nodeName);
        nodeNameContainerMap.put(nodeName, container);
    }

    /**
     * retrieves a name of a node based on the identifier of a container.
     * 
     * @param containerId
     *            the container for which the name of the node shall be
     *            determined.
     * @return the name of the node of specified identifier of the container, or
     *         <code>null</code> in case no mapping was registered for that
     *         container.
     */
    public String getNodeName(String containerId) {
        return containerIdNodeNameMap.get(containerId);
    }

    /**
     * checks, if a given container identifier was registered previously.
     * 
     * @param containerId
     *            the identifier of the container for which the registration shall be checked
     * @return <code>true</code>, if the container identifier was registered before, <code>false</code> otherwise.
     */
    public boolean isContainerIdRegistered(String containerId) {
        String nodeName = getNodeName(containerId);
        
        return nodeName != null;
    }

    /**
     * retrieves a collection of containers, which contains all containers
     * registered in this mapping.
     * 
     * @return a collection of containers, which contains all containers
     *         registered in this mapping.
     */
    public Collection<Container> getAllContainers() {
        return nodeNameContainerMap.values();
    }

    /**
     * merges the current instance with another instance of
     * <code>ContainerNodeNameMapping</code>, returning a new instance of
     * <code>ContainerNodeNameMapping</code>, which contains the superset of all
     * mappings.
     * 
     * @param other
     *            The other instance of <code>ContainerNodeNameMapping</code>,
     *            which shall be merged with the current instance.
     * @return the new instance of <code>ContainerNodeNameMapping</code>, which
     *         contains all mappings availalbe to both original instances.
     */
    public ContainerNodeNameMapping merge(ContainerNodeNameMapping other) {
        ContainerNodeNameMapping result = new ContainerNodeNameMapping();

        result.containerIdNodeNameMap = new HashMap<>(containerIdNodeNameMap);
        result.containerIdNodeNameMap.putAll(other.containerIdNodeNameMap);

        result.nodeNameContainerMap = new HashMap<>(nodeNameContainerMap);
        result.nodeNameContainerMap.putAll(other.nodeNameContainerMap);

        return result;
    }
}