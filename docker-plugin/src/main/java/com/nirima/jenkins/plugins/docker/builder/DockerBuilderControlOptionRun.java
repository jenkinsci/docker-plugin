package com.nirima.jenkins.plugins.docker.builder;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.google.common.base.Strings;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerSimpleTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;

/**
 * Build step that allows run container through existed DockerCloud
 *
 * @author magnayn
 */
public class DockerBuilderControlOptionRun extends DockerBuilderControlCloudOption {
    private static final Logger LOG = LoggerFactory.getLogger(DockerBuilderControlOptionRun.class);

    public final String image;
    private String pullCredentialsId;
    private transient DockerRegistryEndpoint registry;
    public final String dnsString;
    public final String network;
    public final String dockerCommand;
    public final String volumesString;
    public final String volumesFrom;
    public final String environmentsString;
    public final String lxcConfString;
    public final boolean privileged;
    public final boolean tty;
    public final String hostname;
    public final String bindPorts;
    public final Integer memoryLimit;
    public final Integer memorySwap;
    public final Integer cpuShares;
    public final boolean bindAllPorts;
    public final String macAddress;

    @DataBoundConstructor
    public DockerBuilderControlOptionRun(
            String cloudName,
            String image,
            String pullCredentialsId,
            String lxcConfString,
            String dnsString,
            String network,
            String dockerCommand,
            String volumesString,
            String volumesFrom,
            String environmentsString,
            String hostname,
            Integer memoryLimit,
            Integer memorySwap,
            Integer cpuShares,
            String bindPorts,
            boolean bindAllPorts,
            boolean privileged,
            boolean tty,
            String macAddress) {
        super(cloudName);
        this.image = image;
        this.pullCredentialsId = pullCredentialsId;
        this.lxcConfString = lxcConfString;
        this.dnsString = dnsString;
        this.network = network;
        this.dockerCommand = dockerCommand;
        this.volumesString = volumesString;
        this.volumesFrom = volumesFrom;
        this.environmentsString = environmentsString;
        this.privileged = privileged;
        this.tty = tty;
        this.hostname = hostname;
        this.bindPorts = bindPorts;
        this.memoryLimit = memoryLimit;
        this.memorySwap = memorySwap;
        this.cpuShares = cpuShares;
        this.bindAllPorts = bindAllPorts;
        this.macAddress = macAddress;
    }

    public DockerRegistryEndpoint getRegistry() {
        if (registry == null) {
            registry = new DockerRegistryEndpoint(null, pullCredentialsId);
        }
        return registry;
    }

    @Override
    public void execute(Run<?, ?> build, Launcher launcher, TaskListener listener)
            throws DockerException, IOException {
        final PrintStream llog = listener.getLogger();

        DockerClient client = getCloud(build,launcher).getClient();

        String xImage = expand(build, image);
        String xCommand = expand(build, dockerCommand);
        String xHostname = expand(build, hostname);

        LOG.info("Pulling image {}", xImage);
        llog.println("Pulling image " + xImage);


        PullImageResultCallback resultCallback = new PullImageResultCallback() {
            public void onNext(PullResponseItem item) {
                if (item.getStatus() != null && item.getProgress() == null) {
                    llog.print(item.getId() + ":" + item.getStatus());
                    LOG.info("{} : {}", item.getId(), item.getStatus());
                }
                super.onNext(item);
            }
        };

        PullImageCmd cmd = client.pullImageCmd(xImage);
        DockerCloud.setRegistryAuthentication(cmd, getRegistry(), build.getParent().getParent());
        try {
            cmd.exec(resultCallback).awaitCompletion();
        } catch (InterruptedException e) {
            throw new DockerClientException("Interrupted while pulling image", e);
        }
        try {
            client.inspectImageCmd(xImage).exec();
        } catch (NotFoundException e) {
            throw new DockerClientException("Failed to pull image: " + image, e);
        }


        DockerTemplateBase template = new DockerSimpleTemplate(xImage, pullCredentialsId,
                dnsString, network, xCommand,
                volumesString, volumesFrom, environmentsString, lxcConfString, xHostname,
                memoryLimit, memorySwap, cpuShares, bindPorts, bindAllPorts, privileged, tty, macAddress);

        LOG.info("Starting container for image {}", xImage);
        llog.println("Starting container for image " + xImage);
        String containerId = DockerCloud.runContainer(template, client);

        LOG.info("Started container {}", containerId);
        llog.println("Started container " + containerId);

        getLaunchAction(build).started(client, containerId);
    }

    private String expand(Run<?, ?> build, String text) {
        try {
            if (build instanceof AbstractBuild && !Strings.isNullOrEmpty(text)) {
                text = TokenMacro.expandAll((AbstractBuild) build, TaskListener.NULL, text);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return text;
    }

    @Extension
    public static final class DescriptorImpl extends DockerBuilderControlOptionDescriptor {
        @Override
        public String getDisplayName() {
            return "Run Container";
        }
    }
}
