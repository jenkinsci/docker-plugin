package com.nirima.jenkins.plugins.docker.client;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.jaxrs.DockerCmdExecFactoryImpl;
import org.apache.commons.lang.Validate;

import java.io.Serializable;
import java.util.ServiceLoader;

/**
 * Builds ClientConfig with helper methods that extracts info for plugin routines.
 *
 * @author magnayn
 */
public class ClientBuilderForPlugin {

    private DockerClientConfig config;
    private DockerCmdExecFactory dockerCmdExecFactory;

    private ClientBuilderForPlugin() {
    }

    private ClientBuilderForPlugin(DockerClientConfig config, DockerCmdExecFactory dockerCmdExecFactory) {
        this.config = config;
        this.dockerCmdExecFactory = dockerCmdExecFactory;
    }

    public ClientBuilderForPlugin withDockerCmdExecFactory(DockerCmdExecFactory dockerCmdExecFactory) {
        this.dockerCmdExecFactory = dockerCmdExecFactory;
        return this;
    }

    public ClientBuilderForPlugin withDockerCmdExecConfig(DockerCmdExecConfig config) {
        this.dockerCmdExecFactory = new DockerCmdExecFactoryImpl()
                .withReadTimeout(config.getReadTimeoutMillis())
                .withConnectTimeout(config.getConnectTimeout());
        return this;
    }

    public static ClientBuilderForPlugin builder() {
        return new ClientBuilderForPlugin();
    }

    public ClientBuilderForPlugin withDockerClientConfig(DockerClientConfig clientConfig) {
        this.config = clientConfig;
        return this;
    }

    public DockerClient build() {
        Validate.notNull(config, "ClientConfig must be set");
        Validate.notNull(dockerCmdExecFactory, "DockerCmdExecFactory must be set");

        return DockerClientBuilder.getInstance(config)
                .withDockerCmdExecFactory(dockerCmdExecFactory)
                .build();
    }
}
