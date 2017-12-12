package io.jenkins.docker.pipeline;

import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Computer;
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
        final TaskListener listener = getContext().get(TaskListener.class);
        listener.getLogger().println("Launching new docker node based on " + image);
        Computer.threadPoolForRemoting.submit(() -> createNode(listener));
        return false;
    }

    private void createNode(TaskListener listener) {

        final String uuid = UUID.randomUUID().toString();

        final DockerTemplate t = new DockerTemplate(
                new DockerTemplateBase(image),
                new DockerComputerAttachConnector(),
                uuid, remoteFs, "1");

        t.setMode(Node.Mode.EXCLUSIVE); // Doing this we enforce no other task will use this agent

        final DockerAPI api = new DockerAPI(new DockerServerEndpoint(dockerHost, credentialsId));

        Node slave;
        Computer computer;
        EnvVars env;
        FilePath ws;
        try {
            // TODO extract the "pull" process into a new pipeline step so this one is resumable
            slave = t.provisionNode(api, listener);
            Jenkins.getInstance().addNode(slave);

            // FIXME we need to wait for node to be online ...
            listener.getLogger().println("Waiting for node to be online ...");
            while ((computer = slave.toComputer()) == null || computer.isOffline()) {
                Thread.sleep(1000);
            }
            listener.getLogger().println("Node " + slave.getNodeName() + " is online.");

            // TODO workspace should be a volume
            ws = slave.createPath(remoteFs + "/workspace");
            FlowNode flowNode = getContext().get(FlowNode.class);
            flowNode.addAction(new WorkspaceActionImpl(ws, flowNode));

            env = computer.getEnvironment();
            env.overrideExpandingAll(computer.buildEnvironment(listener));
            env.put("NODE_NAME", computer.getName());
            env.put("EXECUTOR_NUMBER", "0");
            env.put("NODE_LABELS", Util.join(slave.getAssignedLabels(), " "));
            env.put("WORKSPACE", ws.getRemote());
        } catch (Exception e) {
            getContext().onFailure(e);
            return;
        }

        getContext().newBodyInvoker().withCallback(new Callback(slave)).withContexts(computer, env, ws).start();
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
                node.terminate(listener);
                Jenkins.getInstance().removeNode(node);
            }
        }
    }
}
