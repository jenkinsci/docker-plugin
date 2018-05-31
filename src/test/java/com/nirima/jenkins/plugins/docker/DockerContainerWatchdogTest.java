package com.nirima.jenkins.plugins.docker;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.mockito.Mockito;

import com.github.dockerjava.api.model.Container;

import hudson.model.Node;
import hudson.model.Descriptor.FormException;
import hudson.slaves.Cloud;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;
import jenkins.model.Jenkins.CloudList;

public class DockerContainerWatchdogTest {
    @Test
    public void testEmptyEnvironment() throws IOException, InterruptedException {
        TestableDockerContainerWatchdog subject = new TestableDockerContainerWatchdog();
        
        subject.setAllNodes(new LinkedList<Node>());
        CloudList cloudList = new CloudList();
        subject.setAllClouds(cloudList);
        
        subject.runExecute();
    }

    @Test
    public void testSimpleEnvironmentNothingTodo() throws IOException, InterruptedException, FormException {
        TestableDockerContainerWatchdog subject = new TestableDockerContainerWatchdog();

        final String nodeName = "unittest-12345";
        final String containerId = UUID.randomUUID().toString();
        
        /* setup of cloud */
        List<Cloud> listOfCloud = new LinkedList<Cloud>();
        
        List<Container> containerList = new LinkedList<Container>();
        Container c = TestableDockerContainerWatchdog.createMockedContainer(containerId, "Running");
        containerList.add(c);
        
        DockerAPI dockerApi = TestableDockerContainerWatchdog.createMockedDockerAPI(containerList, cid -> {
            Map<String, String> labelMap = new HashMap<>();
            labelMap.put(DockerTemplate.CONTAINER_LABEL_NODE_NAME, nodeName);
            
            return TestableDockerContainerWatchdog.createMockedInspectContainerResponse(cid, labelMap);
        });
        DockerCloud cloud = new DockerCloud("unittestcloud", dockerApi, new LinkedList<DockerTemplate>());
        listOfCloud.add(cloud);
        
        subject.setAllClouds(listOfCloud);

        /* setup of nodes */
        LinkedList<Node> allNodes = new LinkedList<Node>();
        
        DockerTransientNode node = TestableDockerContainerWatchdog.createMockedDockerTransientNode(containerId, nodeName, cloud);
        allNodes.add(node);
        
        subject.setAllNodes(allNodes);

        subject.runExecute();
    }
    
    @Test
    public void testContainerExistsButSlaveIsMissing() throws IOException, InterruptedException, FormException {
        TestableDockerContainerWatchdog subject = new TestableDockerContainerWatchdog();

        final String nodeName = "unittest-12345";
        final String containerId = UUID.randomUUID().toString();
        
        /* setup of cloud */
        List<Cloud> listOfCloud = new LinkedList<Cloud>();
        
        List<Container> containerList = new LinkedList<Container>();
        Container c = TestableDockerContainerWatchdog.createMockedContainer(containerId, "Running");
        containerList.add(c);
        
        DockerAPI dockerApi = TestableDockerContainerWatchdog.createMockedDockerAPI(containerList, cid -> {
            Map<String, String> labelMap = new HashMap<>();
            labelMap.put(DockerTemplate.CONTAINER_LABEL_NODE_NAME, nodeName);
            
            return TestableDockerContainerWatchdog.createMockedInspectContainerResponse(cid, labelMap);
        });
        DockerCloud cloud = new DockerCloud("unittestcloud", dockerApi, new LinkedList<DockerTemplate>());
        listOfCloud.add(cloud);
        
        subject.setAllClouds(listOfCloud);

        /* setup of nodes */
        LinkedList<Node> allNodes = new LinkedList<Node>();
        subject.setAllNodes(allNodes);

        subject.runExecute();
        
        Mockito.verify(dockerApi.getClient(), Mockito.times(1)).removeContainerCmd(containerId);
    }
    
