package io.jenkins.docker;

import com.google.common.base.Objects;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import hudson.EnvVars;
import hudson.slaves.SlaveComputer;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Represents remote (running) container
 *
 * @author magnayn
 */
public class DockerComputer extends SlaveComputer {
    private static final Logger LOGGER = Logger.getLogger(DockerComputer.class.getName());

    public DockerComputer(DockerTransientNode node) {
        super(node);
    }

    public DockerCloud getCloud() {
        return getNode().getCloud();
    }

    @CheckForNull
    @Override
    public DockerTransientNode getNode() {
        return (DockerTransientNode) super.getNode();
    }

    public String getContainerId() {
        return getNode().getContainerId();
    }

    public String getCloudId() {
        return getNode().getCloudId();
    }

    @Override
    public EnvVars getEnvironment() throws IOException, InterruptedException {
        EnvVars variables = super.getEnvironment();
        variables.put("DOCKER_CONTAINER_ID", getContainerId());
        final DockerCloud cloud = getCloud();
        if (cloud != null && cloud.isExposeDockerHost()) {
            variables.put("JENKINS_CLOUD_ID", cloud.name);
            String dockerHost = cloud.getDockerApi().getDockerHost().getUri();
            variables.put("DOCKER_HOST", dockerHost);
        }
        return variables;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", super.getName())
                .add("slave", getNode())
                .toString();
    }
}
