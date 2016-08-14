package com.nirima.jenkins.plugins.docker.listener;

import hudson.Extension;
import hudson.model.Project;
import hudson.model.Queue.Item;
import hudson.model.Queue.LeftItem;
import hudson.model.Queue.WaitingItem;
import hudson.model.queue.QueueListener;
import hudson.slaves.Cloud;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerJobProperty;
import com.nirima.jenkins.plugins.docker.DockerJobTemplateProperty;

/**
 * This listener handles templates which are configured in a project.
 * 
 * @author Ingo Rissmann
 */
@Extension
public class DockerQueueListener extends QueueListener {

    @Override
    public void onEnterWaiting(WaitingItem wi) {
        DockerJobTemplateProperty jobTemplate = getJobTemplate(wi);
        if (jobTemplate != null) {
            Cloud cloud = DockerCloud.getCloudByName(jobTemplate.getCloudname());
            if (cloud instanceof DockerCloud) {
                ((DockerCloud) cloud).addJobTemplate(wi.getId(), jobTemplate.getTemplate());
            }
        }
    }

    @Override
    public void onLeft(LeftItem li) {
        DockerJobTemplateProperty jobTemplate = getJobTemplate(li);
        if (jobTemplate != null) {
            Cloud cloud = DockerCloud.getCloudByName(jobTemplate.getCloudname());
            if (cloud instanceof DockerCloud) {
                ((DockerCloud) cloud).removeJobTemplate(li.getId());
            }
        }
    }

    /**
     * Helper method to determine the template from a given item.
     * 
     * @param item Item which includes a template.
     * @return If the item includes a template then the template will be returned. Otherwise <code>null</code>.
     */
    private DockerJobTemplateProperty getJobTemplate(Item item) {
        if (item.task instanceof Project) {
            Project<?, ?> project = (Project<?, ?>) item.task;
            if (project != null) {
                DockerJobProperty property = project.getProperty(DockerJobProperty.class);
                if (property != null) {
                    return property.getDockerJobTemplate();
                }
            }
        }

        return null;
    }
}
