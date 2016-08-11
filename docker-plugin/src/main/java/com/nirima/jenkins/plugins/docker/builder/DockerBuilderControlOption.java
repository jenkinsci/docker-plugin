package com.nirima.jenkins.plugins.docker.builder;

import com.github.dockerjava.api.exception.DockerException;
import com.nirima.jenkins.plugins.docker.action.DockerLaunchAction;
import hudson.Launcher;
import hudson.model.*;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

/**
 * Root abstract class for DockerBuilderControls
 *
 * @author magnayn
 */
public abstract class DockerBuilderControlOption implements Describable<DockerBuilderControlOption>, Serializable {

    public abstract void execute(Run<?, ?> build, Launcher launcher, TaskListener listener)
            throws DockerException;

    /**
     * @return first DockerLaunchAction attached to build
     */
    protected DockerLaunchAction getLaunchAction(Run<?, ?> build) {
        List<DockerLaunchAction> launchActionList = build.getActions(DockerLaunchAction.class);
        DockerLaunchAction launchAction;
        if (launchActionList.size() > 0 ) {
            launchAction = launchActionList.get(0);
        } else {
            launchAction = new DockerLaunchAction();
            build.addAction(launchAction);
        }
        return launchAction;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Descriptor<DockerBuilderControlOption> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
    }
}
