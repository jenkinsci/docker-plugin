package io.jenkins.docker.connector;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import hudson.Platform;
import java.net.URI;
import jenkins.model.JenkinsLocationConfiguration;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.DockerClientFactory;

@WithJenkins
class DockerComputerJNLPConnectorTest extends DockerComputerConnectorTest {
    private static final String JNLP_AGENT_IMAGE_IMAGENAME = "jenkins/inbound-agent";

    @Test
    void connectAgentViaJNLP() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());

        final JenkinsLocationConfiguration location = JenkinsLocationConfiguration.get();
        URI uri = URI.create(location.getUrl());
        if (Platform.isDarwin()) { // Mac OS
            uri = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    "docker.for.mac.localhost",
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment());
        } else if (Platform.current() == Platform.WINDOWS) {
            uri = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    "docker.for.windows.localhost",
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment());
        }

        final String imagenameAndVersion =
                JNLP_AGENT_IMAGE_IMAGENAME + ':' + getJenkinsDockerImageVersionForThisEnvironment();
        final DockerTemplate template = new DockerTemplate(
                new DockerTemplateBase(imagenameAndVersion),
                new DockerComputerJNLPConnector()
                        .withUser(COMMON_IMAGE_USERNAME)
                        .withJenkinsUrl(uri.toString()),
                getLabelForTemplate(),
                COMMON_IMAGE_HOMEDIR,
                INSTANCE_CAP);

        if (Platform.current() == Platform.UNIX && !Platform.isDarwin()) {
            template.getDockerTemplateBase().setNetwork("host");
        }

        template.setName("connectAgentViaJNLP");
        should_connect_agent(template);
    }
}
