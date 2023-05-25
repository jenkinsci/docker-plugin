package io.jenkins.docker;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.EnvVars;
import hudson.slaves.AbstractCloudComputer;
import io.jenkins.docker.client.DockerAPI;
import java.io.IOException;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedItem;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;

/**
 * Represents remote (running) container
 *
 * @author magnayn
 */
public class DockerComputer extends AbstractCloudComputer<DockerTransientNode> implements TrackedItem {
    // private static final Logger LOGGER = Logger.getLogger(DockerComputer.class.getName());

    public DockerComputer(DockerTransientNode node) {
        super(node);
    }

    @CheckForNull
    public DockerCloud getCloud() {
        final DockerTransientNode nodeOrNull = getNode();
        return nodeOrNull == null ? null : nodeOrNull.getCloud();
    }

    @CheckForNull
    public String getContainerId() {
        final DockerTransientNode nodeOrNull = getNode();
        return nodeOrNull == null ? null : nodeOrNull.getContainerId();
    }

    @CheckForNull
    public String getCloudId() {
        final DockerTransientNode nodeOrNull = getNode();
        return nodeOrNull == null ? null : nodeOrNull.getCloudId();
    }

    @Override
    public EnvVars getEnvironment() throws IOException, InterruptedException {
        EnvVars variables = super.getEnvironment();
        final String containerIdOrNull = getContainerId();
        if (containerIdOrNull != null) {
            variables.put("DOCKER_CONTAINER_ID", containerIdOrNull);
        }
        final DockerCloud cloudOrNull = getCloud();
        if (cloudOrNull != null && cloudOrNull.isExposeDockerHost()) {
            variables.put("JENKINS_CLOUD_ID", cloudOrNull.name);
            final DockerAPI dockerApi = cloudOrNull.getDockerApi();
            final DockerServerEndpoint dockerHost = dockerApi.getDockerHost();
            final String dockerHostUriOrNull = dockerHost.getUri();
            if (dockerHostUriOrNull != null) {
                variables.put("DOCKER_HOST", dockerHostUriOrNull);
            }
        }
        return variables;
    }

    @Override
    public String toString() {
        return "DockerComputer{" + "name='" + super.getName() + '\'' + ", node='" + nodeName + '\'' + '}';
    }

    @Nullable
    @Override
    public ProvisioningActivity.Id getId() {
        final DockerTransientNode nodeOrNull = getNode();
        return nodeOrNull == null ? null : nodeOrNull.getId();
    }
}
