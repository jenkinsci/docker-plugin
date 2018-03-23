package io.jenkins.docker.pipeline;

import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.console.PlainTextConsoleOutputStream;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;
import io.jenkins.docker.connector.DockerComputerAttachConnector;
import io.jenkins.docker.connector.DockerComputerConnector;
import jenkins.model.Jenkins;
import jenkins.model.NodeListener;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.support.actions.WorkspaceActionImpl;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
class DockerNodeStepExecution extends StepExecution {

    private final String dockerHost;
    private final String credentialsId;
    private final String image;
    private final String remoteFs;
    private final DockerComputerConnector connector;
    private transient volatile CompletableFuture<DockerTransientNode> task;
    private volatile String nodeName;

    public DockerNodeStepExecution(StepContext context, DockerComputerConnector connector, String dockerHost, String credentialsId, String image, String remoteFs) {
        super(context);
        this.connector = connector != null ? connector : new DockerComputerAttachConnector();
        this.dockerHost = dockerHost;
        this.credentialsId = credentialsId;
        this.image = image;
        this.remoteFs = remoteFs;
    }

    @Override
    public boolean start() throws Exception {
        final TaskListener listener = getContext().get(TaskListener.class);
        listener.getLogger().println("Launching new docker node based on " + image);
        task = CompletableFuture.supplyAsync(() -> createNode(listener));
        task.thenAccept(node -> invokeBody(node, listener));
        return false;
    }

    @Override
    public void onResume() {
        try {
            // Pipeline get resumed after jenkins reboot
            if (nodeName == null) {
                start();
            }
        } catch (Exception x) { // JENKINS-40161
            getContext().onFailure(x);
        }
    }

    private DockerTransientNode createNode(TaskListener listener) {

        final String uuid = UUID.randomUUID().toString();

        final DockerTemplate t = new DockerTemplate(
                new DockerTemplateBase(image),
                connector,
                uuid, remoteFs, "1");

        t.setMode(Node.Mode.EXCLUSIVE);

        final DockerAPI api = new DockerAPI(new DockerServerEndpoint(dockerHost, credentialsId));

        DockerTransientNode node;
        Computer computer = null;
        try {
            node = t.provisionNode(api, listener);
            node.setAcceptingTasks(false); // Prevent this node to be used by tasks from build queue
            Jenkins.getInstance().addNode(node);

            listener.getLogger().println("Waiting for node to be online ...");
            // TODO maybe rely on ComputerListener to catch onOnline() event ?
            while ((computer = node.toComputer()) == null || computer.isOffline()) {
                Thread.sleep(1000);
            }
            listener.getLogger().println("Node " + node.getNodeName() + " is online.");
        } catch (Exception e) {
            // Provisioning failed ! capture computer log and dump to pipeline log to assist in diagnostic
            if (computer != null) {
                try {
                    listener.getLogger().write(computer.getLog().getBytes());
                } catch (IOException x) {
                    listener.getLogger().println("Failed to capture docker agent provisioning log " + x);
                }
            }

            getContext().onFailure(e);
            return null;
        }
        return node;
    }


    private void invokeBody(DockerTransientNode node, TaskListener listener) {

        this.nodeName = node.getNodeName();
        FilePath ws = null;
        Computer computer = null;
        EnvVars env = null;
        try {
            // TODO workspace should be a volume
            ws = node.createPath(node.getRemoteFS() + "/workspace");
            FlowNode flowNode = getContext().get(FlowNode.class);
            flowNode.addAction(new WorkspaceActionImpl(ws, flowNode));

            computer = node.toComputer();
            if (computer == null) throw new IllegalStateException("Agent not started");
            env = computer.getEnvironment();
            env.overrideExpandingAll(computer.buildEnvironment(listener));
            env.put("NODE_NAME", computer.getName());
            env.put("EXECUTOR_NUMBER", "0");
            env.put("NODE_LABELS", Util.join(node.getAssignedLabels(), " "));
            env.put("WORKSPACE", ws.getRemote());
        } catch (IOException | InterruptedException e) {
            getContext().onFailure(e);
        }

        getContext().newBodyInvoker().withCallback(new Callback(node)).withContexts(computer, env, ws).start();
    }

    @Override
    public void stop(@Nonnull Throwable cause) throws Exception {
        if (task != null) {
            task.cancel(true);
        }
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
                listener.getLogger().println("Terminating docker node ...");
                node.terminate(listener);
                Jenkins.getInstance().removeNode(node);
            }
        }
    }
}
