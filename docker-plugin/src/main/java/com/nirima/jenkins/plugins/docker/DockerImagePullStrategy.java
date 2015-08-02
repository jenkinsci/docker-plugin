package com.nirima.jenkins.plugins.docker;

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
}
