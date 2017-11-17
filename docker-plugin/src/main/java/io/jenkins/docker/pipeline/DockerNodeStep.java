package io.jenkins.docker.pipeline;

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerNodeStep extends Step {

    private String dockerHost;

    private String credentialsId;

    private String image;

    private String remoteFs;

    @DataBoundConstructor
    public DockerNodeStep(String dockerHost, String credentialsId, String image, String remoteFs) {
        this.dockerHost = dockerHost;
        this.credentialsId = credentialsId;
        this.image = image;
        this.remoteFs = remoteFs;
    }

    public String getDockerHost() {
        return dockerHost;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getImage() {
        return image;
    }

    public String getRemoteFs() {
        return remoteFs;
    }


    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new DockerNodeStepExecution(context, dockerHost, credentialsId, image, remoteFs);
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "dockerNode";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker Node";
        }


        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, FlowNode.class);
        }

        @Override public Set<? extends Class<?>> getProvidedContext() {
            // TODO can/should we provide Executor? We cannot access Executor.start(WorkUnit) from outside the package. cf. isAcceptingTasks, withContexts
            return ImmutableSet.of(Computer.class, FilePath.class, /* DefaultStepContext infers from Computer: */ Node.class, Launcher.class);
        }
    }

}
