package com.nirima.jenkins.plugins.docker;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.github.dockerjava.api.model.Container;

import hudson.model.Node;
import hudson.model.Descriptor.FormException;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;

public class DockerContainerWatchdogTest {
    @Test
    public void testEmptyEnvironment() throws IOException, InterruptedException {
        TestableDockerContainerWatchdog subject = new TestableDockerContainerWatchdog();
        
        subject.setAllNodes(new LinkedList<Node>());
        subject.setAllClouds(new LinkedList<DockerCloud>());
        
        subject.runExecute();
        
        Assert.assertEquals(0, subject.getAllRemovedNodes().size());
    }

    @Test
    public void testSimpleEnvironmentNothingTodo() throws IOException, InterruptedException, FormException {
        TestableDockerContainerWatchdog subject = new TestableDockerContainerWatchdog();

        final String nodeName = "unittest-12345";
        final String containerId = UUID.randomUUID().toString();
        
        /* setup of cloud */
        List<DockerCloud> listOfCloud = new LinkedList<DockerCloud>();

        Map<String, String> labelMap = new HashMap<>();
        labelMap.put(DockerTemplate.CONTAINER_LABEL_NODE_NAME, nodeName);
        
        List<Container> containerList = new LinkedList<Container>();
        Container c = TestableDockerContainerWatchdog.createMockedContainer(containerId, "Running", 0L, labelMap);
        containerList.add(c);
        
        DockerAPI dockerApi = TestableDockerContainerWatchdog.createMockedDockerAPI(containerList);
        DockerCloud cloud = new DockerCloud("unittestcloud", dockerApi, new LinkedList<DockerTemplate>());
        listOfCloud.add(cloud);
        
        subject.setAllClouds(listOfCloud);

        /* setup of nodes */
        LinkedList<Node> allNodes = new LinkedList<Node>();
        
        DockerTransientNode node = TestableDockerContainerWatchdog.createMockedDockerTransientNode(containerId, nodeName, cloud, false);
        allNodes.add(node);
        
        subject.setAllNodes(allNodes);

        subject.runExecute();
        
        Assert.assertEquals(0, subject.getAllRemovedNodes().size());
    }
    
    @Test
    public void testContainerExistsButSlaveIsMissing() throws IOException, InterruptedException, FormException {
        TestableDockerContainerWatchdog subject = new TestableDockerContainerWatchdog();

        final String nodeName = "unittest-12345";
        final String containerId = UUID.randomUUID().toString();
        
        /* setup of cloud */
        List<DockerCloud> listOfCloud = new LinkedList<DockerCloud>();
        
        Map<String, String> labelMap = new HashMap<>();
        labelMap.put(DockerTemplate.CONTAINER_LABEL_NODE_NAME, nodeName);
        labelMap.put(DockerTemplate.CONTAINER_LABEL_TEMPLATE_NAME, "unittesttemplate");
        
        List<Container> containerList = new LinkedList<Container>();
        Container c = TestableDockerContainerWatchdog.createMockedContainer(containerId, "Running", 0L, labelMap);
        containerList.add(c);
        
        DockerAPI dockerApi = TestableDockerContainerWatchdog.createMockedDockerAPI(containerList);
        DockerCloud cloud = new DockerCloud("unittestcloud", dockerApi, new LinkedList<DockerTemplate>());
        listOfCloud.add(cloud);
        
        subject.setAllClouds(listOfCloud);

        /* setup of nodes */
        LinkedList<Node> allNodes = new LinkedList<Node>();
        subject.setAllNodes(allNodes);

        subject.runExecute();
        
        Assert.assertEquals(0, subject.getAllRemovedNodes().size());
        
        List<String> containersRemoved = subject.getContainersRemoved();
        Assert.assertEquals(1, containersRemoved.size());
        Assert.assertEquals(containerId, containersRemoved.get(0));
    }
    
    @Test
    public void testContainerExistsButSlaveIsMissingWrongNodeNameIsIgnored() throws IOException, InterruptedException, FormException {
        TestableDockerContainerWatchdog subject = new TestableDockerContainerWatchdog();

        final String nodeName = "unittest-12345";
        final String containerId = UUID.randomUUID().toString();
        
        /* setup of cloud */
        List<DockerCloud> listOfCloud = new LinkedList<DockerCloud>();

        Map<String, String> labelMap = new HashMap<>();
        labelMap.put(DockerTemplate.CONTAINER_LABEL_NODE_NAME, nodeName);
        labelMap.put(DockerTemplate.CONTAINER_LABEL_TEMPLATE_NAME, "unittesttemplate");

        List<Container> containerList = new LinkedList<Container>();
        Container c = TestableDockerContainerWatchdog.createMockedContainer(containerId, "Running", 0L, labelMap);
        containerList.add(c);
        
        DockerAPI dockerApi = TestableDockerContainerWatchdog.createMockedDockerAPI(containerList);
        DockerCloud cloud = new DockerCloud("unittestcloud", dockerApi, new LinkedList<DockerTemplate>());
        listOfCloud.add(cloud);
        
        subject.setAllClouds(listOfCloud);

        /* setup of nodes */
        LinkedList<Node> allNodes = new LinkedList<Node>();
        
        Node n = TestableDockerContainerWatchdog.createMockedDockerTransientNode(UUID.randomUUID().toString(), "unittest-other", cloud, false);
        allNodes.add(n);
        
        subject.setAllNodes(allNodes);

        subject.runExecute();
        
        List<String> containersRemoved = subject.getContainersRemoved();
        Assert.assertEquals(1, containersRemoved.size());
        Assert.assertEquals(containerId, containersRemoved.get(0));
        
        Assert.assertEquals(0, subject.getAllRemovedNodes().size());
    }
    
