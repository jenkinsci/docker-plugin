package com.nirima.jenkins.plugins.docker.builder;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerException;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerSimpleTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shaded.com.google.common.base.Strings;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

/**
 * Build step that allows run container through existed DockerCloud
 *
 * @author magnayn
 */
public class DockerBuilderControlOptionRun extends DockerBuilderControlCloudOption {
    private static final Logger LOG = LoggerFactory.getLogger(DockerBuilderControlOptionRun.class);

    public final String image;
    public final String dnsString;
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
    public final Integer cpuShares;
    public final boolean bindAllPorts;
    public final String macAddress;

    @DataBoundConstructor
    public DockerBuilderControlOptionRun(
            String cloudName,
            String image,
            String lxcConfString,
            String dnsString,
            String dockerCommand,
            String volumesString,
            String volumesFrom,
            String environmentsString,
            String hostname,
            Integer memoryLimit,
            Integer cpuShares,
            String bindPorts,
            boolean bindAllPorts,
            boolean privileged,
            boolean tty,
            String macAddress) {
        super(cloudName);
        this.image = image;

        this.lxcConfString = lxcConfString;
        this.dnsString = dnsString;
        this.dockerCommand = dockerCommand;
        this.volumesString = volumesString;
        this.volumesFrom = volumesFrom;
        this.environmentsString = environmentsString;
        this.privileged = privileged;
        this.tty = tty;
        this.hostname = hostname;
        this.bindPorts = bindPorts;
        this.memoryLimit = memoryLimit;
        this.cpuShares = cpuShares;
        this.bindAllPorts = bindAllPorts;
        this.macAddress = macAddress;
    }

    @Override
    public void execute(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws DockerException, IOException {
        final PrintStream llog = listener.getLogger();

        DockerClient client = getClient(build);

        String xImage = expand(build, image);
        String xCommand = expand(build, dockerCommand);
        String xHostname = expand(build, hostname);

        LOG.info("Pulling image {}", xImage);
        llog.println("Pulling image " + xImage);

        InputStream result = null;
        try {
            result = client.pullImageCmd(xImage).exec();
            String strResult = IOUtils.toString(result);

            LOG.info("Pull result = {}", strResult);
            llog.println("Pull result = " + strResult);
        } finally {
            if (result != null) {
                result.close();
            }
        }

        DockerTemplateBase template = new DockerSimpleTemplate(xImage,
                dnsString, xCommand,
                volumesString, volumesFrom, environmentsString, lxcConfString, xHostname,
                memoryLimit, cpuShares, bindPorts, bindAllPorts, privileged, tty, macAddress);

        LOG.info("Starting container for image {}", xImage);
        llog.println("Starting container for image " + xImage);
        String containerId = DockerCloud.runContainer(template, client, null);

        LOG.info("Started container {}", containerId);
        llog.println("Started container " + containerId);

        getLaunchAction(build).started(client, containerId);
    }

    private String expand(AbstractBuild<?, ?> build, String text) {
        try {
            if (!Strings.isNullOrEmpty(text)) {
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
