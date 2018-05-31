package com.nirima.jenkins.plugins.docker;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;

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
    
}