    @Test
    public void testContainerExistsButSlaveIsMissingTwoClouds() throws IOException, InterruptedException, FormException {
        TestableDockerContainerWatchdog subject = new TestableDockerContainerWatchdog();

        final String nodeName1 = "unittest-12345";
        final String containerId1 = "12345f63-cce3-4188-82dc-451b444daa40";
        
        final String nodeName2 = "unittest-12346";
        final String containerId2 = "12346f63-cce3-4188-82dc-451b444daa40";
        
        /* setup of cloud */
        List<DockerCloud> listOfCloud = new LinkedList<DockerCloud>();
        
        List<Container> containerList = new LinkedList<Container>();

        Map<String, String> labelMap = new HashMap<>();
        labelMap.put(DockerTemplate.CONTAINER_LABEL_NODE_NAME, nodeName1);
        labelMap.put(DockerTemplate.CONTAINER_LABEL_TEMPLATE_NAME, "unittestTemplate");

        
        Container c = TestableDockerContainerWatchdog.createMockedContainer(containerId1, "Running", 0L, labelMap);
        containerList.add(c);
        
        labelMap = new HashMap<>();
        labelMap.put(DockerTemplate.CONTAINER_LABEL_NODE_NAME, nodeName2);
        labelMap.put(DockerTemplate.CONTAINER_LABEL_TEMPLATE_NAME, "unittestTemplate");
        
        c = TestableDockerContainerWatchdog.createMockedContainer(containerId2, "Running", 0L, labelMap);
        containerList.add(c);
        
        DockerAPI dockerApi = TestableDockerContainerWatchdog.createMockedDockerAPI(containerList);
        DockerCloud cloud1 = new DockerCloud("unittestcloud1", dockerApi, new LinkedList<DockerTemplate>());
        listOfCloud.add(cloud1);

        DockerCloud cloud2 = new DockerCloud("unittestcloud2", dockerApi, new LinkedList<DockerTemplate>());
        listOfCloud.add(cloud2);
        
        subject.setAllClouds(listOfCloud);

        /* setup of nodes */
        LinkedList<Node> allNodes = new LinkedList<Node>();
        subject.setAllNodes(allNodes);

        subject.runExecute();
        
        List<String> containersRemoved = subject.getContainersRemoved();
        Assert.assertEquals(4, containersRemoved.size());
        
        int countContainer1 = 0;
        int countContainer2 = 0;
        for (String containerId : containersRemoved) {
            if (containerId.equals(containerId1)) {
                countContainer1++;
            } else if (containerId.equals(containerId2)) {
                countContainer2++;
            } else {
                Assert.fail("Unknown container identifier");
            }
        }
        
        Assert.assertEquals(2, countContainer1);
        Assert.assertEquals(2, countContainer2);
        
        /* NB: Why 2 here?
         * keep in mind that the same containers are associated with the same DockerClient.
         * Thus, the same containers also appear twice to our subject - and thus will send the termination
         * requests twice.
         * 
         * Note that this is an acceptable real-life behavior, which is uncommon, though.
         */
        
        Assert.assertEquals(0, subject.getAllRemovedNodes().size());
    }
    
