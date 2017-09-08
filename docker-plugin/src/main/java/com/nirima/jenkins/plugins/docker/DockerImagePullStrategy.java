package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Image;
import shaded.com.google.common.base.Predicate;
import shaded.com.google.common.collect.Iterables;

import java.util.Arrays;
import java.util.List;

/**
 * @author Kanstantsin Shautsou
 */
public enum DockerImagePullStrategy {

    PULL_ALWAYS("Pull all images every time") {
        @Override
        public boolean pullIfNotExists(String imageName) {
            return true;
        }

        @Override
        public boolean pullIfExists(String imageName) {
            return true;
        }
    },
    PULL_LATEST("Pull once and update latest") {
        @Override
        public boolean pullIfNotExists(String imageName) {
            return true;
        }

        @Override
        public boolean pullIfExists(String imageName) {
            return imageName.endsWith(":latest");
        }
    },
    PULL_NEVER("Never pull") {
        @Override
        public boolean pullIfNotExists(String imageName) {
            return false;
        }

        @Override
        public boolean pullIfExists(String imageName) {
            return false;
        }
    };

    private final String description;

    DockerImagePullStrategy(String description) {
        this.description = description;
    }

    public String getDescription() {
        //TODO add {@link #Locale.class}?
        return description;
    }

    public abstract boolean pullIfNotExists(String imageName);

    public abstract boolean pullIfExists(String imageName);

    public boolean shouldPullImage(DockerClient client, String image) {
        // simply check without asking docker
        if (pullIfExists(image) && pullIfNotExists(image)) {
            return true;
        }

        if (!pullIfExists(image) && !pullIfNotExists(image)) {
            return false;
        }

        List<Image> images = client.listImagesCmd().exec();

        boolean imageExists = Iterables.any(images, new Predicate<Image>() {
            @Override
            public boolean apply(Image image) {
                if (image == null || image.getRepoTags() == null) {
                    return false;
                } else {
                    return Arrays.asList(image.getRepoTags()).contains(image);
                }
            }
        });

        return imageExists ?
                pullIfExists(image) :
                pullIfNotExists(image);
    }
}
