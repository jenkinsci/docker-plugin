package com.nirima.jenkins.plugins.docker.strategy;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.OneOffExecutor;
import hudson.model.Queue;
import hudson.model.Queue.FlyweightTask;
import hudson.slaves.RetentionStrategy;
import hudson.util.FormValidation;
import io.jenkins.docker.DockerComputer;
import io.jenkins.docker.DockerTransientNode;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.durabletask.executors.ContinuableExecutable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Retention strategy that allows our docker agents to run only a single build
 * before disconnecting.
 * <ul>
 * <li>A {@link FlyweightTask} is considered "trivial" and does not trigger
 * termination.</li>
 * <li>A {@link OneOffExecutor} (typically used by a {@link FlyweightTask}) is
 * considered "trivial" and does not trigger termination.</li>
 * <li>A {@link ContinuableExecutable} where
 * {@link ContinuableExecutable#willContinue()} is true does not trigger
 * termination.</li>
 * <li>...but any other workload will trigger termination once the node is idle.
 * </li>
 * </ul>
 * Inspired by the logic in
 * {@link org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy}
 * (1.34) but with the kill/don't-kill decision made when we accept a task (not
 * when we complete it) and termination itself delayed until we've done all
 * ongoing work.
 */
public class DockerOnceRetentionStrategy extends RetentionStrategy<DockerComputer> implements ExecutorListener {

    private static final Logger LOGGER = Logger.getLogger(DockerOnceRetentionStrategy.class.getName());
    private static int DEFAULT_IDLEMINUTES = 10;
    private static long ONE_MILLISECOND_LESS_THAN_A_MINUTE = MINUTES.toMillis(1L) - 1L;

    private int idleMinutes = DEFAULT_IDLEMINUTES;
    /**
     * This will be null (the starting value) or {@link Boolean#TRUE} if our node
     * has done something non-trivial.
     */
    private Boolean terminateOnceDone;
    /**
     * This will be null (the starting value) whenever we have zero tasks in
     * progress. It's only non-null (and non-zero) when we have tasks in progress.
     */
    private Integer numberOfTasksInProgress;

