package io.jenkins.docker.connector;

import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import org.junit.Test;

public class DockerComputerAttachConnectorTest extends DockerComputerConnectorTest {
    private static final String ATTACH_SLAVE_IMAGE_IMAGENAME = "jenkins/slave";

    @Test
    public void connectAgentViaDirectAttach() throws Exception {
        final DockerTemplate template = new DockerTemplate(
                new DockerTemplateBase(ATTACH_SLAVE_IMAGE_IMAGENAME),
                new DockerComputerAttachConnector(COMMON_IMAGE_USERNAME),
                LABEL, COMMON_IMAGE_HOMEDIR, INSTANCE_CAP
        );
        template.setName("connectAgentViaDirectAttach");
        should_connect_agent(template);
    }
}
