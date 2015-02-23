package com.nirima.jenkins.plugins.docker.ws;

import com.google.common.base.Strings;
import com.nirima.jenkins.plugins.docker.action.DockerBuildAction;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.WorkspaceBrowser;
import java.io.File;
import java.util.logging.Logger;


@Extension
public class MappedFsWorkspaceBrowser extends WorkspaceBrowser  {

    private static final Logger LOGGER = Logger.getLogger(MappedFsWorkspaceBrowser.class.getName());

    public MappedFsWorkspaceBrowser() {
        LOGGER.info(this.getClass().getName() + " initializing.");
    }

    @Override
    public FilePath getWorkspace(Job job) {

        DockerBuildAction a = (DockerBuildAction) job.getLastBuild().getAction(DockerBuildAction.class);

        if (a!= null) {
            if (! Strings.isNullOrEmpty(a.remoteFsMapping)) {
                File mappedRemoteWorkspace = new File(a.remoteFsMapping);
                mappedRemoteWorkspace = new File(mappedRemoteWorkspace, "workspace");
                mappedRemoteWorkspace = new File(mappedRemoteWorkspace, job.getName());
                return new FilePath(mappedRemoteWorkspace);
            }
        }
        return null;

    }
}