    /**
     * Creates the retention strategy.
     *
     * @param idleMinutes number of minutes of idleness after which to kill the
     *                    agent; serves a backup in case the strategy fails to
     *                    detect the end of a task
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
    public long check(@NonNull DockerComputer c) {
        // When the agent is idle for too long we should terminate it.
        // This can happen if an agent was created but there's no workload for it to
        // process.
        final int maxIdleMinutes = getIdleMinutes();
        if (!computerIsIdle(c)) {
            // if we're not idle now then it'll be some time before we've been idle for long
            // enough to be worth re-checking.
            return maxIdleMinutes;
        }
        final long idleMilliseconds = currentMilliseconds() - computerIdleStartMilliseconds(c);
        final long maxIdleMilliseconds = MINUTES.toMillis(maxIdleMinutes);
        final long excessIdleMilliseconds = idleMilliseconds - maxIdleMilliseconds;
        if (excessIdleMilliseconds < 0L) {
            final long insufficientIdleMilliseconds = -excessIdleMilliseconds;
            final long insufficientIdleMinutesRoundedUp =
                    MILLISECONDS.toMinutes(insufficientIdleMilliseconds + ONE_MILLISECOND_LESS_THAN_A_MINUTE);
            return insufficientIdleMinutesRoundedUp; // check again once enough time has passed
        }
        LOGGER.log(
                Level.FINE,
                "Disconnecting {0} as it's been idle for {1}ms which is {2}ms more than the configured max of {3} minutes",
                new Object[] {computerName(c), idleMilliseconds, excessIdleMilliseconds, maxIdleMinutes});
        terminateContainer(c);
        return 1; // check again in 1 minute
    }

    @Override
    public void start(DockerComputer c) {
        // MAINTENANCE NOTE: This is what OnceRetentionStrategy does.
        c.connect(false);
    }

    @Override
    public synchronized void taskAccepted(Executor executor, Queue.Task task) {
        final int oldNumberOfTasksInProgress = getNumberOfTasksInProgress();
        final int newNumberOfTasksInProgress = oldNumberOfTasksInProgress + 1;
        setNumberOfTasksInProgress(newNumberOfTasksInProgress);
        if (task instanceof FlyweightTask || executor instanceof OneOffExecutor) {
            // these don't "consume" an executor so they don't trigger our "only use it
            // once" behaviour.
            LOGGER.log(Level.FINER, "Node {0} has started FlyweightTask {1}. Tasks in progress now={2}", new Object[] {
                executor.getOwner().getName(), task, newNumberOfTasksInProgress
            });
            return;
        }
        if (executor instanceof ContinuableExecutable && ((ContinuableExecutable) executor).willContinue()) {
            // this isn't Flyweight so it will consume an executor and leave us "dirty" and
            // in need of termination once we're done...
            // ... BUT this task is the first of a series and we don't want to terminate
            // before it's completed so we don't trigger our "only use it once" behaviour
            // here either.
            LOGGER.log(
                    Level.FINER,
                    "Node {0} has started non-FlyweightTask {1}. Tasks in progress now={2}. This is-a ContinuableExecutable where willContinue()=true so we leave ourselves open to the follow-on task(s).",
                    new Object[] {executor.getOwner().getName(), task, newNumberOfTasksInProgress});
            return;
        }
        // anything else will stop us accepting new stuff and ensure we terminate once
        // we're idle.
        setTerminateOnceDone(true);
        LOGGER.log(
                Level.FINER,
                "Node {0} has started non-FlyweightTask {1}. Tasks in progress now={2}. Container will be terminated once idle.",
                new Object[] {executor.getOwner().getName(), task, newNumberOfTasksInProgress});
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        done(executor, task);
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        done(executor, task);
    }

    private synchronized void done(Executor executor, Queue.Task task) {
        final int oldNumberOfTasksInProgress = getNumberOfTasksInProgress();
        final int newNumberOfTasksInProgress = oldNumberOfTasksInProgress - 1;
        setNumberOfTasksInProgress(newNumberOfTasksInProgress);
        if (newNumberOfTasksInProgress != 0) {
            LOGGER.log(Level.FINER, "Node {0} has completed Task {1}. Tasks in progress now={2}", new Object[] {
                executor.getOwner().getName(), task, newNumberOfTasksInProgress
            });
            return;
        }
        final boolean shouldTerminateOnceDone = getTerminateOnceDone();
        if (!shouldTerminateOnceDone) {
            LOGGER.log(
                    Level.FINER,
                    "Node {0} has completed Task {1}. Tasks in progress now={2}. Not terminating yet as only trivial work has been done.",
                    new Object[] {executor.getOwner().getName(), task, newNumberOfTasksInProgress});
            return;
        }
        final DockerComputer c = (DockerComputer) executor.getOwner();
        LOGGER.log(
                Level.FINE,
                "Node {0} has completed Task {1}. Tasks in progress now={2}. Terminating as non-trivial work has been done.",
                new Object[] {executor.getOwner().getName(), task, newNumberOfTasksInProgress});
        terminateContainer(c);
    }

    // Made accessible for unit-test use only
    @Restricted(NoExternalUse.class)
    protected boolean computerIsIdle(DockerComputer c) {
        return c.isIdle();
    }

    // Made accessible for unit-test use only
    @Restricted(NoExternalUse.class)
    protected long computerIdleStartMilliseconds(DockerComputer c) {
        return c.getIdleStartMilliseconds();
    }

    // Made accessible for unit-test use only
    @Restricted(NoExternalUse.class)
    protected long currentMilliseconds() {
        return System.currentTimeMillis();
    }

    // Made accessible for unit-test use only
    @Restricted(NoExternalUse.class)
    protected String computerName(DockerComputer c) {
        return c.getName();
    }

    // Made accessible for unit-test use only
    @Restricted(NoExternalUse.class)
    protected void terminateContainer(final DockerComputer c) {
        c.setAcceptingTasks(false); // just in case
        Computer.threadPoolForRemoting.submit(() -> {
            Queue.withLock(() -> {
                DockerTransientNode node = c.getNode();
                if (node != null) {
                    node._terminate(c.getListener());
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
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DockerOnceRetentionStrategy that = (DockerOnceRetentionStrategy) o;
        return idleMinutes == that.idleMinutes;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
        @Override
        public String getDisplayName() {
            return "Use docker container only once";
        }

        public FormValidation doCheckIdleMinutes(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }
    }
}
