package com.nirima.jenkins.plugins.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dockerjava.api.model.Container;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ContainerNodeNameMapTest {

    private static Container mockContainer(String containerId) {
        Container container = Mockito.mock(Container.class);
        Mockito.when(container.getId()).thenReturn(containerId);
        return container;
    }

    @Test
    void mergeOfIncompleteWithCompleteIsIncomplete() {
        ContainerNodeNameMap incomplete = new ContainerNodeNameMap();
        incomplete.setContainerListIncomplete(true);
        ContainerNodeNameMap complete = new ContainerNodeNameMap();

        ContainerNodeNameMap merged = incomplete.merge(complete);

        assertTrue(merged.isContainerListIncomplete());
    }

    @Test
    void mergeOfCompleteWithIncompleteIsIncomplete() {
        ContainerNodeNameMap complete = new ContainerNodeNameMap();
        ContainerNodeNameMap incomplete = new ContainerNodeNameMap();
        incomplete.setContainerListIncomplete(true);

        ContainerNodeNameMap merged = complete.merge(incomplete);

        assertTrue(merged.isContainerListIncomplete());
    }

    @Test
    void mergeOfCompleteWithCompleteIsComplete() {
        ContainerNodeNameMap complete1 = new ContainerNodeNameMap();
        ContainerNodeNameMap complete2 = new ContainerNodeNameMap();

        ContainerNodeNameMap merged = complete1.merge(complete2);

        assertFalse(merged.isContainerListIncomplete());
    }

    @Test
    void mergeCombinesContainerIdsAndNodeNamesFromBothSides() {
        final String containerId1 = UUID.randomUUID().toString();
        final String nodeName1 = "unittest-node-1";
        final String containerId2 = UUID.randomUUID().toString();
        final String nodeName2 = "unittest-node-2";

        ContainerNodeNameMap left = new ContainerNodeNameMap();
        Container container1 = mockContainer(containerId1);
        left.registerMapping(container1, nodeName1);

        ContainerNodeNameMap right = new ContainerNodeNameMap();
        Container container2 = mockContainer(containerId2);
        right.registerMapping(container2, nodeName2);

        ContainerNodeNameMap merged = left.merge(right);

        assertTrue(merged.isContainerIdRegistered(containerId1));
        assertTrue(merged.isContainerIdRegistered(containerId2));
        assertEquals(nodeName1, merged.getNodeName(containerId1));
        assertEquals(nodeName2, merged.getNodeName(containerId2));
        assertEquals(2, merged.getAllContainers().size());
        assertTrue(merged.getAllContainers().containsAll(List.of(container1, container2)));
    }
}
