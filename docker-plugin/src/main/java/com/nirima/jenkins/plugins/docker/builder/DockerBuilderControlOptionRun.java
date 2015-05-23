package com.nirima.jenkins.plugins.docker.builder;

import shaded.com.google.common.base.Strings;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerException;
import com.nirima.jenkins.plugins.docker.DockerSimpleTemplate;
import com.nirima.jenkins.plugins.docker.DockerTemplateBase;

import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;

/**
 * Created by magnayn on 30/01/2014.
 */
public class DockerBuilderControlOptionRun extends DockerBuilderControlCloudOption {

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
    public DockerBuilderControlOptionRun( String cloudName,
            String image,
            String lxcConfString,
            String dnsString,
            String dockerCommand,
            String volumesString, String volumesFrom,
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
    public void execute(AbstractBuild<?, ?> build) throws DockerException, IOException {
        DockerClient client = getClient(build);

        // Expand some token macros

        String xImage    = expand(build, image);
        String xCommand  = expand(build, dockerCommand);
        String xHostname = expand(build, hostname);


        LOGGER.info("Pulling image " + xImage);

        InputStream result = client.pullImageCmd(xImage).exec();

        String strResult = IOUtils.toString(result);
        LOGGER.log(Level.INFO, "Pull result = {0}", strResult);

        LOGGER.log(Level.INFO, "Starting container for image {0}", xImage);

        DockerTemplateBase template = new DockerSimpleTemplate(xImage,
                dnsString, xCommand,
                volumesString, volumesFrom, environmentsString, lxcConfString, xHostname,
                memoryLimit, cpuShares, bindPorts, bindAllPorts, privileged, tty, macAddress);

        String containerId = template.provisionNew(client);

        LOGGER.log(Level.INFO, "Started container {0}", containerId);
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
    public static final class DescriptorImpl extends DockerBuilderControlOptionDescriptor  {
        @Override
        public String getDisplayName() {
            return "Run Container";
        }

    }



}
