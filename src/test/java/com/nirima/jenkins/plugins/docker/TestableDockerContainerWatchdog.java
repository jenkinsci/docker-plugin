package com.nirima.jenkins.plugins.docker;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang.NotImplementedException;
import org.junit.Assert;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerConfig;

import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.SlaveComputer;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;

public class TestableDockerContainerWatchdog extends DockerContainerWatchdog {

    private static final String UNITTEST_JENKINS_ID = "f1b65f06-be3e-4dac-a760-b17e7592570f";
    private List<Node> allNodes;
    private List<DockerCloud> allClouds;
    private List<DockerTransientNode> nodesRemoved = new LinkedList<>();
    private List<String> containersRemoved = new LinkedList<>();
    
    @Override
    protected List<DockerCloud> getAllClouds() {
        return Collections.unmodifiableList(allClouds);
    }

    @Override
    protected List<Node> getAllNodes() {
        return allNodes;
    }
    
    @Override
    protected void removeNode(DockerTransientNode dtn) throws IOException {
        nodesRemoved.add(dtn);
    }

    @Override
    protected String getJenkinsInstanceId() {
        return UNITTEST_JENKINS_ID;
    }

    @Override
    protected boolean stopAndRemoveContainer(DockerAPI dockerApi, Logger logger, String description,
            boolean removeVolumes, String containerId, boolean stop) {
        containersRemoved.add(containerId);
        return true;
    }

    public void setAllNodes(List<Node> allNodes) {
        this.allNodes = allNodes;
    }

    public void setAllClouds(List<DockerCloud> allClouds) {
        this.allClouds = allClouds;
    }
    
    public List<DockerTransientNode> getAllRemovedNodes() {
        return Collections.unmodifiableList(nodesRemoved);
    }
    
    public List<String> getContainersRemoved() {
        return Collections.unmodifiableList(containersRemoved);
    }

    public void runExecute() throws IOException, InterruptedException {
        TaskListener mockedListener = Mockito.mock(TaskListener.class);
        Mockito.when(mockedListener.getLogger()).thenReturn(System.out);
        
        execute(mockedListener);
    }
    
    private static class MockedInspectContainerCmd implements InspectContainerCmd {

        private String containerId;
        private Function<String, InspectContainerResponse> inspectFunction;

        public MockedInspectContainerCmd(Function<String, InspectContainerResponse> inspectFunction) {
            this.inspectFunction = inspectFunction;
        }
        
        @Override
        public void close() {
            // not necessary
        }

        @Override
        public String getContainerId() {
            return getContainerId();
        }

        @Override
        public InspectContainerCmd withContainerId(String containerId) {
            this.containerId = containerId;
            return this;
        }

        @Override
        public InspectContainerCmd withSize(Boolean showSize) {
            throw new NotImplementedException();
        }

        @Override
        public Boolean getSize() {
            throw new NotImplementedException();
        }

        @Override
        public InspectContainerResponse exec() throws NotFoundException {
            return inspectFunction.apply(containerId);
        }
        
    }
    
    public static DockerAPI createMockedDockerAPI(List<Container> containerList, Function<String, InspectContainerResponse> inspectFunction) {
        DockerAPI result = Mockito.mock(DockerAPI.class);
        
        DockerClient client = Mockito.mock(DockerClient.class);
        Mockito.when(result.getClient()).thenReturn(client);
        
        ListContainersCmd listContainerCmd = Mockito.mock(ListContainersCmd.class);
        Mockito.when(client.listContainersCmd()).thenReturn(listContainerCmd);
        
        Mockito.when(listContainerCmd.withShowAll(true)).thenReturn(listContainerCmd);
        Mockito.when(listContainerCmd.withLabelFilter(Mockito.anyMap())).thenAnswer( new Answer<ListContainersCmd>() {

            @Override
            public ListContainersCmd answer(InvocationOnMock invocation) throws Throwable {
                Map<String, String> arg = (Map<String, String>) invocation.getArgumentAt(0, Map.class);
                String jenkinsInstanceIdInFilter = arg.get(DockerTemplateBase.CONTAINER_LABEL_JENKINS_INSTANCE_ID);
                Assert.assertEquals(UNITTEST_JENKINS_ID, jenkinsInstanceIdInFilter);
                
                return listContainerCmd;
            }
            
        
        });
        Mockito.when(listContainerCmd.exec()).thenReturn(containerList);
        
        Mockito.when(client.inspectContainerCmd(Mockito.anyString())).thenAnswer(new Answer<InspectContainerCmd>() {

            @Override
            public InspectContainerCmd answer(InvocationOnMock invocation) throws Throwable {
                String arg = invocation.getArgumentAt(0, String.class);
                
                MockedInspectContainerCmd result = new MockedInspectContainerCmd(inspectFunction);
                return result.withContainerId(arg);
            }
            
        });
        
        return result;
    }
    
    public static Container createMockedContainer(String containerId, String status, long createdOn) {
        Container result = Mockito.mock(Container.class);
        
        Mockito.when(result.getId()).thenReturn(containerId);
        Mockito.when(result.getStatus()).thenReturn(status);
        Mockito.when(result.getCreated()).thenReturn(createdOn);
        
        return result;
    }
    
    public static InspectContainerResponse createMockedInspectContainerResponse(String containerId, Map<String, String> labelMap) {
        InspectContainerResponse result = Mockito.mock(InspectContainerResponse.class);
        
        Mockito.when(result.getId()).thenReturn(containerId);
        
        ContainerConfig config = Mockito.mock(ContainerConfig.class);
        Mockito.when(result.getConfig()).thenReturn(config);
        
        Mockito.when(config.getLabels()).thenReturn(labelMap);
        
        return result;
    }
    
    public static DockerTransientNode createMockedDockerTransientNode(String containerId, String nodeName, DockerCloud cloud, boolean offline) {
        DockerTransientNode result = Mockito.mock(DockerTransientNode.class);
        Mockito.when(result.getContainerId()).thenReturn(containerId);
        Mockito.when(result.getNodeName()).thenReturn(nodeName);
        Mockito.when(result.getCloud()).thenReturn(cloud);
        
        SlaveComputer sc = Mockito.mock(SlaveComputer.class);
        Mockito.when(sc.isOffline()).thenReturn(offline);
        
        Mockito.when(result.getComputer()).thenReturn(sc);
        
        return result;
    }
}
