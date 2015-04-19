package com.nirima.jenkins.plugins.docker;

import shaded.com.google.common.base.Objects;
import com.nirima.jenkins.plugins.docker.utils.Cacheable;
import hudson.model.*;
import hudson.slaves.AbstractCloudComputer;

import java.util.Date;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by magnayn on 09/01/2014.
 */
public class DockerComputer extends AbstractCloudComputer<DockerSlave> {
    private static final Logger LOGGER = Logger.getLogger(DockerComputer.class.getName());

    private int checked = 0;

    // Jenkins calls isUpdatingTasks a lot, and it's expensive to keep
    // asking the container if it exists or not, so we cache it here.
    private final Cacheable<Boolean> nodeExistenceStatus;

    public DockerComputer(DockerSlave dockerSlave) {
        super(dockerSlave);
        nodeExistenceStatus = new Cacheable<Boolean>(60000, new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return getNode().containerExistsInCloud();
            }
        });
    }

    public DockerCloud getCloud() {
        return getNode().getCloud();
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        super.taskAccepted(executor, task);
        LOGGER.log(Level.FINE, " Computer {0} taskAccepted", this);
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        Queue.Executable executable = executor.getCurrentExecutable();

        LOGGER.log(Level.FINE, " Computer {0} taskCompleted", this);

        if (executable instanceof Run) {
            Run build = (Run) executable;
            DockerSlave slave = getNode();

            if (slave == null) {
                LOGGER.log(Level.FINE, " Ignoring TaskCompleted for {0} as node has already been removed.", this);
            } else {
                slave.setRun(build);
            }
        }

        // May take the slave offline and remove it, in which case getNode()
        // above would return null and we'd not find our DockerSlave anymore.
        super.taskCompleted(executor, task, durationMS);
    }


    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        super.taskCompletedWithProblems(executor, task, durationMS, problems);
        LOGGER.log(Level.FINE, " Computer {0} taskCompletedWithProblems", this);
    }

    @Override
    public boolean isAcceptingTasks() {

        boolean result = super.isAcceptingTasks();

        // Quit quickly if we aren't accepting tasks
        if( !result )
            return false;

        // Update
        updateAcceptingTasks();

        // Are we still accepting tasks?
        result = super.isAcceptingTasks();

        return result;
    }

    private void updateAcceptingTasks() {
        try {

            int pause = 5000;
            if (getOfflineCause() != null) {
                if (getOfflineCause().toString().contains("failed to launch the slave agent") && checked < 3) {
                    LOGGER.log(Level.INFO, "Slave agent not launched after checking " + checked + " time(s).  Waiting for any retries...");
                    checked += 1;
                    Thread.sleep(pause);
                } else {
                    setAcceptingTasks(false);
                    LOGGER.log(Level.INFO, " Offline " + this + " due to " + getOfflineCause());
                }
            } else if (!nodeExistenceStatus.get()) {
                setAcceptingTasks(false);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.INFO, " Computer " + this + " error getting node");
            setAcceptingTasks(false);
        }
    }

    public void onConnected(){
        DockerSlave node = getNode();
        if (node != null) {
            node.onConnected();
        }
    }



    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", super.getName())
                .add("slave", getNode())
                .toString();
    }

}
