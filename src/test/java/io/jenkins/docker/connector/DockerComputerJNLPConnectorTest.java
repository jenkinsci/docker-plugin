package io.jenkins.docker.connector;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import hudson.slaves.JNLPLauncher;
import io.jenkins.docker.client.DockerAPI;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

public class DockerComputerJNLPConnectorTest extends DockerComputerConnectorTest {

    @Test
    public void should_connect_agent() throws InterruptedException, ExecutionException, IOException, URISyntaxException {

        final JenkinsLocationConfiguration location = JenkinsLocationConfiguration.get();
        URI uri = URI.create(location.getUrl());
        if (SystemUtils.IS_OS_MAC) {
            uri = new URI(uri.getScheme(), uri.getUserInfo(), "docker.for.mac.localhost",
                    uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        } else if (SystemUtils.IS_OS_WINDOWS) {
            uri = new URI(uri.getScheme(), uri.getUserInfo(), "docker.for.windows.localhost",
                    uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        }

        final DockerTemplate template = new DockerTemplate(
                new DockerTemplateBase("jenkins/jnlp-slave"),
                new DockerComputerJNLPConnector(new JNLPLauncher(null, null)).user("jenkins")
                        .jenkinsUrl(uri.toString()),
                "docker-agent", "/home/jenkins/agent", "10"
        );

        if (SystemUtils.IS_OS_LINUX) {
            template.getDockerTemplateBase().setNetwork("host");
        }

        should_connect_agent(template);
    }


}