    @Test
    public void testContainerExistsButSlaveIsMissingWithTemplate() throws IOException, InterruptedException, FormException {
        TestableDockerContainerWatchdog subject = new TestableDockerContainerWatchdog();

        final String nodeName = "unittest-12345";
        final String containerId = UUID.randomUUID().toString();
        
        /* setup of cloud */
        List<DockerCloud> listOfCloud = new LinkedList<DockerCloud>();

        Map<String, String> labelMap = new HashMap<>();
        labelMap.put(DockerTemplate.CONTAINER_LABEL_NODE_NAME, nodeName);
        labelMap.put(DockerTemplate.CONTAINER_LABEL_TEMPLATE_NAME, "unittesttemplate");
        
        List<Container> containerList = new LinkedList<Container>();
        Container c = TestableDockerContainerWatchdog.createMockedContainer(containerId, "Running", 0L, labelMap);
        containerList.add(c);
        
        DockerAPI dockerApi = TestableDockerContainerWatchdog.createMockedDockerAPI(containerList);
        DockerCloud cloud = new DockerCloud("unittestcloud", dockerApi, new LinkedList<DockerTemplate>());
        listOfCloud.add(cloud);
        
        DockerTemplate template = Mockito.mock(DockerTemplate.class);
        Mockito.when(template.getName()).thenReturn("unittesttemplate");
        Mockito.when(template.isRemoveVolumes()).thenReturn(true);
        cloud.addTemplate(template);
        
        subject.setAllClouds(listOfCloud);

        /* setup of nodes */
        LinkedList<Node> allNodes = new LinkedList<Node>();
        subject.setAllNodes(allNodes);

        subject.runExecute();
        
        Mockito.verify(dockerApi.getClient(), Mockito.times(0)).removeContainerCmd(containerId); // enforced termination shall not happen
        Mockito.verify(template, Mockito.times(1)).isRemoveVolumes(); // value shall have been read
        
        Assert.assertEquals(0, subject.getAllRemovedNodes().size());
        
        List<String> containersRemoved = subject.getContainersRemoved();
        Assert.assertEquals(1, containersRemoved.size());
        
        Assert.assertEquals(containerId, containersRemoved.get(0));
    }
    
    @Test
    public void testContainerExistsButSlaveIsMissingTooEarly() throws IOException, InterruptedException, FormException {
        TestableDockerContainerWatchdog subject = new TestableDockerContainerWatchdog();
        
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1527970544000L), ZoneId.of("UTC"));
        subject.setClock(clock);

        final String nodeName = "unittest-12345";
        final String containerId = UUID.randomUUID().toString();
        
        /* setup of cloud */
        List<DockerCloud> listOfCloud = new LinkedList<DockerCloud>();

        Map<String, String> labelMap = new HashMap<>();
        labelMap.put(DockerTemplate.CONTAINER_LABEL_NODE_NAME, nodeName);
        labelMap.put(DockerTemplate.CONTAINER_LABEL_TEMPLATE_NAME, "unittesttemplate");

        List<Container> containerList = new LinkedList<Container>();
        Container c = TestableDockerContainerWatchdog.createMockedContainer(containerId, "Running", clock.instant().toEpochMilli() / 1000, labelMap);
        containerList.add(c);
        
        DockerAPI dockerApi = TestableDockerContainerWatchdog.createMockedDockerAPI(containerList);
        DockerCloud cloud = new DockerCloud("unittestcloud", dockerApi, new LinkedList<DockerTemplate>());
        listOfCloud.add(cloud);
        
        subject.setAllClouds(listOfCloud);

        /* setup of nodes */
        LinkedList<Node> allNodes = new LinkedList<Node>();
        subject.setAllNodes(allNodes);

        subject.runExecute();
        
        // shall not have called to remove it by force
        Mockito.verify(dockerApi.getClient(), Mockito.times(0)).removeContainerCmd(containerId);
        
        // ... and shall not have called to remove it gracefully
        Assert.assertEquals(0, subject.getContainersRemoved().size());
        
        // but, if we turn back time a little... 
        subject.setClock(Clock.offset(clock, Duration.ofMinutes(5)));
        
        subject.runExecute();
        
        // ... then it should work
        List<String> containersRemoved = subject.getContainersRemoved();
        Assert.assertEquals(1, containersRemoved.size());
        Assert.assertEquals(containerId, containersRemoved.get(0));
    }
    
    @Test
    public void testSlaveExistsButNoContainer() throws IOException, InterruptedException, FormException {
        TestableDockerContainerWatchdog subject = new TestableDockerContainerWatchdog();

        final String nodeName = "unittest-78901";
        final String containerId = UUID.randomUUID().toString();
        
        /* setup of cloud */
        List<DockerCloud> listOfCloud = new LinkedList<DockerCloud>();

        List<Container> containerList = new LinkedList<Container>();
        
        DockerAPI dockerApi = TestableDockerContainerWatchdog.createMockedDockerAPI(containerList);
        DockerCloud cloud = new DockerCloud("unittestcloud", dockerApi, new LinkedList<DockerTemplate>());
        listOfCloud.add(cloud);
        
        subject.setAllClouds(listOfCloud);

        /* setup of nodes */
        LinkedList<Node> allNodes = new LinkedList<Node>();
        
        DockerTransientNode node = TestableDockerContainerWatchdog.createMockedDockerTransientNode(containerId, nodeName, cloud, true);
        allNodes.add(node);
        
        subject.setAllNodes(allNodes);

        subject.runExecute();
        
        List<DockerTransientNode> nodes = subject.getAllRemovedNodes();
        Assert.assertEquals(1, nodes.size());
        
        DockerTransientNode removedNode = nodes.get(0);
        Assert.assertEquals(node, removedNode);
    }
}
