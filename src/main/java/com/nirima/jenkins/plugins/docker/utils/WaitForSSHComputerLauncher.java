package com.nirima.jenkins.plugins.docker.utils;

import hudson.model.TaskListener;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.trilead.ssh2.Connection;


public class WaitForSSHComputerLauncher extends DelegatingComputerLauncher {
	
	private static int WAIT_MS = 4000;
	private static int NO_TRIES = 5;
	
    private static final Logger LOGGER = Logger.getLogger(WaitForSSHComputerLauncher.class.getName());

    private String host;
    
    private int port;


    public WaitForSSHComputerLauncher(SSHLauncher delegate) {
        super(delegate);
        this.host = delegate.getHost();
        this.port = delegate.getPort();
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
    	waitForSSH();
        super.launch(computer, listener);
        
    }
    
    private void waitForSSH() throws IOException, InterruptedException{
    	LOGGER.log(Level.INFO, "Trying to connect to " + host + ":" + port);
    	for(int i=1;i<=NO_TRIES;i++){
    		Connection c = new Connection(host,port);
			try {
				c.connect();
				break;
			} catch (IOException e) {
				if(i==NO_TRIES){
					throw e;
				}else{
					LOGGER.log(Level.INFO, "Failed to connect to " + host + ":" + port + " try no. " + i,e);
				}
			}finally{
				c.close();
			}
			Thread.sleep(WAIT_MS);
    	}
    }
}