    @Test
    public void testContainerExistsButSlaveIsMissingWrongNodeNameIsIgnored() throws IOException, InterruptedException, FormException {
        TestableDockerContainerWatchdog subject = new TestableDockerContainerWatchdog();

        final String nodeName = "unittest-12345";
        final String containerId = UUID.randomUUID().toString();
        
        /* setup of cloud */
        List<Cloud> listOfCloud = new LinkedList<Cloud>();
        
        List<Container> containerList = new LinkedList<Container>();
        Container c = TestableDockerContainerWatchdog.createMockedContainer(containerId, "Running");
        containerList.add(c);
        
        DockerAPI dockerApi = TestableDockerContainerWatchdog.createMockedDockerAPI(containerList, cid -> {
            Map<String, String> labelMap = new HashMap<>();
            labelMap.put(DockerTemplate.CONTAINER_LABEL_NODE_NAME, nodeName);
            
            return TestableDockerContainerWatchdog.createMockedInspectContainerResponse(cid, labelMap);
        });
        DockerCloud cloud = new DockerCloud("unittestcloud", dockerApi, new LinkedList<DockerTemplate>());
        listOfCloud.add(cloud);
        
        subject.setAllClouds(listOfCloud);

        /* setup of nodes */
        LinkedList<Node> allNodes = new LinkedList<Node>();
        
        Node n = TestableDockerContainerWatchdog.createMockedDockerTransientNode(UUID.randomUUID().toString(), "unittest-other", cloud);
        allNodes.add(n);
        
        subject.setAllNodes(allNodes);

        subject.runExecute();
        
        Mockito.verify(dockerApi.getClient(), Mockito.times(1)).removeContainerCmd(containerId);
    }
    
    @Test
    public void testContainerExistsButSlaveIsMissingTwoClouds() throws IOException, InterruptedException, FormException {
        TestableDockerContainerWatchdog subject = new TestableDockerContainerWatchdog();

        final String nodeName1 = "unittest-12345";
        final String containerId1 = "12345f63-cce3-4188-82dc-451b444daa40";
        
        final String nodeName2 = "unittest-12346";
        final String containerId2 = "12346f63-cce3-4188-82dc-451b444daa40";
        
        /* setup of cloud */
        List<Cloud> listOfCloud = new LinkedList<Cloud>();
        
        List<Container> containerList = new LinkedList<Container>();
        Container c = TestableDockerContainerWatchdog.createMockedContainer(containerId1, "Running");
        containerList.add(c);
        
        DockerAPI dockerApi = TestableDockerContainerWatchdog.createMockedDockerAPI(containerList, cid -> {
            Map<String, String> labelMap = new HashMap<>();
            if (cid.equals(containerId1)) {
                labelMap.put(DockerTemplate.CONTAINER_LABEL_NODE_NAME, nodeName1);
            } else if (cid.equals(containerId2)) {
                labelMap.put(DockerTemplate.CONTAINER_LABEL_NODE_NAME, nodeName2);
            }
            
            return TestableDockerContainerWatchdog.createMockedInspectContainerResponse(cid, labelMap);
        });
        DockerCloud cloud1 = new DockerCloud("unittestcloud1", dockerApi, new LinkedList<DockerTemplate>());
        listOfCloud.add(cloud1);

        DockerCloud cloud2 = new DockerCloud("unittestcloud2", dockerApi, new LinkedList<DockerTemplate>());
        listOfCloud.add(cloud2);
        
        subject.setAllClouds(listOfCloud);

        /* setup of nodes */
        LinkedList<Node> allNodes = new LinkedList<Node>();
        subject.setAllNodes(allNodes);

        subject.runExecute();
        
        Mockito.verify(dockerApi.getClient(), Mockito.times(1)).removeContainerCmd(containerId1);
        Mockito.verify(dockerApi.getClient(), Mockito.times(1)).removeContainerCmd(containerId2);
    }
}