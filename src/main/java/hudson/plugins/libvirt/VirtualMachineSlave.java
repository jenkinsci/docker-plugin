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

import hudson.model.Slave;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.NodeProperty;
import hudson.slaves.Cloud;
import hudson.Util;
import hudson.Extension;
import hudson.Functions;

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;

public class VirtualMachineSlave extends Slave {

    private static final Logger LOGGER = Logger.getLogger(VirtualMachineSlave.class.getName());
    private String hypervisorDescription;
    private String virtualMachineName;

    @DataBoundConstructor
    public VirtualMachineSlave(String name, String nodeDescription, String remoteFS, String numExecutors,
            Mode mode, String labelString, VirtualMachineLauncher launcher, ComputerLauncher delegateLauncher,
            RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties,
            String hypervisorDescription, String virtualMachineName)
            throws
            Descriptor.FormException, IOException {
        super(name, nodeDescription, remoteFS, Util.tryParseNumber(numExecutors, 1).intValue(), mode, labelString,
                launcher == null ? new VirtualMachineLauncher(delegateLauncher, hypervisorDescription, virtualMachineName) : launcher,
                retentionStrategy, nodeProperties);        
        this.hypervisorDescription = hypervisorDescription;
        this.virtualMachineName = virtualMachineName;        
    }

    public String getHypervisorDescription() {
        return hypervisorDescription;
    }

    public String getVirtualMachineName() {
        return virtualMachineName;
    }

    public ComputerLauncher getDelegateLauncher() {
        return ((VirtualMachineLauncher) getLauncher()).getDelegate();
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {

        private String hypervisorDescription;
        private String virtualMachineName;
        
        public DescriptorImpl() {            
            load();
        }

        public String getDisplayName() {
            return "Slave virtual computer running on a virtualization platform (via libvirt)";
        }

        @Override
        public boolean isInstantiable() {
            return true;
        }

        public List<VirtualMachine> getDefinedVirtualMachines(String hypervisorDescription) {
            List<VirtualMachine> virtualMachinesList = new ArrayList<VirtualMachine>();                       
            if (hypervisorDescription != null && !hypervisorDescription.equals("")) {
                Hypervisor hypervisor = null;
                for (Cloud cloud : Hudson.getInstance().clouds) {
                    if (cloud instanceof Hypervisor && ((Hypervisor) cloud).getHypervisorDescription().equals(hypervisorDescription)) {
                        hypervisor = (Hypervisor) cloud;
                        break;
                    }
                }
                virtualMachinesList.addAll(hypervisor.getVirtualMachines());
            }
            return virtualMachinesList;
        }

        public List<Hypervisor> getHypervisors() {
            List<Hypervisor> result = new ArrayList<Hypervisor>();            
            for (Cloud cloud : Hudson.getInstance().clouds) {
                if (cloud instanceof Hypervisor) {
                    result.add((Hypervisor) cloud);
                }
            }
            return result;
        }

        public List<Descriptor<ComputerLauncher>> getComputerLauncherDescriptors() {
            List<Descriptor<ComputerLauncher>> result = new ArrayList<Descriptor<ComputerLauncher>>();
            for (Descriptor<ComputerLauncher> launcher : Functions.getComputerLauncherDescriptors()) {
                if (!VirtualMachineLauncher.DESCRIPTOR.getClass().isAssignableFrom(launcher.getClass())) {
                    result.add(launcher);
                }
            }
            return result;
        }
        
        public String getHypervisorDescription() {
            return hypervisorDescription;
        }

        public String getVirtualMachineName() {
            return virtualMachineName;
        }
        
    }
}
