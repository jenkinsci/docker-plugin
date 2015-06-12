package com.nirima.jenkins.plugins.docker.strategy;

import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Queue;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.EphemeralNode;
import hudson.slaves.RetentionStrategy;

import org.jenkinsci.plugins.durabletask.executors.ContinuableExecutable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.util.TimeUnit2.MINUTES;
import static java.util.logging.Level.WARNING;

/**
 * Mix of {@link org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy} and {@link CloudRetentionStrategy}
 * that allows configure it parameters.
 * <p>
 * Retention strategy that allows a cloud slave to run only a single build before disconnecting.
 * A {@link ContinuableExecutable} does not trigger termination.
 */
public class DockerOnceRetentionStrategy extends CloudRetentionStrategy implements ExecutorListener {

    private static final Logger LOGGER = Logger.getLogger(DockerOnceRetentionStrategy.class.getName());

    private int idleMinutes = 0;

    /**
     * Creates the retention strategy.
     * @param idleMinutes number of minutes of idleness after which to kill the slave; serves a backup in case the strategy fails to detect the end of a task
     */
    @DataBoundConstructor
    public DockerOnceRetentionStrategy(int idleMinutes) {
        super(idleMinutes);
        this.idleMinutes = idleMinutes;
    }

    public int getIdleMinutes() {
        return idleMinutes;
    }

    @Override public void start(AbstractCloudComputer c) {
        if (c.getNode() instanceof EphemeralNode) {
            throw new IllegalStateException("May not use DockerOnceRetentionStrategy on an EphemeralNode: " + c);
        }
        super.start(c);
    }

    @Override public void taskAccepted(Executor executor, Queue.Task task) {}

    @Override public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        done(executor);
    }

    @Override public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        done(executor);
    }

    private void done(Executor executor) {
        AbstractCloudComputer<?> c = (AbstractCloudComputer) executor.getOwner();
        Queue.Executable exec = executor.getCurrentExecutable();
        if (exec instanceof ContinuableExecutable && ((ContinuableExecutable) exec).willContinue()) {
            LOGGER.log(Level.FINE, "not terminating {0} because {1} says it will be continued", new Object[] {c.getName(), exec});
            return;
        }
        LOGGER.log(Level.FINE, "terminating {0} since {1} seems to be finished", new Object[] {c.getName(), exec});
        c.setAcceptingTasks(false); // just in case
        // Best to kill them off ASAP; otherwise NodeProvisioner does nothing until ComputerRetentionWork has run, causing poor throughput:
        try {
            c.getNode().terminate();
        } catch (InterruptedException x) {
            LOGGER.log(Level.WARNING, null, x);
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
        }
        // TODO calling NodeProvisioner.suggestReviewNow here does not seem to help push things along at all
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    @Restricted(NoExternalUse.class)
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    public static final class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
        @Override
        public String getDisplayName() {
            return "Docker Once Retention Strategy";
        }
    }

}
