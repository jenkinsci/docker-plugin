package io.jenkins.docker.connector;

import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;

import io.jenkins.docker.connector.DockerComputerAttachConnector.ArgumentVariables;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

public class DockerComputerAttachConnectorTest extends DockerComputerConnectorTest {
    private static final String ATTACH_AGENT_IMAGE_IMAGENAME = "jenkins/agent";

    @Test
    public void connectAgentViaDirectAttach() throws Exception {
        final DockerComputerAttachConnector connector = new DockerComputerAttachConnector(COMMON_IMAGE_USERNAME);
        final String testName = "connectAgentViaDirectAttach";
        testAgentCanStartAndConnect(connector, testName);
    }

    @Test
    public void connectAgentViaDirectAttachWithCustomCmd() throws Exception {
        final DockerComputerAttachConnector connector = new DockerComputerAttachConnector(COMMON_IMAGE_USERNAME);
        // We could setJavaExe("/usr/local/openjdk-8/bin/java") too, but that'd mean
        // we'd break the instant that the public docker image's JVM updates to 9 or
        // higher; best stick to just using the $PATH.
        connector.setJvmArgsString("-Xmx1g"+"\n"
                + "-Dfoo=bar\n");
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
        final DockerTemplate template = new DockerTemplate(
                new DockerTemplateBase(ATTACH_AGENT_IMAGE_IMAGENAME),
                connector,
                LABEL, COMMON_IMAGE_HOMEDIR, INSTANCE_CAP
        );
        template.setName(testName);
        should_connect_agent(template);
    }
}
