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

import org.kohsuke.stapler.DataBoundConstructor;
import org.libvirt.Domain;
import org.libvirt.DomainInfo.DomainState;

public class VirtualMachineLauncher extends ComputerLauncher {

    private ComputerLauncher delegate;
    private transient VirtualMachine virtualMachine;
    private String hypervisorDescription;
    private String virtualMachineName;
    private static final int RETRY_TIMES = 100;

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
            Hypervisor hypervisor = null;
            for (Cloud cloud : Hudson.getInstance().clouds) {
                if (cloud instanceof Hypervisor && ((Hypervisor) cloud).getHypervisorDescription().equals(hypervisorDescription)) {
                    hypervisor = (Hypervisor) cloud;
                    break;
                }
            }

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
        try {
            taskListener.getLogger().println("Target virtual computer: " + virtualMachine);
            Map<String, Domain> computers = virtualMachine.getHypervisor().getDomains();
            taskListener.getLogger().println("Finding the computer");
            for (String domainName : computers.keySet()) {
                if (virtualMachine.getName().equals(domainName)) {
                    taskListener.getLogger().println("Found the computer");
                    Domain domain = computers.get(domainName);

                    if (domain.getInfo().state != DomainState.VIR_DOMAIN_BLOCKED && domain.getInfo().state != DomainState.VIR_DOMAIN_RUNNING) {
                        taskListener.getLogger().println("Starting virtual computer");
                        domain.create();
                    } else {
                        taskListener.getLogger().println("Virtual computer is already running");
                    }
                    taskListener.getLogger().println("Starting stage 2 launcher");
                    int i = 0;
                    boolean finish = false;
                    while (i < RETRY_TIMES && !finish) {
                        try {

                            finish = true;
                        } catch (Exception e) {
                            i++;
                            wait(5000);
                        }
                    }
                    delegate.launch(slaveComputer, taskListener);
                    taskListener.getLogger().println("Stage 2 launcher completed");
                    return;
                }
            }
            taskListener.getLogger().println("Could not find the computer");
            throw new IOException("Could not find the computer");
        } catch (IOException e) {
            e.printStackTrace(taskListener.getLogger());
            throw e;
        } catch (Throwable t) {
            t.printStackTrace(taskListener.getLogger());
        }
    }

    @Override
    public void afterDisconnect(SlaveComputer slaveComputer, TaskListener taskListener) {
        taskListener.getLogger().println("Starting stage 2 afterDisconnect");
        delegate.afterDisconnect(slaveComputer, taskListener);
        taskListener.getLogger().println("Getting connection to the virtual datacenter");
        try {
            Map<String, Domain> computers = virtualMachine.getHypervisor().getDomains();
            taskListener.getLogger().println("Finding the computer");
            for (String domainName : computers.keySet()) {
                if (virtualMachine.getName().equals(domainName)) {
                    Domain domain = computers.get(domainName);
                    taskListener.getLogger().println("Found the computer");
                    if (domain.getInfo().state.equals(DomainState.VIR_DOMAIN_RUNNING) || domain.getInfo().state.equals(DomainState.VIR_DOMAIN_BLOCKED)) {
                        taskListener.getLogger().println("Suspending virtual computer");
                        domain.shutdown();
                    } else {
                        taskListener.getLogger().println("Virtual computer is already suspended");
                    }
                    return;
                }
            }
            taskListener.getLogger().println("Could not find the computer");
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

//        @Override
//        public boolean configure(StaplerRequest req, JSONObject o) throws FormException {
//            virtualMachineName = o.getString("computerName");
//            hypervisorDescription = o.getString("hypervisorDescription");
//            delegate = (ComputerLauncher) o.get("slave.delegateLauncher");
//            save();
//            return super.configure(req, o);
//        }
    };
}
