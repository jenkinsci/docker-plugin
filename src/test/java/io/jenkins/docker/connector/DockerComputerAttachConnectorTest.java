package io.jenkins.docker.connector;

import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

public class DockerComputerAttachConnectorTest extends DockerComputerConnectorTest {

    @Test
    public void should_connect_agent() throws InterruptedException, ExecutionException, IOException, URISyntaxException {

        final DockerTemplate template = new DockerTemplate(
                new DockerTemplateBase("jenkins/slave"),
                new DockerComputerAttachConnector("jenkins"),
                "docker-agent", "/home/jenkins/agent", "10"
        );

        should_connect_agent(template);
    }


}
