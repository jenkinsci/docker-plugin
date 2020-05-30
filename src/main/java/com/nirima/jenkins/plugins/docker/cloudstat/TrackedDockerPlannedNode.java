package com.nirima.jenkins.plugins.docker.cloudstat;

import com.nirima.jenkins.plugins.docker.DockerNodeFactory;
import hudson.model.Node;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedItem;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nullable;
import java.util.concurrent.Future;

import static org.jenkinsci.plugins.cloudstats.CloudStatistics.ProvisioningListener.get;

@Restricted(NoExternalUse.class)
class TrackedDockerPlannedNode extends DockerNodeFactory.DockerPlannedNode implements TrackedItem {

    private final ProvisioningActivity.Id id;

    @Restricted(NoExternalUse.class)
    public TrackedDockerPlannedNode(String displayName, Future<Node> future, int numExecutors,
                                    String cloud, String template, String node) {
        super(displayName, future, numExecutors);
        this.id = new ProvisioningActivity.Id(cloud,template,node);
    }



    @Override
    public void notifyFailure(Throwable e) {
        get().onFailure(id, e);
        super.notifyFailure(e);
    }

    @Override
    public void notifySuccess(Node node) {
        get().onComplete(id, node);
        super.notifySuccess(node);
        if(node instanceof TrackedDockerTransientNode){
            ((TrackedDockerTransientNode)node).setId(id);
        }
    }

    @Override
    public void notifyStarted() {
        get().onStarted(id);
        super.notifyStarted();
    }

    @Nullable
    @Override
    public ProvisioningActivity.Id getId() {
        return id;
    }
}
