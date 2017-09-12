package com.nirima.jenkins.plugins.docker.ws;

import static org.apache.commons.lang.StringUtils.isNotBlank;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.nirima.jenkins.plugins.docker.action.DockerBuildAction;
import com.nirima.jenkins.plugins.docker.utils.PathUtils;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.WorkspaceBrowser;


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

            if (action != null && isNotBlank(action.remoteFsMapping) && isNotBlank(action.remoteFs) && isNotBlank(action.slaveWorkspace)) {
                Path slaveWorkspaceDir = Paths.get(action.slaveWorkspace);
                Path slaveJenkinsHomeDir = Paths.get(action.remoteFs);
                Path masterJenkinsHomeDir = Paths.get(action.remoteFsMapping);

                Path masterWorkspaceDir = PathUtils.mapDirectoryToOtherRoot(slaveWorkspaceDir, slaveJenkinsHomeDir, masterJenkinsHomeDir);
                if (masterWorkspaceDir != null) {
                    return new FilePath(masterWorkspaceDir.toFile());
                }
            }
        }

        return null;
    }
}
