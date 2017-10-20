package com.nirima.jenkins.plugins.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Image;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

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

        boolean imageExists;
        try {
            client.inspectImageCmd(image).exec();
            imageExists = true;
        } catch (NotFoundException e) {
            imageExists = false;
        }

        return imageExists ?
                pullIfExists(image) :
                pullIfNotExists(image);
    }
}
