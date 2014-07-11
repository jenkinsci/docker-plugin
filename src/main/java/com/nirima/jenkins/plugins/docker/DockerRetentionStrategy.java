package com.nirima.jenkins.plugins.docker;

import hudson.model.Descriptor;
import hudson.slaves.RetentionStrategy;
import hudson.util.TimeUnit2;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DockerRetentionStrategy extends RetentionStrategy<DockerComputer> {

    private AtomicBoolean currentlyChecking;

    @DataBoundConstructor
    public DockerRetentionStrategy() {
        currentlyChecking = new AtomicBoolean(false);
    }

    @Override
    public long check(DockerComputer c) {
        LOGGER.log(Level.INFO, "Checking " + c);

        LOGGER.log(Level.INFO, "currentlyChecking: " + currentlyChecking);

        synchronized (this) {
            if (currentlyChecking == null) {
                currentlyChecking = new AtomicBoolean(false);
            }
        }

        // We only want to check once at a time.
        if (!currentlyChecking.compareAndSet(false, true)) {
            return 1;
        }

        boolean shouldTerminate = false;

        try {
            synchronized (this) {
                if (c.isIdle() && c.isOnline() && !disabled && c.haveWeRunAnyJobs()) {
                    shouldTerminate = true;
                }
                if (!c.isAcceptingTasks()) {
                    shouldTerminate = true;
                }
            }

            // TODO: really think about the right strategy here
            if (shouldTerminate) {
                final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
                if (idleMilliseconds > 0) {
                    LOGGER.info("Idle timeout: " + c.getName());
                    LOGGER.log(Level.INFO, "Terminating " + c);
                    try {
                        c.getNode().retentionTerminate();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        } finally {
            currentlyChecking.set(false);
        }
        return 1;
    }

    /**
     * Try to connect to it ASAP.
     */
    @Override
    public void start(DockerComputer c) {
        c.connect(false);
    }

    // no registration since this retention strategy is used only for EC2 nodes that we provision automatically.
    // @Extension
    public static class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {

        @Override
        public String getDisplayName() {
            return "Docker";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(DockerRetentionStrategy.class.getName());

    public static boolean disabled = Boolean.getBoolean(DockerRetentionStrategy.class.getName() + ".disabled");
}
