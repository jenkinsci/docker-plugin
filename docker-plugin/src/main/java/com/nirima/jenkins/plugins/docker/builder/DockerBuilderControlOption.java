package com.nirima.jenkins.plugins.docker.builder;

import com.github.dockerjava.api.DockerException;
import com.nirima.jenkins.plugins.docker.action.DockerLaunchAction;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Descriptor;
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

    public abstract void execute(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws DockerException, IOException;

    /**
     * @return first DockerLaunchAction attached to build
     */
    protected DockerLaunchAction getLaunchAction(AbstractBuild<?, ?> build) {
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
