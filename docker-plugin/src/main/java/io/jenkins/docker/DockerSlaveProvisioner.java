package io.jenkins.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.google.common.base.Strings;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerImagePullStrategy;
import com.nirima.jenkins.plugins.docker.DockerSlave;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import java.io.IOException;

/**
 * Responsible to create the adequate (set of) container(s) to run a Jenkins Agent on Docker.
 * Depending on mechanism used to establish the agent's remoting connection (ssh, jnlp, ...) the actual
 * {@link DockerSlave} instance might be registered as a {@link hudson.model.Node}
 * before or after the container have been created.
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class DockerSlaveProvisioner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerSlaveProvisioner.class);


    protected final DockerCloud cloud;
    protected final DockerTemplate template;
    protected final DockerClient client;
    protected @CheckForNull String container;

    public DockerSlaveProvisioner(DockerCloud cloud, DockerTemplate template, DockerClient client) {
        this.cloud = cloud;
        this.template = template;
        this.client =  client;
    }

    /**
     * Provision and setup container(s) to create an active {@link DockerSlave}.
     */
    public abstract DockerSlave provision() throws IOException, Descriptor.FormException, InterruptedException;

    public String getDisplayName(String containerId, InspectContainerResponse inspect) {
        String slaveName = containerId.substring(0, 12);
        try {
            slaveName = cloud.getDisplayName() + "-" + slaveName;
        } catch (Exception ex) {
            LOGGER.warn("Error fetching cloud name");
        }
        if (cloud.isSwarm()) {
            String hostName = JenkinsUtils.getHostnameFromBinding(inspect);
            if (!Strings.isNullOrEmpty(hostName)) {
                slaveName = slaveName + "-" + hostName;
            }
        }
        return slaveName;
    }

    protected String runContainer() throws IOException, InterruptedException {
        pullImage();

        LOGGER.info("Trying to run container for {}", template.getImage());
        CreateContainerCmd cmd = client.createContainerCmd(template.getImage());
        template.fillContainerConfig(cmd);

        prepareCreateContainerCommand(cmd);

        this.container = cmd.exec().getId();

        try {
            setupContainer();

            client.startContainerCmd(container).exec();
        } catch (DockerException e) {
            // if something went wrong, cleanup aborted container
            client.removeContainerCmd(container).withForce(true).exec();
            throw e;
        }

        return container;
    }

    protected void pullImage() throws IOException {

        String image = template.getFullImageId();
        DockerImagePullStrategy strategy = template.getPullStrategy();

        if (strategy.shouldPullImage(client, image)) {
            // TODO create a FlyWeightTask so end-user get visibility on pull operation progress
            LOGGER.info("Pulling image '{}'. This may take awhile...", image);

            long startTime = System.currentTimeMillis();

            PullImageCmd cmd =  client.pullImageCmd(image);
            final DockerRegistryEndpoint registry = template.getRegistry();
            DockerCloud.setRegistryAuthentication(cmd, registry, Jenkins.getInstance());
            try {
                cmd.exec(new PullImageResultCallback()).awaitCompletion();
            } catch (InterruptedException e) {
                throw new DockerClientException("Could not pull image: " + image, e);
            }
            try {
                client.inspectImageCmd(image).exec();
            } catch (NotFoundException e) {
                throw new DockerClientException("Could not pull image: " + image, e);
            }
            long pullTime = System.currentTimeMillis() - startTime;
            LOGGER.info("Finished pulling image '{}', took {} ms", image, pullTime);
        }
    }


    /**
     * Implementation can override this method to customize the container to be created as Jenkins Agent.
     * Typically : inject environment variables
     */
    protected void prepareCreateContainerCommand(CreateContainerCmd cmd) throws DockerException, IOException {
        // nop
    }

    /**
     * Implementation can override this method to customize the container before it get started.
     * Typically : copy <code>slave.jar</code> into container, etc
     */
    protected void setupContainer() throws IOException, InterruptedException {
        // nop
    }

}
