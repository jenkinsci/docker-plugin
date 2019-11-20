package io.jenkins.docker.connector;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;

import hudson.slaves.JNLPLauncher;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.TreeMap;

public class DockerComputerJNLPConnectorTest extends DockerComputerConnectorTest {
    private static final String JNLP_SLAVE_IMAGE_IMAGENAME = "jenkins/jnlp-slave";

    @Test
    public void connectAgentViaJNLP() throws Exception {

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
                new DockerTemplateBase(JNLP_SLAVE_IMAGE_IMAGENAME),
                new DockerComputerJNLPConnector(new JNLPLauncher(null, null)).withUser(COMMON_IMAGE_USERNAME)
                        .withJenkinsUrl(uri.toString()),
                        LABEL, COMMON_IMAGE_HOMEDIR, INSTANCE_CAP
        );

        if (SystemUtils.IS_OS_LINUX) {
            template.getDockerTemplateBase().setNetwork("host");
        }

        template.setName("connectAgentViaJNLP");
        should_connect_agent(template);
    }

    @Test
    public void testKeepingEvnInBeforeContainerCreated() throws IOException, InterruptedException {
        // Given
        final String env1 = "ENV1=val1";
        final String vmargs = "-Dhttp.proxyPort=8080";
        final DockerComputerJNLPConnector connector = new DockerComputerJNLPConnector(new JNLPLauncher(null, vmargs));

        final CreateContainerCmd createCmd = mock(CreateContainerCmd.class);
        final Map<String, String> containerLabels = new TreeMap<>();
        when(createCmd.getLabels()).thenReturn(containerLabels);
        DockerTemplate.setNodeNameInContainerConfig(createCmd, "nodeName");
        when(createCmd.getEnv()).thenReturn(new String[]{ env1 });

        // When
        connector.beforeContainerCreated(null, null, createCmd);

        // Then
        verify(createCmd, times(1)).withEnv(new String[]{
                env1,
                "JAVA_OPT=" + vmargs
        });
    }

    @Test
    public void testAddingVmargsInBeforeContainerCreated() throws IOException, InterruptedException {
        // Given
        final String vmargs = "-Dhttp.proxyPort=8080";
        final DockerComputerJNLPConnector connector = new DockerComputerJNLPConnector(new JNLPLauncher(null, vmargs));

        final CreateContainerCmd createCmd = mock(CreateContainerCmd.class);
        final Map<String, String> containerLabels = new TreeMap<>();
        when(createCmd.getLabels()).thenReturn(containerLabels);
        DockerTemplate.setNodeNameInContainerConfig(createCmd, "nodeName");

        // When
        connector.beforeContainerCreated(null, null, createCmd);

        // Then
        verify(createCmd, times(1)).withEnv(new String[]{
                "JAVA_OPT=" + vmargs
        });
    }

}
