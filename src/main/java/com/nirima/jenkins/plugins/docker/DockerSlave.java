package com.nirima.jenkins.plugins.docker;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.nirima.docker.client.DockerException;
import com.nirima.docker.client.model.CommitConfig;
import com.nirima.docker.client.DockerClient;
import com.nirima.jenkins.plugins.docker.action.DockerBuildAction;

import hudson.Extension;
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

    private transient boolean commitOnTermate;

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

    public void setRun(Run run) {
        this.theRun = run;
    }

    public void commitOnTerminate() {
       commitOnTermate = true;
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
            client.container(containerId).stop();

            if( theRun != null ) {
                try {
                    if( commitOnTermate )
                        commit();
                    else
                        tag(null);
                } catch (DockerException e) {
                    LOGGER.log(Level.SEVERE, "Failure to commit instance " + containerId);
                }
            }

            client.containersApi().removeContainer(containerId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failure to terminate instance " + containerId);
        }
    }

    public void commit() throws DockerException, IOException {
        DockerClient client = getClient();

        String tag_image = client.container(containerId).createCommitCommand()
                                 .repo(theRun.getParent().getDisplayName())
                                 .tag(theRun.getDisplayName())
                                 .author("Jenkins")
                                 .execute();

        tag(tag_image);

        // SHould we add additional tags?
        try
        {
            if( !Strings.isNullOrEmpty(dockerTemplate.additionalTag) ) {
                client.image(tag_image).tag(dockerTemplate.additionalTag, false);
            }
        }
        catch(Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not add additional tags");
        }

        if( dockerTemplate.push ) {
            try {
                client.image(tag_image).push(null);
            }
            catch(Exception ex) {
                LOGGER.log(Level.SEVERE, "Could not push image");
            }
        }

    }

    private void tag(String tag_image) throws IOException {
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

    @Extension
	public static final class DescriptorImpl extends SlaveDescriptor {

    	@Override
		public String getDisplayName() {
			return "Docker Slave";
    	};

		@Override
		public boolean isInstantiable() {
			return false;
		}

	}
}
