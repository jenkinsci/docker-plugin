package io.jenkins.docker.connector;

import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import io.jenkins.docker.connector.DockerComputerAttachConnector.ArgumentVariables;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.Assume;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;

public class DockerComputerAttachConnectorTest extends DockerComputerConnectorTest {
    private static final String ATTACH_AGENT_IMAGE_IMAGENAME = "jenkins/agent";

    @Test
    public void connectAgentViaDirectAttach() throws Exception {
        Assume.assumeTrue(DockerClientFactory.instance().isDockerAvailable());
        final DockerComputerAttachConnector connector = new DockerComputerAttachConnector(COMMON_IMAGE_USERNAME);
        final String testName = "connectAgentViaDirectAttach";
        testAgentCanStartAndConnect(connector, testName);
    }

    @Test
    public void connectAgentViaDirectAttachWithCustomCmd() throws Exception {
        Assume.assumeTrue(DockerClientFactory.instance().isDockerAvailable());
        final DockerComputerAttachConnector connector = new DockerComputerAttachConnector(COMMON_IMAGE_USERNAME);
        // We could setJavaExe("/opt/jdk-11/bin/java") too, but that'd
        // mean we'd break the instant that the public docker image's JVM
        // updates to a newer JDK; best stick to just using the $PATH.
        connector.setJvmArgsString("-Xmx1g" + "\n" + "-Dfoo=bar\n");
        connector.setEntryPointCmdString("java\n"
                + "${" + ArgumentVariables.JvmArgs.getName() + "}\n"
                + "-Dsomething=somethingElse\n"
                + "-jar\n"
                + "${" + ArgumentVariables.RemoteFs.getName() + "}/${" + ArgumentVariables.JarName.getName() + "}\n"
                + "-noReconnect\n"
                + "-noKeepAlive\n");
        final String testName = "connectAgentViaDirectAttachWithCustomCmd";
        testAgentCanStartAndConnect(connector, testName);
    }

    private void testAgentCanStartAndConnect(final DockerComputerAttachConnector connector, final String testName)
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        final String imagenameAndVersion =
                ATTACH_AGENT_IMAGE_IMAGENAME + ':' + getJenkinsDockerImageVersionForThisEnvironment();
        final DockerTemplate template = new DockerTemplate(
                new DockerTemplateBase(imagenameAndVersion),
                connector,
                getLabelForTemplate(),
                COMMON_IMAGE_HOMEDIR,
                INSTANCE_CAP);
        template.setName(testName);
        should_connect_agent(template);
    }
}
