package com.nirima.jenkins.plugins.docker;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.InvisibleAction;
import hudson.model.Queue;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import jenkins.model.Jenkins;

/** Displays temporary Docker template provisioning failures on queued builds until Jenkins can retry them. */
@Extension
public class DockerQueueTaskDispatcher extends QueueTaskDispatcher {

    static void recordProvisioningFailure(
            Queue.Item item, DockerTemplate template, Throwable failure, long retryDelayMilliseconds) {
        recordProvisioningFailure(item, template, failure, retryDelayMilliseconds, System.currentTimeMillis());
        Jenkins.get().getQueue().scheduleMaintenance();
    }

    static void recordProvisioningFailure(
            Queue.Item item,
            DockerTemplate template,
            Throwable failure,
            long retryDelayMilliseconds,
            long currentTimeMillis) {
        final long retryAtMillis = currentTimeMillis + Math.max(0L, retryDelayMilliseconds);
        item.replaceAction(new ProvisioningFailureAction(
                template.getImage(),
                template.getPullStrategy().getDescription(),
                rootCauseMessage(failure),
                retryAtMillis));
    }

    @Override
    public CauseOfBlockage canRun(Queue.Item item) {
        return causeOfBlockage(item, System.currentTimeMillis());
    }

    @CheckForNull
    static CauseOfBlockage causeOfBlockage(Queue.Item item, long currentTimeMillis) {
        final ProvisioningFailureAction failure = item.getAction(ProvisioningFailureAction.class);
        if (failure == null) {
            return null;
        }
        if (failure.retryAtMillis <= currentTimeMillis) {
            item.removeAction(failure);
            return null;
        }
        final String description = failure.getDescription(currentTimeMillis);
        return new CauseOfBlockage() {
            @Override
            public String getShortDescription() {
                return description;
            }
        };
    }

    private static String rootCauseMessage(Throwable failure) {
        Throwable rootCause = failure;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        final String message = rootCause.getMessage();
        return message == null || message.isBlank() ? rootCause.getClass().getSimpleName() : message;
    }

    private static final class ProvisioningFailureAction extends InvisibleAction {
        private final String image;
        private final String pullStrategy;
        private final String failure;
        private final long retryAtMillis;

        private ProvisioningFailureAction(String image, String pullStrategy, String failure, long retryAtMillis) {
            this.image = image;
            this.pullStrategy = pullStrategy;
            this.failure = failure;
            this.retryAtMillis = retryAtMillis;
        }

        private String getDescription(long currentTimeMillis) {
            final String retryIn = Util.getTimeSpanString(retryAtMillis - currentTimeMillis);
            return "Docker image '" + image + "' provisioning failed (pull strategy: " + pullStrategy + "): " + failure
                    + ". Retrying in " + retryIn + ".";
        }
    }
}
