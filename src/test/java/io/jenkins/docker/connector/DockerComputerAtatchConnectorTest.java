package io.jenkins.docker.connector;

import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

public class DockerComputerAtatchConnectorTest extends DockerComputerConnectorTest {

    @Test
    public void should_connect_agent() throws InterruptedException, ExecutionException, IOException, URISyntaxException {
        should_connect_agent(
                new DockerComputerAttachConnector("jenkins"),
                "jenkins/slave"
        );
    }


}
