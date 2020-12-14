package com.nirima.jenkins.plugins.docker.utils;

import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.nirima.jenkins.plugins.docker.DockerCloud;

import hudson.model.Label;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import io.jenkins.docker.client.DockerAPI;

public class JenkinsUtilsTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void getCloudByNameOrThrowGivenNameThenReturnsCloud() throws Exception {
        // Given
        final DockerAPI dockerApi = new DockerAPI(new DockerServerEndpoint("uri", "credentialsId"));
        final DockerCloud cloudEmpty = new DockerCloud("", dockerApi, Collections.emptyList());
        final String expectedCloudName = "expectedCloudName";
        final DockerCloud cloudExpected = new DockerCloud(expectedCloudName, dockerApi, Collections.emptyList());
        final DockerCloud cloudOther = new DockerCloud("otherCloudName", dockerApi, Collections.emptyList());
        final OtherTypeOfCloud cloudForeign = new OtherTypeOfCloud("foreign");
        final List<Cloud> clouds = Arrays.asList(cloudEmpty, cloudOther, cloudExpected, cloudForeign);
        jenkins.getInstance().clouds.replaceBy(clouds);

        // When
        final DockerCloud actual = JenkinsUtils.getCloudByNameOrThrow(expectedCloudName);

        // Then
        assertThat(actual, sameInstance(cloudExpected));
    }

    @Test
    public void getCloudByNameOrThrowGivenUnknownNameThenThrows() throws Exception {
        // Given
        final DockerAPI dockerApi = new DockerAPI(new DockerServerEndpoint("uri", "credentialsId"));
        final DockerCloud cloudEmpty = new DockerCloud("", dockerApi, Collections.emptyList());
        final String requestedCloudName = "anUnknownCloudName";
        final String cloudName1 = "expectedCloudName";
        final DockerCloud cloud1 = new DockerCloud(cloudName1, dockerApi, Collections.emptyList());
        final String cloudName2 = "otherCloudName";
        final DockerCloud cloud2 = new DockerCloud(cloudName2, dockerApi, Collections.emptyList());
        final OtherTypeOfCloud cloudForeign = new OtherTypeOfCloud("foreign");
        final List<Cloud> clouds = Arrays.asList(cloudEmpty, cloud2, cloud1, cloudForeign);
        jenkins.getInstance().clouds.replaceBy(clouds);

        try {
            // When
            JenkinsUtils.getCloudByNameOrThrow(requestedCloudName);
            Assert.fail("Expected " + IllegalArgumentException.class.getCanonicalName());
        } catch (IllegalArgumentException ex) {
            // Then
            final String actualMsg = ex.getMessage();
            assertThat(actualMsg, containsString(requestedCloudName));
            assertThat(actualMsg, containsString(cloudName1));
            assertThat(actualMsg, containsString(cloudName2));
            assertThat(actualMsg, not(containsString(cloudForeign.name)));
        }
    }

    @Test
    public void getCloudByNameOrThrowGivenForeignCloudNameThenThrows() throws Exception {
        // Given
        final DockerAPI dockerApi = new DockerAPI(new DockerServerEndpoint("uri", "credentialsId"));
        final DockerCloud cloudEmpty = new DockerCloud("", dockerApi, Collections.emptyList());
        final String requestedCloudName = "foreign";
        final String cloudName1 = "DockerCloud1Name";
        final DockerCloud cloud1 = new DockerCloud(cloudName1, dockerApi, Collections.emptyList());
        final String cloudName2 = "DockerCloud2Name";
        final DockerCloud cloud2 = new DockerCloud(cloudName2, dockerApi, Collections.emptyList());
        final OtherTypeOfCloud cloudForeign = new OtherTypeOfCloud(requestedCloudName);
        final List<Cloud> clouds = Arrays.asList(cloudEmpty, cloud2, cloud1, cloudForeign);
        jenkins.getInstance().clouds.replaceBy(clouds);

        try {
            // When
            JenkinsUtils.getCloudByNameOrThrow(requestedCloudName);
            Assert.fail("Expected " + IllegalArgumentException.class.getCanonicalName());
        } catch (IllegalArgumentException ex) {
            // Then
            final String actualMsg = ex.getMessage();
            assertThat(actualMsg, containsString(requestedCloudName));
            assertThat(actualMsg, containsString(cloudName1));
            assertThat(actualMsg, containsString(cloudName2));
        }
    }

    private static class OtherTypeOfCloud extends Cloud {
        protected OtherTypeOfCloud(String name) {
            super(name);
        }

        @Override
        public Collection<PlannedNode> provision(Label label, int excessWorkload) {
            return null;
        }

        @Override
        public boolean canProvision(Label label) {
            return false;
        }
    }
}
