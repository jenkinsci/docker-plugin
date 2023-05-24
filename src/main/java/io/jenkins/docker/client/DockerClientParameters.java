package io.jenkins.docker.client;

import java.util.Objects;

class DockerClientParameters {
    final String dockerUri;
    final String credentialsId;
    final Integer readTimeoutInMsOrNull;
    final Integer connectTimeoutInMsOrNull;

    DockerClientParameters(
            String dockerUri, String credentialsId, Integer readTimeoutInMsOrNull, Integer connectTimeoutInMsOrNull) {
        this.dockerUri = dockerUri;
        this.credentialsId = credentialsId;
        this.readTimeoutInMsOrNull = readTimeoutInMsOrNull;
        this.connectTimeoutInMsOrNull = connectTimeoutInMsOrNull;
    }

    public String getDockerUri() {
        return dockerUri;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public Integer getReadTimeoutInMsOrNull() {
        return readTimeoutInMsOrNull;
    }

    public Integer getConnectTimeoutInMsOrNull() {
        return connectTimeoutInMsOrNull;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dockerUri, credentialsId, connectTimeoutInMsOrNull, readTimeoutInMsOrNull);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final DockerClientParameters other = (DockerClientParameters) obj;
        return Objects.equals(dockerUri, other.dockerUri)
                && Objects.equals(credentialsId, other.credentialsId)
                && Objects.equals(readTimeoutInMsOrNull, other.readTimeoutInMsOrNull)
                && Objects.equals(connectTimeoutInMsOrNull, other.connectTimeoutInMsOrNull);
    }

    @Override
    public String toString() {
        return "DockerClientParameters{" + "dockerUri='"
                + dockerUri + '\'' + ", credentialsId='"
                + credentialsId + '\'' + ", readTimeoutInMsOrNull="
                + readTimeoutInMsOrNull + ", connectTimeoutInMsOrNull="
                + connectTimeoutInMsOrNull + '}';
    }
}
