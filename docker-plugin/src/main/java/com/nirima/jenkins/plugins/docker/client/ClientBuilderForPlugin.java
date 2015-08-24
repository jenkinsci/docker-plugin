package com.nirima.jenkins.plugins.docker.client;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.jaxrs.DockerCmdExecFactoryImpl;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ServiceLoader;

/**
 * Created by magnayn on 10/07/2015.
 */
public class ClientBuilderForPlugin  {

    private final DockerClientConfig config;
    private int readTimeout;

    private ClientBuilderForPlugin(DockerClientConfig config) {
        this.config = config;
    }

    public static ClientBuilderForPlugin getInstance(DockerClientConfig.DockerClientConfigBuilder dockerClientConfigBuilder) {
        return getInstance(dockerClientConfigBuilder.build());
    }

    public static ClientBuilderForPlugin getInstance(DockerClientConfig dockerClientConfig) {
        return new ClientBuilderForPlugin(dockerClientConfig);
    }

    public static ClientBuilderForPlugin getInstance(ClientConfigBuilderForPlugin dockerClientConfig) {
        return new ClientBuilderForPlugin(dockerClientConfig.build());
    }

    public ClientBuilderForPlugin withReadTimeout(int readTimeout) {
        if (readTimeout > 0) {
            this.readTimeout = (int) SECONDS.toMillis(readTimeout);
        }
        return this;
    }

    public DockerClient build() {
        DockerCmdExecFactoryImpl dockerCmdExecFactory = new DockerCmdExecFactoryImpl();
        if (readTimeout > 0) {
            dockerCmdExecFactory.withReadTimeout(readTimeout);
        }

        return DockerClientBuilder.getInstance(config)
                .withDockerCmdExecFactory(dockerCmdExecFactory)
                .build();

    }
}
