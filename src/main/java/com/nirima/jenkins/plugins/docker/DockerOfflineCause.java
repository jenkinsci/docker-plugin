package com.nirima.jenkins.plugins.docker;

import hudson.slaves.OfflineCause;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerOfflineCause extends OfflineCause {
    @Override
    public String toString() {
        return "Shutting down Docker";
    }
}
