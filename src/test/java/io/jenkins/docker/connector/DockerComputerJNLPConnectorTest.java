package io.jenkins.docker.connector;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.core.command.CreateContainerCmdImpl;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import hudson.slaves.JNLPLauncher;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
                new DockerComputerJNLPConnector(new JNLPLauncher(null, null)).withUser("jenkins")
                        .withJenkinsUrl(uri.toString()),
                "docker-agent", "/home/jenkins/agent", "10"
        );

        if (SystemUtils.IS_OS_LINUX) {
            template.getDockerTemplateBase().setNetwork("host");
        }

        should_connect_agent(template);
    }

    @Test
    public void testKeepingEvnInBeforeContainerCreated() throws IOException, InterruptedException {

        String env1 = "ENV1=val1";
        DockerComputerJNLPConnector connector = new DockerComputerJNLPConnector(new JNLPLauncher(null, "-Dhttp.proxyPort=8080"));

        CreateContainerCmd createCmd = new CreateContainerCmdImpl(createContainerCmd -> null, "hello-world");
        createCmd.withName("container-name").withEnv(env1);
        connector.beforeContainerCreated(null, null, createCmd);

        String[] env = createCmd.getEnv();
        assertNotNull("Environment variables are expected", env);
        assertEquals("Environment variables are expected", 2, env.length);

        assertTrue("Original environment variable is not found", Arrays.asList(env).contains(env1));

    }

    @Test
    public void testAddingVmargsInBeforeContainerCreated() throws IOException, InterruptedException {

        String vmargs = "-Dhttp.proxyPort=8080";
        DockerComputerJNLPConnector connector = new DockerComputerJNLPConnector(new JNLPLauncher(null, vmargs));

        CreateContainerCmd createCmd = new CreateContainerCmdImpl(createContainerCmd -> null, "hello-world");
        createCmd.withName("container-name");
        connector.beforeContainerCreated(null, null, createCmd);

        String[] env = createCmd.getEnv();
        assertNotNull("Environment variable is expected", env);
        assertEquals("Environment variable is expected", 1, env.length);

        assertTrue("Original environment variable is not found", env[0].endsWith(vmargs));

    }

}
