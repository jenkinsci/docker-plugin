package com.nirima.jenkins.plugins.docker.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import hudson.model.Label;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import io.jenkins.docker.client.DockerAPI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class JenkinsUtilsTest {

    @Test
    void getCloudByNameOrThrowGivenNameThenReturnsCloud(JenkinsRule jenkins) throws Exception {
        // Given
        final DockerAPI dockerApi = new DockerAPI(new DockerServerEndpoint("uri", "credentialsId"));
        final String expectedCloudName = "expectedCloudName";
        final DockerCloud cloudExpected = new DockerCloud(expectedCloudName, dockerApi, Collections.emptyList());
        final DockerCloud cloudOther = new DockerCloud("otherCloudName", dockerApi, Collections.emptyList());
        final OtherTypeOfCloud cloudForeign = new OtherTypeOfCloud("foreign");
        final List<Cloud> clouds = List.of(cloudOther, cloudExpected, cloudForeign);
        jenkins.getInstance().clouds.replaceBy(clouds);

        // When
        final DockerCloud actual = JenkinsUtils.getCloudByNameOrThrow(expectedCloudName);

        // Then
        assertThat(actual, sameInstance(cloudExpected));
    }

    @Test
    void getCloudByNameOrThrowGivenUnknownNameThenThrows(JenkinsRule jenkins) throws Exception {
        // Given
        final DockerAPI dockerApi = new DockerAPI(new DockerServerEndpoint("uri", "credentialsId"));
        final String requestedCloudName = "anUnknownCloudName";
        final String cloudName1 = "expectedCloudName";
        final DockerCloud cloud1 = new DockerCloud(cloudName1, dockerApi, Collections.emptyList());
        final String cloudName2 = "otherCloudName";
        final DockerCloud cloud2 = new DockerCloud(cloudName2, dockerApi, Collections.emptyList());
        final OtherTypeOfCloud cloudForeign = new OtherTypeOfCloud("foreign");
        final List<Cloud> clouds = List.of(cloud2, cloud1, cloudForeign);
        jenkins.getInstance().clouds.replaceBy(clouds);

        // When
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> JenkinsUtils.getCloudByNameOrThrow(requestedCloudName));

        // Then
        final String actualMsg = ex.getMessage();
        assertThat(actualMsg, containsString(requestedCloudName));
        assertThat(actualMsg, containsString(cloudName1));
        assertThat(actualMsg, containsString(cloudName2));
        assertThat(actualMsg, not(containsString(cloudForeign.name)));
    }

    @Test
    void getCloudByNameOrThrowGivenForeignCloudNameThenThrows(JenkinsRule jenkins) throws Exception {
        // Given
        final DockerAPI dockerApi = new DockerAPI(new DockerServerEndpoint("uri", "credentialsId"));
        final String requestedCloudName = "foreign";
        final String cloudName1 = "DockerCloud1Name";
        final DockerCloud cloud1 = new DockerCloud(cloudName1, dockerApi, Collections.emptyList());
        final String cloudName2 = "DockerCloud2Name";
        final DockerCloud cloud2 = new DockerCloud(cloudName2, dockerApi, Collections.emptyList());
        final OtherTypeOfCloud cloudForeign = new OtherTypeOfCloud(requestedCloudName);
        final List<Cloud> clouds = List.of(cloud2, cloud1, cloudForeign);
        jenkins.getInstance().clouds.replaceBy(clouds);

        // When
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> JenkinsUtils.getCloudByNameOrThrow(requestedCloudName));

        // Then
        final String actualMsg = ex.getMessage();
        assertThat(actualMsg, containsString(requestedCloudName));
        assertThat(actualMsg, containsString(cloudName1));
        assertThat(actualMsg, containsString(cloudName2));
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
