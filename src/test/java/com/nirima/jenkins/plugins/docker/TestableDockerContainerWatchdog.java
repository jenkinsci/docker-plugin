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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerConfig;

import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.Descriptor.FormException;
import hudson.slaves.Cloud;
import hudson.slaves.SlaveComputer;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;
import jenkins.model.Jenkins.CloudList;

public class TestableDockerContainerWatchdog extends DockerContainerWatchdog {

    private static final String UNITTEST_JENKINS_ID = "f1b65f06-be3e-4dac-a760-b17e7592570f";
    private List<Node> allNodes;
    private List<Cloud> allClouds;
    private List<DockerTransientNode> allDTNs = new LinkedList<>();
    private List<DockerTransientNode> nodesRemoved = new LinkedList<>();
    
    @Override
    protected CloudList getAllClouds() {
        CloudList cloudList = Mockito.mock(CloudList.class);
        
        Mockito.when(cloudList.iterator()).thenReturn(Collections.unmodifiableList(this.allClouds).iterator());
        
        return cloudList;
    }

    @Override
    protected List<Node> getAllNodes() {
        return this.allNodes;
    }
    
    @Override
    protected DockerTransientNode createDockerTransientNode(String nodeName, String containerId, String remoteFs)
            throws FormException, IOException {
        
        Assert.assertNotNull(nodeName);
        Assert.assertNotNull(containerId);
        DockerTransientNode dtn = Mockito.mock(DockerTransientNode.class);
        
        this.allDTNs.add(dtn);
        
        return dtn;
    }

    @Override
    protected void removeNode(DockerTransientNode dtn) throws IOException {
        this.nodesRemoved.add(dtn);
    }

    @Override
    protected String getJenkinsInstanceId() {
        return UNITTEST_JENKINS_ID;
    }

    public void setAllNodes(List<Node> allNodes) {
        this.allNodes = allNodes;
    }

    public void setAllClouds(List<Cloud> allClouds) {
        this.allClouds = allClouds;
    }
    
    public List<DockerTransientNode> getAllDockerTransientNodes() {
        return Collections.unmodifiableList(this.allDTNs);
    }
    
    public List<DockerTransientNode> getAllRemovedNodes() {
        return Collections.unmodifiableList(this.nodesRemoved);
    }
    
    public void runExecute() throws IOException, InterruptedException {
        TaskListener mockedListener = Mockito.mock(TaskListener.class);
        Mockito.when(mockedListener.getLogger()).thenReturn(System.out);
        
        this.execute(mockedListener);
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
            return this.getContainerId();
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
            return this.inspectFunction.apply(this.containerId);
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
