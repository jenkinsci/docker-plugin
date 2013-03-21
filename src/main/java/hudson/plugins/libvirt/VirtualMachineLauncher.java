/**
 *  Copyright (C) 2010, Byte-Code srl <http://www.byte-code.com>
 *  Copyright (C) 2012  Philipp Bartsch <tastybug@tastybug.com>
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Date: Mar 04, 2010
 * @author Marco Mornati<mmornati@byte-code.com>
 * @author Philipp Bartsch <tastybug@tastybug.com>
 */
package hudson.plugins.libvirt;

import hudson.model.TaskListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.libvirt.Domain;
import org.libvirt.DomainInfo.DomainState;

public class VirtualMachineLauncher extends ComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(VirtualMachineLauncher.class.getName());
    private ComputerLauncher delegate;
    private transient VirtualMachine virtualMachine;
    private String hypervisorDescription;
    private String virtualMachineName;
    private String snapshotName;
    private final int WAIT_TIME_MS;
    
    @DataBoundConstructor
    public VirtualMachineLauncher(ComputerLauncher delegate, String hypervisorDescription, String virtualMachineName, String snapshotName, int waitingTimeSecs) {
        super();
        this.delegate = delegate;
        this.virtualMachineName = virtualMachineName;
        this.snapshotName = snapshotName;
        this.hypervisorDescription = hypervisorDescription;
        this.WAIT_TIME_MS = waitingTimeSecs*1000;
        lookupVirtualMachineHandle();
    }

    private void lookupVirtualMachineHandle() {
        if (hypervisorDescription != null && virtualMachineName != null) {
            LOGGER.log(Level.FINE, "Grabbing hypervisor...");
            Hypervisor hypervisor = null;
            for (Cloud cloud : Hudson.getInstance().clouds) {
                if (cloud instanceof Hypervisor && ((Hypervisor) cloud).getHypervisorDescription().equals(hypervisorDescription)) {
                    hypervisor = (Hypervisor) cloud;
                    break;
                }
            }
            LOGGER.log(Level.FINE, "Hypervisor found, searching for a matching virtual machine for \"" + virtualMachineName + "\"...");
            for (VirtualMachine vm : hypervisor.getVirtualMachines()) {
                if (vm.getName().equals(virtualMachineName)) {
                    virtualMachine = vm;
                    break;
                }
            }
        }
    }
    
    public ComputerLauncher getDelegate() {
        return delegate;
    }

    public VirtualMachine getVirtualMachine() {
        return virtualMachine;
    }

    public String getVirtualMachineName() {
        return virtualMachineName;
    }

    @Override
    public boolean isLaunchSupported() {
        return delegate.isLaunchSupported();
    }

    public Hypervisor findOurHypervisorInstance() throws RuntimeException {
        if (hypervisorDescription != null && virtualMachineName != null) {
            Hypervisor hypervisor = null;
            for (Cloud cloud : Hudson.getInstance().clouds) {
                if (cloud instanceof Hypervisor && ((Hypervisor) cloud).getHypervisorDescription().equals(hypervisorDescription)) {
                    hypervisor = (Hypervisor) cloud;
                    return hypervisor;
                }
            }
        }
        LOGGER.log(Level.SEVERE, "Could not find our libvirt cloud instance!");
        throw new RuntimeException("Could not find our libvirt cloud instance!");
    }

    @Override
    public void launch(SlaveComputer slaveComputer, TaskListener taskListener) throws IOException, InterruptedException {
    	
    	taskListener.getLogger().println("Virtual machine \"" + virtualMachineName + "\" (slave title \"" + slaveComputer.getDisplayName() + "\") is to be started.");
    	try {
	        if (virtualMachine == null) {
	            taskListener.getLogger().println("No connection ready to the Hypervisor, connecting...");
	            lookupVirtualMachineHandle();
	            if (virtualMachine == null) // still null? no such vm!
	            	throw new Exception("Virtual machine \"" + virtualMachineName + "\" (slave title \"" + slaveComputer.getDisplayName() + "\") not found on the specified hypervisor!");
	        }
        
            Map<String, Domain> computers = virtualMachine.getHypervisor().getDomains();
            Domain domain = computers.get(virtualMachine.getName());
            if (domain != null) {
                if (domain.getInfo().state != DomainState.VIR_DOMAIN_BLOCKED && domain.getInfo().state != DomainState.VIR_DOMAIN_RUNNING) {
                    taskListener.getLogger().println("Starting, waiting for " + WAIT_TIME_MS + "ms to let it fully boot up...");
                    domain.create();
                    Thread.sleep(WAIT_TIME_MS);
                } else {
                    taskListener.getLogger().println("Already running, no startup required.");
                }
                taskListener.getLogger().println("Connecting slave client.");
                delegate.launch(slaveComputer, taskListener);
            } else {
	            throw new IOException("VM \"" + virtualMachine.getName() + "\" (slave title \"" + slaveComputer.getDisplayName() + "\") not found!");
            }
        } catch (IOException e) {
            taskListener.fatalError(e.getMessage(), e);
            
            LogRecord rec = new LogRecord(Level.SEVERE, "Error while launching {0} on Hypervisor {1}.");
            rec.setParameters(new Object[]{virtualMachine.getName(), virtualMachine.getHypervisor().getHypervisorURI()});
            rec.setThrown(e);
            LOGGER.log(rec);
            throw e;
        } catch (Throwable t) {
        	taskListener.fatalError(t.getMessage(), t);
            
            LogRecord rec = new LogRecord(Level.SEVERE, "Error while launching {0} on Hypervisor {1}.");
            rec.setParameters(new Object[]{virtualMachine.getName(), virtualMachine.getHypervisor().getHypervisorURI()});
            rec.setThrown(t);
            LOGGER.log(rec);
        }
    }

    @Override
    public synchronized void afterDisconnect(SlaveComputer slaveComputer, TaskListener taskListener) {
    	
        taskListener.getLogger().println("Virtual machine \"" + virtualMachineName + "\" (slave \"" + slaveComputer.getDisplayName() + "\") is to be shut down.");
        delegate.afterDisconnect(slaveComputer, taskListener);

        try {
            Map<String, Domain> computers = virtualMachine.getHypervisor().getDomains();
            Domain domain = computers.get(virtualMachine.getName());
            if (domain != null) {
            	if (domain.getInfo().state.equals(DomainState.VIR_DOMAIN_RUNNING) || domain.getInfo().state.equals(DomainState.VIR_DOMAIN_BLOCKED)) {
                    if (snapshotName != null && snapshotName.length() > 0) {
                    	taskListener.getLogger().println("Reverting to " + snapshotName + " and shutting down.");
                        domain.revertToSnapshot(domain.snapshotLookupByName(snapshotName));
                    } else {
                    	taskListener.getLogger().println("Shutting down.");
                        domain.shutdown();
                    }
                } else {
                    taskListener.getLogger().println("Already suspended, no shutdown required.");
                }
                VirtualMachineLauncher vmL = (VirtualMachineLauncher) ((SlaveComputer) slaveComputer).getLauncher();
                Hypervisor vmC = vmL.findOurHypervisorInstance();
                vmC.markVMOffline(slaveComputer.getDisplayName(), vmL.getVirtualMachineName());
            } else {
            	// log to slave 
            	taskListener.getLogger().println("\"" + virtualMachineName + "\" not found on Hypervisor, can not shut down!");
            	
            	// log to jenkins
            	LogRecord rec = new LogRecord(Level.WARNING, "Can not shut down {0} on Hypervisor {1}, domain not found!");
                rec.setParameters(new Object[]{virtualMachine.getName(), virtualMachine.getHypervisor().getHypervisorURI()});
                LOGGER.log(rec);
            }
        } catch (Throwable t) {
            taskListener.fatalError(t.getMessage(), t);
            
            LogRecord rec = new LogRecord(Level.SEVERE, "Error while shutting down {0} on Hypervisor {1}.");
            rec.setParameters(new Object[]{virtualMachine.getName(), virtualMachine.getHypervisor().getHypervisorURI()});
            rec.setThrown(t);
            LOGGER.log(rec);
        }
    }

    @Override
    public void beforeDisconnect(SlaveComputer slaveComputer, TaskListener taskListener) {
        delegate.beforeDisconnect(slaveComputer, taskListener);
    }

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
    	throw new UnsupportedOperationException();
    }
}
