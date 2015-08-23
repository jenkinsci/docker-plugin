package com.nirima.jenkins.plugins.docker.client;

import com.github.dockerjava.jaxrs.DockerCmdExecFactoryImpl;
import com.nirima.jenkins.plugins.docker.DockerCloud;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerCmdExecConfigBuilderForPlugin {

    private Integer readTimeout;
    private Integer connectTimeout;

    private DockerCmdExecConfigBuilderForPlugin() {
    }

    public static DockerCmdExecConfigBuilderForPlugin builder() {
        return new DockerCmdExecConfigBuilderForPlugin();
    }

    public DockerCmdExecConfigBuilderForPlugin forCloud(DockerCloud dockerCloud) {
        readTimeout = dockerCloud.readTimeout;
        connectTimeout = dockerCloud.getConnectTimeout();
        return this;
    }

    public DockerCmdExecConfigBuilderForPlugin withReadTimeout(Integer readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    public DockerCmdExecConfigBuilderForPlugin withConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public DockerCmdExecConfig build() {
        return new DockerCmdExecConfig(readTimeout, connectTimeout);
    }
}
