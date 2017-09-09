package com.nirima.jenkins.plugins.docker.launcher;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.nirima.jenkins.plugins.docker.DockerComputer;
import com.nirima.jenkins.plugins.docker.DockerTemplate;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.TimeUnit2;
import jenkins.model.Jenkins;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shaded.com.google.common.annotations.Beta;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

/**
 * JNLP launcher. Doesn't require open ports on docker host.
 * <p/>
 * Steps:
 * - runs container with nop command
 * - as launch action executes jnlp connection to master
 *
 * @author Kanstantsin Shautsou
 */
@Beta
public class DockerComputerJNLPLauncher extends DockerComputerLauncher {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerComputerJNLPLauncher.class);

    /**
     * Configured from UI
     */
    protected JNLPLauncher jnlpLauncher;

    protected String user;

    @DataBoundConstructor
    public DockerComputerJNLPLauncher(JNLPLauncher jnlpLauncher) {
        this.jnlpLauncher = jnlpLauncher;
    }

    public JNLPLauncher getJnlpLauncher() {
        return jnlpLauncher;
    }

    @DataBoundSetter
    public void setUser(String user) {
        this.user = user;
    }

    public String getUser() {
        return user;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerComputerLauncher> {

        @Override
        public String getDisplayName() {
            return "Docker JNLP launcher";
        }
    }


}
