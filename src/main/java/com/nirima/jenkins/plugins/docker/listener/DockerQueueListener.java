package com.nirima.jenkins.plugins.docker.listener;

import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerJobProperty;
import com.nirima.jenkins.plugins.docker.DockerJobTemplateProperty;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.InvisibleAction;
import hudson.model.Label;
import hudson.model.ParametersAction;
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
                final DockerTemplate template = cloneForQueueItem(jobTemplate.getTemplate(), wi, uuid);
                cloud.addJobTemplate(wi.getId(), template);
                wi.addAction(new DockerTemplateLabelAssignmentAction(uuid, cloud.name));
            }
            return;
        }

        final Label label = wi.getAssignedLabel();
        if (label == null) {
            return;
        }
        for (DockerCloud cloud : DockerCloud.instances()) {
            for (DockerTemplate configuredTemplate : cloud.getTemplates(label)) {
                final String resolvedImage = resolveImage(configuredTemplate, wi);
                if (resolvedImage != null) {
                    final String uuid = UUID.randomUUID().toString();
                    cloud.addJobTemplate(wi.getId(), configuredTemplate.cloneWithImageAndLabel(resolvedImage, uuid));
                    wi.addAction(new DockerTemplateLabelAssignmentAction(uuid, cloud.name));
                    return;
                }
            }
        }
    }

    @Override
    public void onLeft(LeftItem li) {
        final DockerTemplateLabelAssignmentAction assignment = li.getAction(DockerTemplateLabelAssignmentAction.class);
        if (assignment != null) {
            final DockerCloud cloud = DockerCloud.getCloudByName(assignment.cloudName);
            if (cloud != null) {
                cloud.removeJobTemplate(li.getId());
            }
            return;
        }
        final DockerJobTemplateProperty jobTemplate = getJobTemplate(li);
        if (jobTemplate != null) {
            final DockerCloud cloud = DockerCloud.getCloudByName(jobTemplate.getCloudname());
            if (cloud != null) {
                cloud.removeJobTemplate(li.getId());
            }
        }
    }

    /**
     * Returns a resolved image, or null when the configured image contains no resolvable parameter.
     */
    static String resolveImage(DockerTemplate template, Item item) {
        final String configuredImage = template.getImage();
        if (!configuredImage.contains("${")) {
            return null;
        }
        final EnvVars variables = new EnvVars();
        final ParametersAction parameters = item.getAction(ParametersAction.class);
        if (parameters == null) {
            return null;
        }
        parameters.getParameters().forEach(parameter -> {
            if (parameter.getValue() != null) {
                final String value = String.valueOf(parameter.getValue());
                if (!value.isEmpty()) {
                    variables.put(parameter.getName(), value);
                }
            }
        });
        final String resolved = Util.replaceMacro(configuredImage, variables);
        return resolved.equals(configuredImage) || resolved.contains("${") ? null : resolved;
    }

    private static DockerTemplate cloneForQueueItem(DockerTemplate template, Item item, String label) {
        final String resolvedImage = resolveImage(template, item);
        return resolvedImage == null
                ? template.cloneWithLabel(label)
                : template.cloneWithImageAndLabel(resolvedImage, label);
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
        private final String cloudName;

        private DockerTemplateLabelAssignmentAction(String uuid, String cloudName) {
            this.uuid = uuid;
            this.cloudName = cloudName;
        }

        @Override
        public Label getAssignedLabel(@NonNull SubTask task) {
            return new LabelAtom(uuid);
        }
    }
}
