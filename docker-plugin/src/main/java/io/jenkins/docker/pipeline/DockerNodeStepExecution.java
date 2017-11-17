package io.jenkins.docker.pipeline;

import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;
import io.jenkins.docker.connector.DockerComputerAttachConnector;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.support.actions.WorkspaceActionImpl;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.UUID;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
class DockerNodeStepExecution extends StepExecution {

    private final String dockerHost;
    private final String credentialsId;
    private final String image;
    private final String remoteFs;

    public DockerNodeStepExecution(StepContext context, String dockerHost, String credentialsId, String image, String remoteFs) {
        super(context);
        this.dockerHost = dockerHost;
        this.credentialsId = credentialsId;
        this.image = image;
        this.remoteFs = remoteFs;
    }

    @Override
    public boolean start() throws Exception {
        TaskListener listener = getContext().get(TaskListener.class);
        final String uuid = UUID.randomUUID().toString();

        final DockerTemplate t = new DockerTemplate(
                new DockerTemplateBase(image),
                uuid, remoteFs, null, "1", Collections.EMPTY_LIST);

        t.setConnector(new DockerComputerAttachConnector());
        t.setMode(Node.Mode.EXCLUSIVE); // Doing this we enforce no other task will use this agent

        // TODO launch asynchronously, not in CPS VM thread
        listener.getLogger().println("Launching new docker node based on " + image);

        final DockerAPI api = new DockerAPI(new DockerServerEndpoint(dockerHost, credentialsId));
        final Node slave = t.provisionNode(listener, api);

        Jenkins.getInstance().addNode(slave);

        // FIXME we need to wait for node to be online ...
        listener.getLogger().println("Waiting for node to be online ...");
        while (slave.toComputer().isOffline()) {
            Thread.sleep(1000);
        }
        listener.getLogger().println("Node " + slave.getNodeName() + " is online.");

        final FilePath ws = slave.createPath(remoteFs + "/workspace");
        FlowNode flowNode = getContext().get(FlowNode.class);
        flowNode.addAction(new WorkspaceActionImpl(ws, flowNode));
        getContext().newBodyInvoker().withCallback(new Callback(slave)).withContexts(slave.toComputer(), ws).start();

        return false;
    }

    @Override
    public void stop(@Nonnull Throwable cause) throws Exception {

    }

    private static class Callback extends BodyExecutionCallback.TailCall {

        private final String nodeName;

        public Callback(Node node) {
            this.nodeName = node.getNodeName();
        }

        @Override
        protected void finished(StepContext context) throws Exception {
            final DockerTransientNode node = (DockerTransientNode) Jenkins.getInstance().getNode(nodeName);
            if (node != null) {
                TaskListener listener = context.get(TaskListener.class);
                listener.getLogger().println("Waiting for node to be online ...");
                node.terminate(listener, null);
                Jenkins.getInstance().removeNode(node);
            }
        }
    }
}
