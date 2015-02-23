package com.nirima.jenkins.plugins.docker;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;

@Extension
public class DockerComputerListener extends ComputerListener {

    @Override
    public void onOnline(Computer c, TaskListener listener) {
        if(c instanceof DockerComputer){
            ((DockerComputer) c).onConnected();
        }
    }
}
