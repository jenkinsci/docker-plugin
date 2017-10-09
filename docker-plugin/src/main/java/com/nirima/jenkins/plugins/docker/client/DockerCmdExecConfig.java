package com.nirima.jenkins.plugins.docker.client;

import java.io.Serializable;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Serializable object that store options for {@see com.github.dockerjava.jaxrs.DockerCmdExecFactoryImpl}
 * Required for {@see #DockerBuilderPublisher.class} that builds DockerClient on slave side
 *
 * @author Kanstantsin Shautsou
 */
public class DockerCmdExecConfig implements Serializable {
    private final Integer readTimeout; //sec
    private final Integer connectTimeout; //sec

    /**
     * @param readTimeout Value as is from docker-plugin DockerCloud configuration
     */
    public DockerCmdExecConfig(Integer readTimeout, Integer connectTimeout) {
        this.readTimeout = readTimeout;
        this.connectTimeout = connectTimeout;
    }

    public Integer getReadTimeout() {
        return readTimeout;
    }

    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Helper methods that returns sec in ms for @see com.github.dockerjava.jaxrs.DockerCmdExecFactoryImpl
     */
    public Integer getReadTimeoutMillis() {
        if (readTimeout != null) {
            return (int) SECONDS.toMillis(readTimeout);
        } else {
            return null;
        }
    }

    public Integer getConnectTimeoutMillis() {
        if (connectTimeout != null) {
            return (int) SECONDS.toMillis(connectTimeout);
        } else {
            return null;
        }
    }
}
