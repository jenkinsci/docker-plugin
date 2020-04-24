package io.jenkins.docker.pipeline;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;
import io.jenkins.docker.connector.DockerComputerAttachConnector;
import io.jenkins.docker.connector.DockerComputerConnector;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.support.actions.WorkspaceActionImpl;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.IOException;
import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
class DockerNodeStepExecution extends StepExecution {
    private static final long serialVersionUID = 1959552800000929329L;

    /**
     * Default <code>connector</code> used by
     * {@link #DockerNodeStepExecution(StepContext, DockerComputerConnector, String, String, String, String)}
     * if null was provided.
     */
    @Restricted(NoExternalUse.class)
    static final DockerComputerConnector DEFAULT_CONNECTOR = new DockerComputerAttachConnector();

    private final String dockerHost;
    private final String credentialsId;
    private final String image;
    private final String remoteFs;
    /** The {@link DockerComputerConnector} ... which has to be {@link Serializable} too (not all are) */
    private final Serializable connector;
    private transient volatile CompletableFuture<DockerTransientNode> task;
    private volatile String nodeName;

    public DockerNodeStepExecution(StepContext context, @Nullable DockerComputerConnector connector, String dockerHost, String credentialsId, String image, String remoteFs) {
        super(context);
        if( connector!=null ) {
            assertIsSerializableDockerComputerConnector(connector);
            this.connector = (Serializable) connector;
        } else {
            assertIsSerializableDockerComputerConnector(DEFAULT_CONNECTOR);
            this.connector = (Serializable) DEFAULT_CONNECTOR;
        }
        this.dockerHost = dockerHost;
        this.credentialsId = credentialsId;
        this.image = image;
        this.remoteFs = remoteFs;
    }

    /**
     * @throws IllegalArgumentException if given anything other than a
     *                                  {@link DockerComputerConnector} that is also
     *                                  {@link Serializable}.
     */
    @Restricted(NoExternalUse.class)
    static void assertIsSerializableDockerComputerConnector(Object connector) {
        final String whatUserTried = connector.toString();
        final Class<? extends Object> clazz = connector.getClass();
        final String msg = getReasonWhyThisIsNotASerializableDockerComputerConnector(whatUserTried, clazz);
        if (msg != null) {
            throw new IllegalArgumentException(msg);
        }
    }

    @Restricted(NoExternalUse.class)
    static String getReasonWhyThisIsNotASerializableDockerComputerConnector(final String whatUserTried,
            final Class<? extends Object> clazz) {
        final boolean extendsOk = DockerComputerConnector.class.isAssignableFrom(clazz);
        final boolean implementsOk = Serializable.class.isAssignableFrom(clazz);
        if (extendsOk && implementsOk) {
            return null;
        }
        final String msg = whatUserTried + " is not valid."
                + (extendsOk ? "" : (" It does not extend " + DockerComputerConnector.class.getCanonicalName() + "."))
                + (implementsOk ? "" : (" It does not implement " + Serializable.class.getCanonicalName() + "."));
        return msg;
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
                (DockerComputerConnector) connector,
                uuid, remoteFs, "1");
        t.setMode(Node.Mode.EXCLUSIVE);

        final DockerAPI api;
        if (dockerHost == null && credentialsId == null) {
            api = defaultApi();
        } else {
            api = new DockerAPI(new DockerServerEndpoint(dockerHost, credentialsId));
        }

        final DockerTransientNode node;
        Computer computer = null;
        try {
            node = t.provisionNode(api, listener);
            node.setDockerAPI(api);
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
                    final String computerLogAsString = computer.getLog();
                    listener.getLogger().println("Node provisioning failed: " + e);
                    listener.getLogger().println(computerLogAsString);
                    listener.getLogger().println("See log above for details.");
                } catch (IOException x) {
                    listener.getLogger().println("Failed to capture docker agent provisioning log " + x);
                }
            }
            getContext().onFailure(e);
            return null;
        }
        return node;
    }

    private static DockerAPI defaultApi() {
        for (Cloud cloud : Jenkins.getInstance().clouds) {
            if (cloud instanceof DockerCloud) {
                return ((DockerCloud) cloud).getDockerApi();
            }
        }
        throw new IllegalStateException("Must either specify dockerHost/credentialsId, or define at least one Docker cloud");
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
