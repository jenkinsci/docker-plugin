package com.nirima.jenkins.plugins.docker;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.github.dockerjava.api.model.Container;

/**
 * A class which stores a one-way association between containers (their
 * identifiers) and names of nodes.
 * 
 * @author eaglerainbow
 *
 */
class ContainerNodeNameMap {
    private final Map<String, String> containerIdNodeNameMap = new HashMap<>();
    private final Set<Container> containerSet = new HashSet<>();

    /**
     * Indicates that the list of containers is known to be incomplete.
     * e.g. not all DockerClouds could be interrogated.
     */
    private boolean containerListIncomplete;

    /**
     * Adds a new mapping between a container and its name of the node, which
     * shall be persisted.
     * 
     * @param container
     *            the container which shall be added.
     * @param nodeName
     *            the name of the name which shall be mapped.
     */
    public void registerMapping(Container container, String nodeName) {
        containerIdNodeNameMap.put(container.getId(), nodeName);
        containerSet.add(container);
    }

    /**
     * Retrieves a name of a node based on the identifier of a container.
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
     * Checks if a given container identifier was registered previously.
     * 
     * @param containerId
     *            the identifier of the container for which the registration shall be checked
     * @return <code>true</code>, if the container identifier was registered before, <code>false</code> otherwise.
     */
    public boolean isContainerIdRegistered(String containerId) {
        return containerIdNodeNameMap.containsKey(containerId);
    }

    /**
     * Retrieves a collection of containers which contains all containers
     * registered in this mapping.
     * 
     * @return a collection of containers, which contains all containers
     *         registered in this mapping.
     */
    public Collection<Container> getAllContainers() {
        return Collections.unmodifiableSet(containerSet);
    }

    /**
     * Merges the current instance with another instance of
     * <code>ContainerNodeNameMapping</code>, returning a new instance of
     * <code>ContainerNodeNameMapping</code> which contains the superset of all
     * mappings.
     * 
     * @param other
     *            The other instance of <code>ContainerNodeNameMapping</code>,
     *            which shall be merged with the current instance.
     * @return the new instance of <code>ContainerNodeNameMapping</code>, which
     *         contains all mappings available to both original instances.
     */
    public ContainerNodeNameMap merge(ContainerNodeNameMap other) {
        ContainerNodeNameMap result = new ContainerNodeNameMap();

        result.containerIdNodeNameMap.putAll(containerIdNodeNameMap);
        result.containerIdNodeNameMap.putAll(other.containerIdNodeNameMap);

        result.containerSet.addAll(containerSet);
        result.containerSet.addAll(other.containerSet);

        return result;
    }

    /**
     * Checks if the container list is known to be incomplete.
     * @return <code>true</code> if the list is known to be incomplete; <code>false</code> otherwise.
     */
    public boolean isContainerListIncomplete() {
        return containerListIncomplete;
    }

    /**
     * Sets the known state of completeness of container list.
     * @param containerListIncomplete the new state of completeness to set.
     */
    public void setContainerListIncomplete(boolean containerListIncomplete) {
        this.containerListIncomplete = containerListIncomplete;
    }
}

