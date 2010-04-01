/**
 *  Copyright (C) 2010, Byte-Code srl <http://www.byte-code.com>
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
 * Author: Marco Mornati<mmornati@byte-code.com>
 */
package hudson.plugins.libvirt;

import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.model.TaskListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.Extension;
import hudson.slaves.Cloud;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
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
    private static final int WAIT_TIME = 60000;

    @DataBoundConstructor
    public VirtualMachineLauncher(ComputerLauncher delegate, String hypervisorDescription, String virtualMachineName) {
        super();
        this.delegate = delegate;
        this.virtualMachineName = virtualMachineName;
        this.hypervisorDescription = hypervisorDescription;
        buildVirtualMachine();
    }

    private void buildVirtualMachine() {
        if (hypervisorDescription != null && virtualMachineName != null) {
            LOGGER.log(Level.INFO, "Building virtual machine object from names");
            Hypervisor hypervisor = null;
            for (Cloud cloud : Hudson.getInstance().clouds) {
                if (cloud instanceof Hypervisor && ((Hypervisor) cloud).getHypervisorDescription().equals(hypervisorDescription)) {
                    hypervisor = (Hypervisor) cloud;
                    break;
                }
            }
            LOGGER.log(Level.INFO, "Hypervisor found... getting Virtual Machines associated");
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

    @Override
    public boolean isLaunchSupported() {
        return delegate.isLaunchSupported();
    }

    @Override
    public void launch(SlaveComputer slaveComputer, TaskListener taskListener)
            throws IOException, InterruptedException {
        taskListener.getLogger().println("Getting connection to the virtual datacenter");
        if (virtualMachine == null) {
            taskListener.getLogger().println("No connection ready to the Hypervisor... reconnecting...");
            buildVirtualMachine();
        }
        try {
            Map<String, Domain> computers = virtualMachine.getHypervisor().getDomains();
            taskListener.getLogger().println("Looking for the virtual machine on Hypervisor...");
            for (String domainName : computers.keySet()) {
                if (virtualMachine.getName().equals(domainName)) {
                    taskListener.getLogger().println("Virtual Machine Found");
                    Domain domain = computers.get(domainName);

                    if (domain.getInfo().state != DomainState.VIR_DOMAIN_BLOCKED && domain.getInfo().state != DomainState.VIR_DOMAIN_RUNNING) {
                        taskListener.getLogger().println("Starting virtual machine");
                        domain.create();
                        taskListener.getLogger().println("Waiting " + WAIT_TIME + "ms for machine startup");
                        Thread.sleep(WAIT_TIME);
                    } else {
                        taskListener.getLogger().println("Virtual machine is already running. No startup procedure required.");
                    }
                    taskListener.getLogger().println("Finished startup procedure... Connecting slave client");
                    delegate.launch(slaveComputer, taskListener);
                    return;
                }
            }
            taskListener.getLogger().println("Error! Could not find virtual machine on the hypervisor");
            throw new IOException("VM not found!");
        } catch (IOException e) {
            e.printStackTrace(taskListener.getLogger());
            throw e;
        } catch (Throwable t) {
            t.printStackTrace(taskListener.getLogger());
        }
    }

    @Override
    public void afterDisconnect(SlaveComputer slaveComputer, TaskListener taskListener) {
        taskListener.getLogger().println("Running disconnect procedure...");
        delegate.afterDisconnect(slaveComputer, taskListener);
        taskListener.getLogger().println("Shutting down Virtual Machine...");
        try {
            Map<String, Domain> computers = virtualMachine.getHypervisor().getDomains();
            taskListener.getLogger().println("Looking for the virtual machine on Hypervisor...");
            for (String domainName : computers.keySet()) {
                if (virtualMachine.getName().equals(domainName)) {
                    Domain domain = computers.get(domainName);
                    taskListener.getLogger().println("Virtual Machine Found");
                    if (domain.getInfo().state.equals(DomainState.VIR_DOMAIN_RUNNING) || domain.getInfo().state.equals(DomainState.VIR_DOMAIN_BLOCKED)) {
                        taskListener.getLogger().println("Shutting down virtual machine");
                        domain.shutdown();
                    } else {
                        taskListener.getLogger().println("Virtual machine is already suspended. No shutdown procedure required.");
                    }
                    return;
                }
            }
            taskListener.getLogger().println("Error! Could not find virtual machine on the hypervisor");
        } catch (Throwable t) {
            taskListener.fatalError(t.getMessage(), t);
        }
    }

    @Override
    public void beforeDisconnect(SlaveComputer slaveComputer, TaskListener taskListener) {
        delegate.beforeDisconnect(slaveComputer, taskListener);
    }

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }
    @Extension
    public static final Descriptor<ComputerLauncher> DESCRIPTOR = new Descriptor<ComputerLauncher>() {

        private String hypervisorDescription;
        private String virtualMachineName;
        private ComputerLauncher delegate;

        public String getDisplayName() {
            return "Virtual Machine Launcher";
        }

        public String getHypervisorDescription() {
            return hypervisorDescription;
        }

        public String getVirtualMachineName() {
            return virtualMachineName;
        }

        public ComputerLauncher getDelegate() {
            return delegate;
        }
    };
}
