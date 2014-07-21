package com.nirima.jenkins.plugins.docker;

import hudson.model.Descriptor;
import hudson.slaves.RetentionStrategy;
import hudson.util.TimeUnit2;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;


public class DockerRetentionStrategy  extends RetentionStrategy<DockerComputer> {

    private AtomicBoolean currentlyChecking;

    /** Number of minutes of idleness before an instance should be terminated.
        A value of zero indicates that the instance should never be automatically terminated */
    public final int idleTerminationMinutes;

    @DataBoundConstructor
    public DockerRetentionStrategy(String idleTerminationMinutes) {
        currentlyChecking = new AtomicBoolean(false);

        if (idleTerminationMinutes == null || idleTerminationMinutes.trim() == "") {
            this.idleTerminationMinutes = 0;
        } else {
            int value = 30;
            try {
                value = Integer.parseInt(idleTerminationMinutes);
            } catch (NumberFormatException nfe) {
                LOGGER.info("Malformed idleTermination value: " + idleTerminationMinutes);
            }

            this.idleTerminationMinutes = value;
        }
    }

    @Override
    public long check(DockerComputer c) {
        LOGGER.log(Level.INFO, "Checking " + c);

        LOGGER.log(Level.INFO, "currentlyChecking: " + currentlyChecking);

        synchronized (this) {
            if (currentlyChecking == null)
                currentlyChecking = new AtomicBoolean(false);
        }

        // We only want to check once at a time.
        if( !currentlyChecking.compareAndSet(false, true) )
            return 1;

        boolean shouldTerminate = false;

        try {

            if (c.isIdle() && !disabled) {
                if( idleTerminationMinutes > 0) {
                    final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
                    if (idleMilliseconds > TimeUnit2.MINUTES.toMillis(idleTerminationMinutes)) {
                        shouldTerminate = true;
                    }
                }

                //TODO: Verify all of this should be in a synchronized block
                synchronized (this) {
                    if (c.isOnline() && c.haveWeRunAnyJobs())
                        shouldTerminate = true;
                    if( !c.isAcceptingTasks() )
                        shouldTerminate = true;
                }
            }


            if( shouldTerminate ) {
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
        finally
        {
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

    public static boolean disabled = Boolean.getBoolean(DockerRetentionStrategy.class.getName()+".disabled");
}
