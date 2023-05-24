package com.nirima.jenkins.plugins.docker;

import static com.cloudbees.plugins.credentials.CredentialsScope.SYSTEM;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.dockerjava.api.model.AuthConfig;
import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import hudson.model.Node;
import hudson.util.Secret;
import io.jenkins.docker.client.DockerAPI;
import io.jenkins.docker.connector.DockerComputerAttachConnector;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerCloudTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @SuppressWarnings("unused")
    @Test
    public void testConstructor_0_10_2() {
        new DockerCloud(
                "name",
                List.of(), // templates
                "http://localhost:4243", // serverUrl
                100, // containerCap,
                10, // connectTimeout,
                10, // readTimeout,
                null, // credentialsId,
                null, // version
                null); // dockerHostname
    }

    @Test
    public void globalConfigRoundtrip() throws Exception {

        // Create fake credentials, so they are selectable on configuration for during configuration roundtrip
        final CredentialsStore store = CredentialsProvider.lookupStores(jenkins.getInstance())
                .iterator()
                .next();
        DockerServerCredentials dc =
                new DockerServerCredentials(SYSTEM, "credentialsId", "test", (Secret) null, null, null);
        store.addCredentials(Domain.global(), dc);
        UsernamePasswordCredentials rc =
                new UsernamePasswordCredentialsImpl(SYSTEM, "pullCredentialsId", null, null, null);
        store.addCredentials(Domain.global(), rc);

        final DockerTemplateBase templateBase = new DockerTemplateBase(
                "image",
                "pullCredentialsId",
                "dnsString",
                "network",
                "dockerCommand",
                "mountsString",
                "volumesFromString",
                "environmentString",
                "hostname",
                "user1",
                "",
                128,
                256,
                0L,
                0L,
                42,
                102,
                "bindPorts",
                true,
                true,
                true,
                "macAddress",
                "extraHostsString");
        templateBase.setCapabilitiesToAddString("SYS_ADMIN");
        templateBase.setCapabilitiesToDropString("CHOWN");
        templateBase.setSecurityOptsString("seccomp=unconfined");
        final DockerTemplate template = new DockerTemplate(
                templateBase, new DockerComputerAttachConnector("jenkins"), "labelString", "remoteFs", "10");
        template.setPullStrategy(DockerImagePullStrategy.PULL_NEVER);
        template.setMode(Node.Mode.NORMAL);
        template.setRemoveVolumes(true);
        template.setStopTimeout(42);
        template.setRetentionStrategy(new DockerOnceRetentionStrategy(33));

        DockerCloud cloud = new DockerCloud(
                "docker", new DockerAPI(new DockerServerEndpoint("uri", "credentialsId")), List.of(template));

        jenkins.getInstance().clouds.replaceBy(Set.of(cloud));

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
        Assert.assertEquals(
                "DockerCloud.CONTAINERS_IN_PROGRESS is empty to start with",
                DockerCloud.CONTAINERS_IN_PROGRESS,
                Map.of());

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
        Assert.assertEquals(
                "DockerCloud.CONTAINERS_IN_PROGRESS is empty afterwards", DockerCloud.CONTAINERS_IN_PROGRESS, Map.of());
    }

    private static void assertCount(
            DockerCloud c1,
            DockerCloud c2,
            DockerTemplate i1,
            DockerTemplate i2,
            int c1i1,
            int c1i2,
            int c2i1,
            int c2i2) {
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

    @Test
    public void testRegistryCredentials() throws IOException {

        final CredentialsStore store = CredentialsProvider.lookupStores(jenkins.getInstance())
                .iterator()
                .next();
        StandardUsernamePasswordCredentials rc =
                new UsernamePasswordCredentialsImpl(SYSTEM, "pullCredentialsId", null, "test", "secret");
        store.addCredentials(Domain.global(), rc);

        // Test default registry / no tag
        DockerTemplateBase dtb1 = new DockerTemplateBase("user/image1");
        dtb1.setPullCredentialsId(rc.getId());
        AuthConfig authConfig = DockerCloud.getAuthConfig(
                dtb1.getRegistry(), jenkins.getInstance().getItemGroup());
        Assert.assertEquals("test", authConfig.getUsername());
        Assert.assertEquals("secret", authConfig.getPassword());
        Assert.assertEquals(AuthConfig.DEFAULT_SERVER_ADDRESS, authConfig.getRegistryAddress());

        // Test default registry / tag
        dtb1 = new DockerTemplateBase("user/image1:tag");
        dtb1.setPullCredentialsId(rc.getId());
        authConfig = DockerCloud.getAuthConfig(
                dtb1.getRegistry(), jenkins.getInstance().getItemGroup());
        Assert.assertEquals("test", authConfig.getUsername());
        Assert.assertEquals("secret", authConfig.getPassword());
        Assert.assertEquals(AuthConfig.DEFAULT_SERVER_ADDRESS, authConfig.getRegistryAddress());

        // Test custom registry / tag
        dtb1 = new DockerTemplateBase("my.docker.registry/repo/image1:tag");
        dtb1.setPullCredentialsId(rc.getId());
        authConfig = DockerCloud.getAuthConfig(
                dtb1.getRegistry(), jenkins.getInstance().getItemGroup());
        Assert.assertEquals("test", authConfig.getUsername());
        Assert.assertEquals("secret", authConfig.getPassword());
        Assert.assertEquals("https://my.docker.registry", authConfig.getRegistryAddress());

        // Test custom registry / port / tag
        dtb1 = new DockerTemplateBase("my.docker.registry:12345/repo/image1:tag");
        dtb1.setPullCredentialsId(rc.getId());
        authConfig = DockerCloud.getAuthConfig(
                dtb1.getRegistry(), jenkins.getInstance().getItemGroup());
        Assert.assertEquals("test", authConfig.getUsername());
        Assert.assertEquals("secret", authConfig.getPassword());
        Assert.assertEquals("https://my.docker.registry:12345", authConfig.getRegistryAddress());

        // Test custom registry / port / tag / sha
        dtb1 = new DockerTemplateBase("my.docker.registry:12345/repo/image@sha256:sha256");
        dtb1.setPullCredentialsId(rc.getId());
        authConfig = DockerCloud.getAuthConfig(
                dtb1.getRegistry(), jenkins.getInstance().getItemGroup());
        Assert.assertEquals("test", authConfig.getUsername());
        Assert.assertEquals("secret", authConfig.getPassword());
        Assert.assertEquals("https://my.docker.registry:12345", authConfig.getRegistryAddress());

        // Test V2
        dtb1 = new DockerTemplateBase("my.docker.registry:12345/namespace/repo/image1:tag");
        dtb1.setPullCredentialsId(rc.getId());
        authConfig = DockerCloud.getAuthConfig(
                dtb1.getRegistry(), jenkins.getInstance().getItemGroup());
        Assert.assertEquals("test", authConfig.getUsername());
        Assert.assertEquals("secret", authConfig.getPassword());
        Assert.assertEquals("https://my.docker.registry:12345", authConfig.getRegistryAddress());

        dtb1 = new DockerTemplateBase("my.docker.registry:12345/namespace/repo/image@sha256:sha256");
        dtb1.setPullCredentialsId(rc.getId());
        authConfig = DockerCloud.getAuthConfig(
                dtb1.getRegistry(), jenkins.getInstance().getItemGroup());
        Assert.assertEquals("test", authConfig.getUsername());
        Assert.assertEquals("secret", authConfig.getPassword());
        Assert.assertEquals("https://my.docker.registry:12345", authConfig.getRegistryAddress());
    }
}
