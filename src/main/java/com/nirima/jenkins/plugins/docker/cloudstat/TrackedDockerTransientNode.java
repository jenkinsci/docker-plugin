package com.nirima.jenkins.plugins.docker.cloudstat;

import static java.util.Objects.nonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import io.jenkins.docker.DockerComputer;
import io.jenkins.docker.DockerTransientNode;
import java.io.IOException;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedItem;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.slf4j.Logger;

@Restricted(NoExternalUse.class)
class TrackedDockerTransientNode extends DockerTransientNode implements TrackedItem {

    private static final long serialVersionUID = 1;

    private ProvisioningActivity.Id id;

    @Restricted(NoExternalUse.class)
    TrackedDockerTransientNode(
            @NonNull String nodeName,
            @NonNull String containerId,
            @NonNull String workdir,
            @NonNull ComputerLauncher launcher)
            throws Descriptor.FormException, IOException {
        super(nodeName, containerId, workdir, launcher);
    }

    @Restricted(NoExternalUse.class)
    public void setId(@Nullable ProvisioningActivity.Id id) {
        this.id = id;
    }

    @Nullable
    @Override
    public ProvisioningActivity.Id getId() {
        return id;
    }

    @Override
    public DockerComputer createComputer() {
        return new TrackedDockerComputer(this, id);
    }

    @Override
    public void terminate(Logger logger) {
        try {
            super.terminate(logger);
        } finally {
            _terminate();
        }
    }

    @Override
    public void _terminate(TaskListener listener) {
        try {
            super._terminate(listener);
        } finally {
            _terminate();
        }
    }

    private void _terminate() {
        ProvisioningActivity activity = CloudStatistics.get().getActivityFor(this);
        if (nonNull(activity)) {
            activity.enterIfNotAlready(ProvisioningActivity.Phase.COMPLETED);
        }
    }
}
