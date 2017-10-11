package com.nirima.jenkins.plugins.docker;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildVariableContributor;
import hudson.model.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

/**
 * Contribute docker related vars in build.
 * TODO seems this will never satisfy all use cases and dumping inspect json into WS
 * will be more generic solution
 *
 * @author Kanstantsin Shautsou
 */
@Extension
public class DockerBuildVariableContributor extends BuildVariableContributor {
    private static final Logger LOG = LoggerFactory.getLogger(DockerBuildVariableContributor.class);

    @Override
    public void buildVariablesFor(AbstractBuild build, Map<String, String> variables) {
        final Executor executor = build.getExecutor();
        if (executor != null && executor.getOwner() instanceof DockerComputer) {
            final DockerComputer dockerComputer = (DockerComputer) executor.getOwner();
            variables.put("DOCKER_CONTAINER_ID", dockerComputer.getContainerId());
            variables.put("JENKINS_CLOUD_ID", dockerComputer.getCloudId());

            final DockerCloud cloud = dockerComputer.getCloud();
            if (cloud.isExposeDockerHost()) {
                //replace http:// and https:// from docker-java to tcp://
                String dockerHost = cloud.getDockerHost().getUri();
                if (dockerHost.startsWith("unix:")) {
                    dockerHost = "tcp:" + dockerHost.substring(5);
                }
                variables.put("DOCKER_HOST", dockerHost);
            }
        }
    }
}
