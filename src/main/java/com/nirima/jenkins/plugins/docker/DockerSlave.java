package com.nirima.jenkins.plugins.docker;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.nirima.docker.client.DockerException;
import com.nirima.docker.client.DockerClient;
import com.nirima.jenkins.plugins.docker.action.DockerBuildAction;

import hudson.Extension;
import hudson.model.*;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DockerSlave extends AbstractCloudSlave {

    private static final Logger LOGGER = Logger.getLogger(DockerSlave.class.getName());

    public final DockerTemplate dockerTemplate;
    public final String containerId;

    private transient Run theRun;

    @DataBoundConstructor
    public DockerSlave(DockerTemplate dockerTemplate, String containerId, String name, String nodeDescription, String remoteFS, int numExecutors, Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws Descriptor.FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);
        Preconditions.checkNotNull(dockerTemplate);
        Preconditions.checkNotNull(containerId);

        this.dockerTemplate = dockerTemplate;
        this.containerId = containerId;
    }

    public DockerCloud getCloud() {
        DockerCloud theCloud = dockerTemplate.getParent();

        if (theCloud == null) {
            throw new RuntimeException("Docker template " + dockerTemplate + " has no parent ");
        }

        return theCloud;
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

    public boolean containerExistsInCloud() {
        try {
            DockerClient client = getClient();
            client.container(containerId).inspect();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {

        try {
            toComputer().disconnect(null);

            try {
                DockerClient client = getClient();
                client.container(containerId).stop();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to stop instance " + containerId + " for slave " + name + " due to exception", ex);
            }

            // If the run was OK, then do any tagging here
            if (theRun != null) {
                try {
                    slaveShutdown(listener);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Failure to slaveShutdown instance " + containerId + " for slave " + name, e);
                }
            }

            try {
                DockerClient client = getClient();
                client.container(containerId).remove();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to remove instance " + containerId + " for slave " + name + " due to exception", ex);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failure to terminate instance " + containerId + " for slave " + name, e);
        }
    }

    private void slaveShutdown(TaskListener listener) throws DockerException, IOException {

        // The slave has stopped. Should we commit / tag / push ?
        if (!getJobProperty().tagOnCompletion) {
            addJenkinsAction(null);
            return;
        }

        DockerClient client = getClient();

        // Commit
        String tag_image = client.container(containerId).createCommitCommand()
                .repo(theRun.getParent().getDisplayName())
                .tag(theRun.getDisplayName())
                .author("Jenkins")
                .execute();

        // Tag it with the jenkins name
        addJenkinsAction(tag_image);

        // SHould we add additional tags?
        try {
            String tagToken = getAdditionalTag(listener);

            if (!Strings.isNullOrEmpty(tagToken)) {
                client.image(tag_image).tag(tagToken, false);
                addJenkinsAction(tagToken);

                if (getJobProperty().pushOnSuccess) {
                    client.image(tagToken).push(null);
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not add additional tags");
        }

        if (getJobProperty().cleanImages) {

            // For some reason, docker delete doesn't delete all tagged
            // versions, despite force = true.
            // So, do it multiple times (protect against infinite looping).
            int delete = 100;
            while (delete != 0) {
                int count = client.image(tag_image).removeCommand()
                        .force(true)
                        .execute().size();
                if (count == 0) {
                    delete = 0;
                } else {
                    delete--;
                }
            }
        }

    }

    private String getAdditionalTag(TaskListener listener) {
        // Do a macro expansion on the addJenkinsAction token

        // Job property
        String tagToken = getJobProperty().additionalTag;

        // Do any macro expansions
        try {
            if (!Strings.isNullOrEmpty(tagToken)) {
                tagToken = TokenMacro.expandAll((AbstractBuild) theRun, listener, tagToken);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tagToken;
    }

    /**
     * Add a built on docker action.
     *
     * @param tag_image
     * @throws IOException
     */
    private void addJenkinsAction(String tag_image) throws IOException {
        theRun.addAction(new DockerBuildAction(getCloud().serverUrl, containerId, tag_image));
        theRun.save();
    }

    public DockerClient getClient() {
        return getCloud().connect();
    }

    /**
     * Called when the slave is connected to Jenkins
     */
    public void onConnected() {

    }

    public void retentionTerminate() throws IOException, InterruptedException {
        terminate();
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", name)
                .add("containerId", containerId)
                .add("template", dockerTemplate)
                .toString();
    }

    private DockerJobProperty getJobProperty() {

        try {
            DockerJobProperty p = (DockerJobProperty) ((AbstractBuild) theRun).getProject().getProperty(DockerJobProperty.class);

            if (p != null) {
                return p;
            }
        } catch (Exception ex) {
            // Don't care.
        }
        // Safe default
        return new DockerJobProperty(false, null, false, true);
    }

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {

        @Override
        public String getDisplayName() {
            return "Docker Slave";
        }

        ;

		@Override
        public boolean isInstantiable() {
            return false;
        }

    }
}
