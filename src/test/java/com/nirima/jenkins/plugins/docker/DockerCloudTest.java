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

    @Test
    public void keepTrackOfContainersInProgress() {
        final DockerTemplate i1 = new DockerTemplate(new DockerTemplateBase("image1"), null, null, null, null);
        final DockerTemplate i2 = new DockerTemplate(new DockerTemplateBase("image2"), null, null, null, null);
        final String uniqueId = Integer.toString(hashCode(), 36);
        final DockerCloud c1 = new DockerCloud("cloud1." + uniqueId, null, null);
        final DockerCloud c2 = new DockerCloud("cloud2." + uniqueId, null, null);

        assertCount(c1, c2, i1, i2, 0, 0, 0, 0);
        Assert.assertEquals("DockerCloud.CONTAINERS_IN_PROGRESS is empty to start with",
                DockerCloud.CONTAINERS_IN_PROGRESS, Collections.EMPTY_MAP);

        c1.incrementContainersInProgress(i1);
        assertCount(c1, c2, i1, i2, 1, 0, 0, 0);
        c1.decrementContainersInProgress(i2);
        assertCount(c1, c2, i1, i2, 1, -1, 0, 0);
        c2.incrementContainersInProgress(i1);
        assertCount(c1, c2, i1, i2, 1, -1, 1, 0);
        c2.incrementContainersInProgress(i1);
        assertCount(c1, c2, i1, i2, 1, -1, 2, 0);

        c1.decrementContainersInProgress(i1);
        assertCount(c1, c2, i1, i2, 0, -1, 2, 0);
        c1.incrementContainersInProgress(i2);
        assertCount(c1, c2, i1, i2, 0, 0, 2, 0);
        c2.decrementContainersInProgress(i1);
        assertCount(c1, c2, i1, i2, 0, 0, 1, 0);
        c2.decrementContainersInProgress(i1);
        assertCount(c1, c2, i1, i2, 0, 0, 0, 0);
        Assert.assertEquals("DockerCloud.CONTAINERS_IN_PROGRESS is empty afterwards",
                DockerCloud.CONTAINERS_IN_PROGRESS, Collections.EMPTY_MAP);
    }

    private static void assertCount(DockerCloud c1, DockerCloud c2, DockerTemplate i1, DockerTemplate i2, int c1i1,
            int c1i2, int c2i1, int c2i2) {
        final int c1All = c1i1 + c1i2;
        final int c2All = c2i1 + c2i2;
        final String state = "when c1(" + c1i1 + "," + c1i2 + "), c2(" + c2i1 + "," + c2i2 + "), ";
        Assert.assertEquals(state + "c1.countContainersInProgress()", c1All, c1.countContainersInProgress());
        Assert.assertEquals(state + "c2.countContainersInProgress()", c2All, c2.countContainersInProgress());
        Assert.assertEquals(state + "c1.countContainersInProgress(i1)", c1i1, c1.countContainersInProgress(i1));
        Assert.assertEquals(state + "c1.countContainersInProgress(i2)", c1i2, c1.countContainersInProgress(i2));
        Assert.assertEquals(state + "c2.countContainersInProgress(i1)", c2i1, c2.countContainersInProgress(i1));
        Assert.assertEquals(state + "c2.countContainersInProgress(i2)", c2i2, c2.countContainersInProgress(i2));
    }
}
