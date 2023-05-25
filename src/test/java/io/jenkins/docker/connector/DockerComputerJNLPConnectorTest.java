package io.jenkins.docker.connector;

import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import hudson.slaves.JNLPLauncher;
import java.net.URI;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Test;

public class DockerComputerJNLPConnectorTest extends DockerComputerConnectorTest {
    private static final String JNLP_AGENT_IMAGE_IMAGENAME = "jenkins/inbound-agent";

    @Test
    public void connectAgentViaJNLP() throws Exception {

        final JenkinsLocationConfiguration location = JenkinsLocationConfiguration.get();
        URI uri = URI.create(location.getUrl());
        if (SystemUtils.IS_OS_MAC) {
            uri = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    "docker.for.mac.localhost",
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment());
        } else if (SystemUtils.IS_OS_WINDOWS) {
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
                new DockerComputerJNLPConnector(new JNLPLauncher(null))
                        .withUser(COMMON_IMAGE_USERNAME)
                        .withJenkinsUrl(uri.toString()),
                getLabelForTemplate(),
                COMMON_IMAGE_HOMEDIR,
                INSTANCE_CAP);

        if (SystemUtils.IS_OS_LINUX) {
            template.getDockerTemplateBase().setNetwork("host");
        }

        template.setName("connectAgentViaJNLP");
        should_connect_agent(template);
    }
}
