package com.nirima.jenkins.plugins.docker.strategy;

import io.jenkins.docker.DockerComputer;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.OneOffExecutor;
import hudson.model.Queue;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.RetentionStrategy;
import hudson.util.FormValidation;
import io.jenkins.docker.DockerTransientNode;
import org.jenkinsci.plugins.durabletask.executors.ContinuableExecutable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MINUTES;

import java.util.Objects;

/**
 * Mix of {@link org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy} (1.3) and {@link CloudRetentionStrategy}
 * that allows configure it parameters and has Descriptor.
 * <p>
 * Retention strategy that allows a cloud agent to run only a single build before disconnecting.
 * A {@link ContinuableExecutable} does not trigger termination.
 * </p>
 */
public class DockerOnceRetentionStrategy extends RetentionStrategy<DockerComputer> implements ExecutorListener {

    private static final Logger LOGGER = Logger.getLogger(DockerOnceRetentionStrategy.class.getName());
    private static int DEFAULT_IDLEMINUTES = 10;

    private int idleMinutes = DEFAULT_IDLEMINUTES;
    private Boolean terminateOnceDone;
    private Integer numberOfTasksInProgress;

    /**
     * Creates the retention strategy.
     *
     * @param idleMinutes number of minutes of idleness after which to kill the agent; serves a backup in case the strategy fails to detect the end of a task
     */
    @DataBoundConstructor
    public DockerOnceRetentionStrategy(int idleMinutes) {
        this.idleMinutes = idleMinutes;
        this.terminateOnceDone = null;
        this.numberOfTasksInProgress = null;
    }

    @DataBoundSetter
    public void setTerminateOnceDone(Boolean terminateOnceDone) {
        if (terminateOnceDone != null && terminateOnceDone.booleanValue()) {
            this.terminateOnceDone = Boolean.TRUE;
        } else {
            this.terminateOnceDone = null;
        }
    }

    @DataBoundSetter
    public void setNumberOfTasksInProgress(Integer numberOfTasksInProgress) {
        if (numberOfTasksInProgress != null && numberOfTasksInProgress.intValue() != 0) {
            this.numberOfTasksInProgress = numberOfTasksInProgress;
        } else {
            this.numberOfTasksInProgress = null;
        }
    }

    public int getIdleMinutes() {
        if (idleMinutes < 1) {
            idleMinutes = DEFAULT_IDLEMINUTES;
        }
        return idleMinutes;
    }

    public boolean getTerminateOnceDone() {
        return terminateOnceDone != null && terminateOnceDone.booleanValue();
    }

    public int getNumberOfTasksInProgress() {
        return numberOfTasksInProgress == null ? 0 : numberOfTasksInProgress.intValue();
    }

    @Override
    public long check(@Nonnull DockerComputer c) {
        // When the agent is idle we should disable accepting tasks and check to see if it is already trying to
        // terminate. If it's not already trying to terminate then lets terminate manually.
        if (c.isIdle()) {
            final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
            if (idleMilliseconds > MINUTES.toMillis(getIdleMinutes())) {
                LOGGER.log(Level.FINE, "Disconnecting {0}", c.getName());
                terminateContainer(c);
            }
        }

        // Return one because we want to check every minute if idle.
        return 1;
    }

    @Override
    public void start(DockerComputer c) {
        c.connect(false);
    }

    @Override
    public synchronized void taskAccepted(Executor executor, Queue.Task task) {
        final int oldNumberOfTasksInProgress = getNumberOfTasksInProgress();
        final int newNumberOfTasksInProgress = oldNumberOfTasksInProgress + 1;
        setNumberOfTasksInProgress(newNumberOfTasksInProgress);
        if (task instanceof Queue.FlyweightTask || executor instanceof OneOffExecutor) {
            // these don't "consume" an executor so they don't trigger our "only use it once" behaviour.
            LOGGER.log(Level.FINER, "Node {0} has started FlyweightTask {1}. Tasks in progress now={2}", new Object[]{executor.getOwner().getName(), task, newNumberOfTasksInProgress});
            return;
        }
        if (executor instanceof ContinuableExecutable && ((ContinuableExecutable) executor).willContinue() ) {
            // this isn't Flyweight so it will consume an executor and leave us "dirty" and in need of termination once we're done...
            // ... BUT this task is the first of a series and we don't want to terminate before it's completed so we don't trigger our "only use it once" behaviour here either.
            LOGGER.log(Level.FINER, "Node {0} has started non-FlyweightTask {1}. Tasks in progress now={2}. This is-a ContinuableExecutable where willContinue()=true so we leave ourselves open to the follow-on task(s).", new Object[]{executor.getOwner().getName(), task, newNumberOfTasksInProgress});
            return;
        }
        // anything else will stop us accepting new stuff and ensure we terminate once we're idle.
        setTerminateOnceDone(true);
        LOGGER.log(Level.FINER, "Node {0} has started non-FlyweightTask {1}. Tasks in progress now={2}. Container will be terminated once idle.", new Object[]{executor.getOwner().getName(), task, newNumberOfTasksInProgress});
    }

    @Override
    public synchronized void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        done(executor, task);
    }

    @Override
    public synchronized void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        done(executor, task);
    }

    private void done(Executor executor, Queue.Task task) {
        final int oldNumberOfTasksInProgress = getNumberOfTasksInProgress();
        final int newNumberOfTasksInProgress = oldNumberOfTasksInProgress - 1;
        setNumberOfTasksInProgress(newNumberOfTasksInProgress);
        if ( newNumberOfTasksInProgress!=0 ) {
            LOGGER.log(Level.FINER, "Node {0} has completed Task {1}. Tasks in progress now={2}", new Object[]{executor.getOwner().getName(), task, newNumberOfTasksInProgress});
            return;
        }
        final boolean shouldTerminateOnceDone = getTerminateOnceDone();
        if ( !shouldTerminateOnceDone ) {
            LOGGER.log(Level.FINER, "Node {0} has completed Task {1}. Tasks in progress now={2}. Not terminating yet as only Flyweight and/or Continuable work has been done.", new Object[]{executor.getOwner().getName(), task, newNumberOfTasksInProgress});
        }
        final DockerComputer c = (DockerComputer) executor.getOwner();
        LOGGER.log(Level.FINE, "Node {0} has completed Task {1}. Tasks in progress now={2}. Terminating as non-FlyweightTask work has been done.", new Object[]{executor.getOwner().getName(), task, newNumberOfTasksInProgress});
        terminateContainer(c);
    }

    private static void terminateContainer(final DockerComputer c) {
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
    public synchronized boolean isAcceptingTasks(DockerComputer c) {
        return !getTerminateOnceDone();
    }

    @Override
    public int hashCode() {
        return Objects.hash(idleMinutes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DockerOnceRetentionStrategy that = (DockerOnceRetentionStrategy) o;
        return idleMinutes == that.idleMinutes;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
        @Override
        public String getDisplayName() {
            return "Use container only once";
        }

        public FormValidation doCheckIdleMinutes(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }
    }
}
