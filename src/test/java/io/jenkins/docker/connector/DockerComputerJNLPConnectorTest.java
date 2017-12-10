package io.jenkins.docker.connector;

import hudson.slaves.JNLPLauncher;
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
        }

        should_connect_agent(
                new DockerComputerJNLPConnector(new JNLPLauncher(null, null)).user("jenkins")
                        .jenkinsUrl(uri.toString()),
                "jenkins/jnlp-slave"
        );
    }


}
