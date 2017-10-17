package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Identifier;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.core.NameParser;
import com.github.dockerjava.core.command.PushImageResultCallback;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.nirima.jenkins.plugins.docker.action.DockerBuildAction;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.nirima.jenkins.plugins.docker.utils.LogUtils.printResponseItemToListener;
import static org.apache.commons.lang.StringUtils.isEmpty;


public class DockerSlave extends AbstractCloudSlave {
    private static final Logger LOGGER = Logger.getLogger(DockerSlave.class.getName());

    public DockerTemplate dockerTemplate;

    // remember container id
    @CheckForNull private String containerId;

    // remember cloud name
    @CheckForNull private String cloudId;

    private transient Run theRun;

    public DockerSlave(DockerTemplate dockerTemplate, String containerId,
                       String name, String nodeDescription,
                       String remoteFS, int numExecutors, Mode mode,
                       String labelString, ComputerLauncher launcher,
                       RetentionStrategy retentionStrategy,
                       List<? extends NodeProperty<?>> nodeProperties)
            throws Descriptor.FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);
        Preconditions.checkNotNull(dockerTemplate);
        Preconditions.checkNotNull(containerId);

        setDockerTemplate(dockerTemplate);
        this.containerId = containerId;
    }

    public DockerSlave(String slaveName, String nodeDescription, ComputerLauncher launcher, String containerId,
                       DockerTemplate dockerTemplate, String cloudId)
            throws IOException, Descriptor.FormException {
        super(slaveName,
                nodeDescription, //description
                dockerTemplate.getRemoteFs(),
                dockerTemplate.getNumExecutors(),
                dockerTemplate.getMode(),
                dockerTemplate.getLabelString(),
                launcher,
                dockerTemplate.getRetentionStrategyCopy(),
                dockerTemplate.getNodeProperties()
        );
        setContainerId(containerId);
        setDockerTemplate(dockerTemplate);
        setCloudId(cloudId);
    }

    public DockerSlave(DockerCloud cloud, DockerTemplate template, ComputerLauncher launcher) throws IOException, Descriptor.FormException {
        super(
                cloud.getDisplayName() + '-' + Long.toHexString(System.nanoTime()),
                "Docker Node [" + template.getDockerTemplateBase().getImage() + " on "+ cloud.getDisplayName() + "]",
                template.getRemoteFs(),
                template.getNumExecutors(),
                template.getMode(),
                template.getLabelString(),
                launcher,
                template.getRetentionStrategyCopy(),
                template.getNodeProperties()
        );
        this.cloudId = cloud.getDisplayName();
        this.dockerTemplate = template;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public String getCloudId() {
        return cloudId;
    }

    public void setCloudId(String cloudId) {
        this.cloudId = cloudId;
    }

    public DockerTemplate getDockerTemplate() {
        return dockerTemplate;
    }

    public void setDockerTemplate(DockerTemplate dockerTemplate) {
        this.dockerTemplate = dockerTemplate;
    }

    public DockerCloud getCloud() {
        final Cloud cloud = Jenkins.getInstance().getCloud(getCloudId());

        if (cloud == null) {
            throw new RuntimeException("Docker template " + dockerTemplate + " has no assigned Cloud.");
        }

        if (cloud.getClass() != DockerCloud.class) {
            throw new RuntimeException("Assigned cloud is not DockerCloud");
        }

        return (DockerCloud) cloud;
    }

    @Override
    public DockerComputer getComputer() {
        return (DockerComputer) super.getComputer();
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    public void setRun(Run run) {
        this.theRun = run;
    }

    @Override
    public DockerComputer createComputer() {
        return new DockerComputer(this);
    }

    @Override
    public CauseOfBlockage canTake(Queue.BuildableItem item) {
        if (item.task instanceof Queue.FlyweightTask) {
            return new CauseOfBlockage() {
                public String getShortDescription() {
                    return "Don't run FlyweightTask on Docker node";
                }
            };
        }
        return super.canTake(item);
    }

    public boolean containerExistsInCloud() {
        try {
            DockerClient client = getClient();
            client.inspectContainerCmd(containerId).exec();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    protected void _terminate(final TaskListener listener) throws IOException, InterruptedException {
        try {
            toComputer().disconnect(new DockerOfflineCause());
            LOGGER.log(Level.INFO, "Disconnected computer");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Can't disconnect", e);
        }

        if (containerId != null) {
            Computer.threadPoolForRemoting.submit(new Runnable() {

                @Override
                public void run() {

                try {
                    DockerClient client = getClient();
                    client.stopContainerCmd(getContainerId())
                            .withTimeout(10)
                            .exec();
                    LOGGER.log(Level.INFO, "Stopped container {0}", getContainerId());
                } catch(NotFoundException e) {
                    LOGGER.log(Level.INFO, "Container already removed {0}", getContainerId());
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Failed to stop instance " + getContainerId() + " for slave " + name + " due to exception", ex.getMessage());
                    LOGGER.log(Level.SEVERE, "Causing exception for failure on stopping the instance was", ex);
                }

                // If the run was OK, then do any tagging here
                if (theRun != null) {
                    try {
                        slaveShutdown(listener);
                        LOGGER.log(Level.INFO, "Shutdowned slave for {0}", getContainerId());
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Failure to slaveShutdown instance " + getContainerId() + " for slave " + name, e);
                        LOGGER.log(Level.SEVERE, "Causing exception for failure on slaveShutdown was", e);
                    }
                }

                try {
                    DockerClient client = getClient();
                    client.removeContainerCmd(containerId)
                            .withRemoveVolumes(getDockerTemplate().isRemoveVolumes())
                            .exec();

                    LOGGER.log(Level.INFO, "Removed container {0}", getContainerId());
                } catch (NotFoundException e) {
                    LOGGER.log(Level.INFO, "Container already gone.");
                }catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Failed to remove instance " + getContainerId() + " for slave " + name + " due to exception: " + ex.getMessage());
                    LOGGER.log(Level.SEVERE, "Causing exception for failre on removing instance was", ex);
                }
                }
            });

        } else {
            LOGGER.log(Level.SEVERE, "ContainerId is absent, no way to remove/stop container");
        }
    }

    private void slaveShutdown(final TaskListener listener) throws DockerException, IOException {

        // The slave has stopped. Should we commit / tag / push ?

        if (!getJobProperty().tagOnCompletion) {
            addJenkinsAction(null);
            return;
        }

        DockerClient client = getClient();


        // Commit
        String tag_image = client.commitCmd(containerId)
                .withRepository(theRun.getParent().getDisplayName())
                .withTag(theRun.getDisplayName().replace("#", "b")) // allowed only ([a-zA-Z_][a-zA-Z0-9_]*)
                .withAuthor("Jenkins")
                .exec();

        // Tag it with the jenkins name
        addJenkinsAction(tag_image);

        // SHould we add additional tags?
        try {
            String tagToken = getAdditionalTag(listener);

            if (!Strings.isNullOrEmpty(tagToken)) {


                final NameParser.ReposTag reposTag = NameParser.parseRepositoryTag(tagToken);
                final String commitTag = isEmpty(reposTag.tag) ? "latest" : reposTag.tag;

                getClient().tagImageCmd(tag_image, reposTag.repos, commitTag).withForce().exec();

                addJenkinsAction(tagToken);

                if (getJobProperty().pushOnSuccess) {
                    Identifier identifier = Identifier.fromCompoundString(tagToken);

                    PushImageResultCallback resultCallback = new PushImageResultCallback() {
                        public void onNext(PushResponseItem item) {
                            printResponseItemToListener(listener, item);
                            super.onNext(item);
                        }
                    };
                    try {

                        PushImageCmd cmd = getClient().pushImageCmd(identifier);
                        final DockerRegistryEndpoint registry = dockerTemplate.getRegistry();
                        DockerCloud.setRegistryAuthentication(cmd, registry, Jenkins.getInstance());
                        cmd.exec(resultCallback).awaitSuccess();

                    } catch(DockerException ex) {

                        LOGGER.log(Level.SEVERE, "Exception pushing docker image. Check that the destination registry is running.", ex);
                        throw ex;
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not add additional tags", ex);
        }

        if (getJobProperty().cleanImages) {

            client.removeImageCmd(tag_image)
                    .withForce(true)
                    .exec();
        }

    }

    private String getAdditionalTag(TaskListener listener) {
        // Do a macro expansion on the addJenkinsAction token

        // Job property
        String tagToken = getJobProperty().additionalTag;

        // Do any macro expansions
        try {
            if (!Strings.isNullOrEmpty(tagToken))
                tagToken = TokenMacro.expandAll((AbstractBuild) theRun, listener, tagToken);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "can't expand macroses", e);
        }
        return tagToken;
    }

    /**
     * Add a built on docker action.
     */
    private void addJenkinsAction(String tag_image) throws IOException {
        theRun.addAction(new DockerBuildAction(getCloud().getDockerHost().getUri(), containerId, tag_image, dockerTemplate.remoteFsMapping));
        theRun.save();
    }

    public DockerClient getClient() {
        return getCloud().getClient();
    }

    private DockerJobProperty getJobProperty() {

        try {
            DockerJobProperty p = (DockerJobProperty) ((AbstractBuild) theRun).getProject().getProperty(DockerJobProperty.class);

            if (p != null)
                return p;
        } catch (Exception ex) {
            // Don't care.
        }
        // Safe default
        return new DockerJobProperty(false, null, false, true, null);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", name)
                .add("containerId", containerId)
                .add("template", dockerTemplate)
                .toString();
    }

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {

        @Override
        public String getDisplayName() {
            return "Docker Slave";
        }

        @Override
        public boolean isInstantiable() {
            return false;
        }

    }
}
