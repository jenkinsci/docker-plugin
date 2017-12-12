package com.nirima.jenkins.plugins.docker;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import hudson.model.Node;
import io.jenkins.docker.client.DockerAPI;
import io.jenkins.docker.connector.DockerComputerAttachConnector;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;

import static com.cloudbees.plugins.credentials.CredentialsScope.SYSTEM;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerCloudTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testConstructor_0_10_2() {
        new DockerCloud("name",
                Collections.<DockerTemplate>emptyList(), //templates
                "http://localhost:4243", //serverUrl
                100, //containerCap,
                10, // connectTimeout,
                10, // readTimeout,
                null, // credentialsId,
                null, //version
                null); // dockerHostname
    }

    @Test
    public void globalConfigRoundtrip() throws Exception {

        // Create fake credentials, so they are selectable on configuration for during configuration roundtrip
        final CredentialsStore store = CredentialsProvider.lookupStores(jenkins.getInstance()).iterator().next();
        DockerServerCredentials dc = new DockerServerCredentials(SYSTEM, "credentialsId", "test", null, null, null);
        store.addCredentials(Domain.global(), dc);
        UsernamePasswordCredentials rc = new UsernamePasswordCredentialsImpl(SYSTEM, "pullCredentialsId", null, null, null);
        store.addCredentials(Domain.global(), rc);

        final DockerTemplate template = new DockerTemplate(
                new DockerTemplateBase("image", "pullCredentialsId", "dnsStirng", "network",
                        "dockerCommand", "volumesString", "volumesFroString", "environmentString",
                        "hostname", 128, 256, 42, "bindPorts", true, true, true, "macAddress", "extraHostsString"),
                new DockerComputerAttachConnector("jenkins"),
                "labelString", "remoteFs", "10");
        template.setPullStrategy(DockerImagePullStrategy.PULL_NEVER);
        template.setMode(Node.Mode.NORMAL);
        template.setRemoveVolumes(true);
        template.setRetentionStrategy(new DockerOnceRetentionStrategy(33));

        DockerCloud cloud = new DockerCloud("docker", new DockerAPI(new DockerServerEndpoint("uri", "credentialsId")),
                Collections.singletonList(template));

        jenkins.getInstance().clouds.replaceBy(Collections.singleton(cloud));

        jenkins.configRoundtrip();

        Assert.assertEquals(cloud, jenkins.getInstance().clouds.get(0));
    }
}
