package com.nirima.jenkins.plugins.docker.listener;

import com.nirima.jenkins.plugins.docker.DockerTemplate;
import hudson.Extension;
import hudson.model.InvisibleAction;
import hudson.model.Label;
import hudson.model.Project;
import hudson.model.Queue.Item;
import hudson.model.Queue.LeftItem;
import hudson.model.Queue.WaitingItem;
import hudson.model.labels.LabelAssignmentAction;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.QueueListener;
import hudson.model.queue.SubTask;
import hudson.slaves.Cloud;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerJobProperty;
import com.nirima.jenkins.plugins.docker.DockerJobTemplateProperty;

import javax.annotation.Nonnull;
import java.util.UUID;

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
                final String uuid = UUID.randomUUID().toString();
                final DockerTemplate template = jobTemplate.getTemplate().cloneWithLabel(uuid);
                ((DockerCloud) cloud).addJobTemplate(wi.getId(), template);
                wi.addAction(new DockerTemplateLabelAssignmentAction(uuid));
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
                final DockerJobTemplateProperty p = project.getProperty(DockerJobTemplateProperty.class);
                if (p != null) return p;

                // backward compatibility. DockerJobTemplateProperty used to be a nested object in DockerJobProperty
                DockerJobProperty property = project.getProperty(DockerJobProperty.class);
                if (property != null) {
                    return property.getDockerJobTemplate();
                }
            }
        }

        return null;
    }

    private static class DockerTemplateLabelAssignmentAction extends InvisibleAction implements LabelAssignmentAction {

        private final String uuid;

        private DockerTemplateLabelAssignmentAction(String uuid) {
            this.uuid = uuid;
        }

        @Override
        public Label getAssignedLabel(@Nonnull SubTask task) {
            return new LabelAtom(uuid);
        }
    }
}
