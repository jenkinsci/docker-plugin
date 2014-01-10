package com.nirima.jenkins.plugins.docker;

import com.google.common.base.Objects;
import com.kpelykh.docker.client.DockerClient;
import com.kpelykh.docker.client.DockerException;
import com.kpelykh.docker.client.model.CommitConfig;
import com.nirima.jenkins.plugins.docker.action.DockerBuildAction;
import hudson.model.*;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


public class DockerSlave extends AbstractCloudSlave {

    private static final Logger LOGGER = Logger.getLogger(DockerSlave.class.getName());

    public final DockerTemplate dockerTemplate;
    public final String containerId;


    private transient Run theRun;

    public DockerSlave(DockerTemplate dockerTemplate, String containerId, String name, String nodeDescription, String remoteFS, int numExecutors, Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties) throws Descriptor.FormException, IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);
        this.dockerTemplate = dockerTemplate;
        this.containerId = containerId;
    }

    public DockerCloud getCloud() {
        return dockerTemplate.getParent();
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    public void commitOnTerminate(Run run) {
       this.theRun = run;
    }

    @Override
    public DockerComputer createComputer() {
        return new DockerComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        DockerClient client = getClient();

        try {
            toComputer().disconnect(null);
            client.stopContainer(containerId);

            if( theRun != null ) {
                try {
                    commit();
                } catch (DockerException e) {
                    LOGGER.log(Level.SEVERE, "Failure to commit instance " + containerId);
                }
            }

            client.removeContainer(containerId);
        } catch (DockerException e) {
            LOGGER.log(Level.SEVERE, "Failure to terminate instance " + containerId);
        }
    }

    public void commit() throws DockerException, IOException {
        DockerClient client = getClient();

        CommitConfig commitConfig = new CommitConfig.Builder(containerId)
                .author("Jenkins")
                .repo(theRun.getParent().getDisplayName())
                .tag(theRun.getDisplayName())
                .build();

        String tag_image = client.commit(commitConfig);

        theRun.addAction( new DockerBuildAction(getCloud().serverUrl, containerId, tag_image) );
        theRun.save();
    }

    public DockerClient getClient() {
        return dockerTemplate.getParent().connect();
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
                .add("containerId", containerId)
                .toString();
    }
}
