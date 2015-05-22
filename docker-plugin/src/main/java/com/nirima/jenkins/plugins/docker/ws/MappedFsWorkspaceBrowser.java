package com.nirima.jenkins.plugins.docker.ws;

import com.nirima.jenkins.plugins.docker.action.DockerBuildAction;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.WorkspaceBrowser;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.apache.commons.io.FileUtils.getFile;
import static org.apache.commons.lang.StringUtils.isNotBlank;


@Extension
public class MappedFsWorkspaceBrowser extends WorkspaceBrowser {

    private static final Logger LOGGER = Logger.getLogger(MappedFsWorkspaceBrowser.class.getName());

    public MappedFsWorkspaceBrowser() {
        LOGGER.log(Level.INFO, "{0} initializing...", this.getClass().getName());
    }

    @Override
    public FilePath getWorkspace(Job job) {
        Run lastBuild = job.getLastBuild();

        if (lastBuild != null) {
            DockerBuildAction action = lastBuild.getAction(DockerBuildAction.class);

            if (action != null && isNotBlank(action.remoteFsMapping)) {
                return new FilePath(getFile(new File(action.remoteFsMapping), "workspace", job.getName()));
            }
        }

        return null;
    }
}
