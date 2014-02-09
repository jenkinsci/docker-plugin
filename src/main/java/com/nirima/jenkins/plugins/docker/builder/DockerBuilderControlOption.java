package com.nirima.jenkins.plugins.docker.builder;

import com.nirima.docker.client.DockerException;
import com.nirima.jenkins.plugins.docker.action.DockerLaunchAction;
import hudson.model.AbstractBuild;
import hudson.model.Describable;
import jenkins.model.Jenkins;

import java.util.List;
import java.util.logging.Logger;

/**
 * Created by magnayn on 30/01/2014.
 */
public abstract class DockerBuilderControlOption implements Describable<DockerBuilderControlOption> {
    protected static final Logger LOGGER = Logger.getLogger(DockerBuilderControl.class.getName());

    public abstract void execute(AbstractBuild<?, ?> build) throws DockerException;

    protected DockerLaunchAction getLaunchAction(AbstractBuild<?, ?> build) {
        List<DockerLaunchAction> launchActionList = build.getActions(DockerLaunchAction.class);
        DockerLaunchAction launchAction;
        if( launchActionList.size() > 0 ) {
            launchAction = launchActionList.get(0);
        } else {
            launchAction = new DockerLaunchAction();
            build.addAction(launchAction);
        }
        return launchAction;
    }

    public DockerBuilderControlOptionDescriptor getDescriptor() {
        return (DockerBuilderControlOptionDescriptor) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }
}
