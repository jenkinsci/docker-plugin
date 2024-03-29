package com.nirima.jenkins.plugins.docker.listener;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerJobProperty;
import com.nirima.jenkins.plugins.docker.DockerJobTemplateProperty;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
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
        final DockerJobTemplateProperty jobTemplate = getJobTemplate(wi);
        if (jobTemplate != null) {
            final DockerCloud cloud = DockerCloud.getCloudByName(jobTemplate.getCloudname());
            if (cloud != null) {
                final String uuid = UUID.randomUUID().toString();
                final DockerTemplate template = jobTemplate.getTemplate().cloneWithLabel(uuid);
                cloud.addJobTemplate(wi.getId(), template);
                wi.addAction(new DockerTemplateLabelAssignmentAction(uuid));
            }
        }
    }

    @Override
    public void onLeft(LeftItem li) {
        final DockerJobTemplateProperty jobTemplate = getJobTemplate(li);
        if (jobTemplate != null) {
            final DockerCloud cloud = DockerCloud.getCloudByName(jobTemplate.getCloudname());
            if (cloud != null) {
                cloud.removeJobTemplate(li.getId());
            }
        }
    }

    /**
     * Helper method to determine the template from a given item.
     *
     * @param item Item which includes a template.
     * @return If the item includes a template then the template will be returned. Otherwise <code>null</code>.
     */
    @CheckForNull
    private static DockerJobTemplateProperty getJobTemplate(Item item) {
        if (item.task instanceof Project) {
            final Project<?, ?> project = (Project<?, ?>) item.task;
            final DockerJobTemplateProperty p = project.getProperty(DockerJobTemplateProperty.class);
            if (p != null) {
                return p;
            }
            // backward compatibility. DockerJobTemplateProperty used to be a nested object in DockerJobProperty
            final DockerJobProperty property = project.getProperty(DockerJobProperty.class);
            if (property != null) {
                return property.getDockerJobTemplate();
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
        public Label getAssignedLabel(@NonNull SubTask task) {
            return new LabelAtom(uuid);
        }
    }
}
