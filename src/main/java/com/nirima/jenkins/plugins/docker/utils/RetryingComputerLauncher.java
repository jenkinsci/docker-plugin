package com.nirima.jenkins.plugins.docker.utils;

import com.nirima.docker.client.DockerException;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;


public class RetryingComputerLauncher extends DelegatingComputerLauncher {

    private static final Logger log = LoggerFactory.getLogger(RetryingComputerLauncher.class);

    /**
     * How many times to retry the launch?
     */
    private final int retries = 3;

    /**
     * time (ms) to back off between retries?
     */
    private final int pause   = 5000;

    public RetryingComputerLauncher(ComputerLauncher delegate) {
        super(delegate);
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        log.info("Launch......");
        for( int i=0;i<retries;i++) {
            log.info("Launch Retry: " + i);
            try {
                super.launch(computer, listener);
                return;
            } catch(Exception ex) {
                log.info("Launch failed on attempt {}", i);
            }
            Thread.sleep(pause);
        }
        throw new IOException("SSH Launch failed");
    }
}
