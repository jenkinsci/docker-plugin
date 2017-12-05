package com.nirima.jenkins.plugins.docker.builder;

import com.github.dockerjava.api.exception.DockerException;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.action.DockerLaunchAction;
import hudson.Launcher;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import hudson.util.ListBoxModel;
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
            throws DockerException, IOException;

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

    public static abstract class DockerBuilderControlOptionDescriptor extends Descriptor<DockerBuilderControlOption> {

        public ListBoxModel doFillCloudNameItems() {
            ListBoxModel model = new ListBoxModel();
            model.add("Cloud this build is running on", "");
            for (Cloud cloud : DockerCloud.instances()) {
                model.add(cloud.name);
            }
            return model;
        }

    }
}
