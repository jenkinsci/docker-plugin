package com.nirima.jenkins.plugins.docker.builder;

import static org.hamcrest.CoreMatchers.containsString;

import org.junit.Assert;
import org.junit.Test;

public class DockerBuilderPublisherTest {
    private static final String VALID1 = "myimage";
    private static final String VALID2 = "my/image-2";
    private static final String VALID3 = "myimage_3:tag";
    private static final String VALID4 = "myimage.4:Tag4";
    private static final String VALID5 = "localhost/myimage";
    private static final String VALID6 = "localhost.localdomain/myimage:My_Tag-6";
    private static final String VALID7 = "my.repository.com:1234/myimage.7";
    private static final String VALID8 = "my.repository.com:${PORT_NUMBER}/myimage.7";
    private static final String VALID9 = "localhost.localdomain:5000/myimage:${JOB_NAME}-latest";
    private static final String VALID10 = "localhost.localdomain:5000/myimage:${JOB_NAME}-${BUILD_NUMBER}";
    private static final String INVALID1 = "MyImagei1";
    private static final String INVALID2 = "myimage%";
    private static final String INVALID3 = "1.2.3.4:abc/myimage:invalid3";
    private static final String INVALID4 = "funnyhost£name:5000/myimage4";

    @Test
    public void verifyTagsGivenValidTagsThenPasses() {
        // Given
        final String validTags = String.join("\n", VALID1, VALID2, VALID3, VALID4, VALID5, VALID6, VALID7, VALID8,
                VALID9, VALID10);

        // When
        DockerBuilderPublisher.verifyTags(validTags);

        // Then
        // no exception thrown
    }

    @Test
    public void verifyTagsGivenInvalidTag1ThenThrows() {
        // Given
        final String invalidTag = INVALID1;
        final String tags = String.join("\n", VALID1, invalidTag, VALID2);

        // When
        try {
            DockerBuilderPublisher.verifyTags(tags);
            Assert.fail("Expected exception");
        } catch (IllegalArgumentException ex) {
            // Then
            Assert.assertThat(ex.getMessage(), containsString(invalidTag));
        }
    }

    @Test
    public void verifyTagsGivenInvalidTag2ThenThrows() {
        // Given
        final String invalidTag = INVALID2;
        final String tags = String.join("\n", VALID1, invalidTag, VALID2);

        // When
        try {
            DockerBuilderPublisher.verifyTags(tags);
            Assert.fail("Expected exception");
        } catch (IllegalArgumentException ex) {
            // Then
            Assert.assertThat(ex.getMessage(), containsString(invalidTag));
        }
    }

    @Test
    public void verifyTagsGivenInvalidTag3ThenThrows() {
        // Given
        final String invalidTag = INVALID3;
        final String tags = String.join("\n", VALID1, invalidTag, VALID2);

        // When
        try {
            DockerBuilderPublisher.verifyTags(tags);
            Assert.fail("Expected exception");
        } catch (IllegalArgumentException ex) {
            // Then
            Assert.assertThat(ex.getMessage(), containsString(invalidTag));
        }
    }

    @Test
    public void verifyTagsGivenInvalidTag4ThenThrows() {
        // Given
        final String invalidTag = INVALID4;
        final String tags = String.join("\n", VALID1, invalidTag, VALID2);

        // When
        try {
            DockerBuilderPublisher.verifyTags(tags);
            Assert.fail("Expected exception");
        } catch (IllegalArgumentException ex) {
            // Then
            Assert.assertThat(ex.getMessage(), containsString(invalidTag));
        }
    }
}
