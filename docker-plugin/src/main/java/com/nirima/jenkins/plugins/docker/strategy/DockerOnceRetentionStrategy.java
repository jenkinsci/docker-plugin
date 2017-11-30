package com.nirima.jenkins.plugins.docker.strategy;

import io.jenkins.docker.DockerComputer;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Queue;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.EphemeralNode;
import hudson.slaves.RetentionStrategy;
import io.jenkins.docker.DockerTransientNode;
import org.jenkinsci.plugins.durabletask.executors.ContinuableExecutable;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.util.TimeUnit2.MINUTES;

/**
 * Mix of {@link org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy} (1.3) and {@link CloudRetentionStrategy}
 * that allows configure it parameters and has Descriptor.
 * <p/>
 * Retention strategy that allows a cloud slave to run only a single build before disconnecting.
 * A {@link ContinuableExecutable} does not trigger termination.
 */
public class DockerOnceRetentionStrategy extends RetentionStrategy<DockerComputer> implements ExecutorListener {

    private static final Logger LOGGER = Logger.getLogger(DockerOnceRetentionStrategy.class.getName());

    private int timeout = 10;

    /**
     * Creates the retention strategy.
     *
     * @param idleMinutes number of minutes of idleness after which to kill the slave; serves a backup in case the strategy fails to detect the end of a task
     */
    @DataBoundConstructor
    public DockerOnceRetentionStrategy(int idleMinutes) {
        this.timeout = idleMinutes;
    }

    public int getIdleMinutes() {
        return timeout;
    }

    @Override
    public long check(@Nonnull DockerComputer c) {
        // When the slave is idle we should disable accepting tasks and check to see if it is already trying to
        // terminate. If it's not already trying to terminate then lets terminate manually.
        if (c.isIdle()) {
            final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
            if (idleMilliseconds > MINUTES.toMillis(timeout)) {
                LOGGER.log(Level.FINE, "Disconnecting {0}", c.getName());
                done(c);
            }
        }

        // Return one because we want to check every minute if idle.
        return 1;
    }

    @Override
    public void start(DockerComputer c) {
        if (c.getNode() instanceof EphemeralNode) {
            throw new IllegalStateException("May not use OnceRetentionStrategy on an EphemeralNode: " + c);
        }
        c.connect(true);
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        done(executor);
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        done(executor);
    }

    private void done(Executor executor) {
        final DockerComputer c = (DockerComputer) executor.getOwner();
        Queue.Executable exec = executor.getCurrentExecutable();
        if (exec instanceof ContinuableExecutable && ((ContinuableExecutable) exec).willContinue()) {
            LOGGER.log(Level.FINE, "not terminating {0} because {1} says it will be continued", new Object[]{c.getName(), exec});
            return;
        }
        LOGGER.log(Level.FINE, "terminating {0} since {1} seems to be finished", new Object[]{c.getName(), exec});
        done(c);
    }

    private synchronized void done(final DockerComputer c) {
        c.setAcceptingTasks(false); // just in case
        Computer.threadPoolForRemoting.submit(() -> {
            Queue.withLock( () -> {
                DockerTransientNode node = c.getNode();
                if (node != null) {
                    node.terminate(c.getListener());
                }
            });
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DockerOnceRetentionStrategy that = (DockerOnceRetentionStrategy) o;

        return timeout == that.timeout;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
        @Override
        public String getDisplayName() {
            return "Use container only once";
        }
    }

}
