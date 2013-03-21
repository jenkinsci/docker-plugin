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

import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Slave;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerListener;
import hudson.slaves.NodeProperty;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

public class VirtualMachineSlave extends Slave {

    private String 				hypervisorDescription;
    private String 				snapshotName;
    private String 				virtualMachineName;
    private int 				startupWaitingPeriodSeconds;

    @DataBoundConstructor
    public VirtualMachineSlave(String name, String nodeDescription, String remoteFS, String numExecutors,
            Mode mode, String labelString, VirtualMachineLauncher launcher, ComputerLauncher delegateLauncher,
            RetentionStrategy retentionStrategy, List<? extends NodeProperty<?>> nodeProperties,
            String hypervisorDescription, String virtualMachineName, String snapshotName, int startupWaitingPeriodSeconds)
            throws
            Descriptor.FormException, IOException {
        super(name, nodeDescription, remoteFS, Util.tryParseNumber(numExecutors, 1).intValue(), mode, labelString,
                launcher == null ? new VirtualMachineLauncher(delegateLauncher, hypervisorDescription, virtualMachineName, snapshotName, startupWaitingPeriodSeconds) : launcher,
                retentionStrategy, nodeProperties);        
        this.hypervisorDescription = hypervisorDescription;
        this.virtualMachineName = virtualMachineName;
        this.snapshotName = snapshotName;
        this.startupWaitingPeriodSeconds = startupWaitingPeriodSeconds;
    }

    public String getHypervisorDescription() {
        return hypervisorDescription;
    }

    public String getVirtualMachineName() {
        return virtualMachineName;
    }

    public String getSnapshotName() {
        return snapshotName;
    }

    public int getStartupWaitingPeriodSeconds() {
        return startupWaitingPeriodSeconds;
    }

    public ComputerLauncher getDelegateLauncher() {
        return ((VirtualMachineLauncher) getLauncher()).getDelegate();
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    @Extension
    public static class VirtualMachineComputerListener extends ComputerListener {
        
        @Override
        public void preLaunch(Computer c, TaskListener taskListener) throws IOException, InterruptedException {
            /* We may be called on any slave type so check that we should
             * be in here. */
            if (!(c.getNode() instanceof VirtualMachineSlave)) {
                return;
            }
            
            VirtualMachineLauncher vmL = (VirtualMachineLauncher) ((SlaveComputer) c).getLauncher();
            Hypervisor vmC = vmL.findOurHypervisorInstance();
            
            if (!vmC.markVMOnline(c.getDisplayName(), vmL.getVirtualMachineName()))
                throw new AbortException("Capacity threshold  (" + vmC.getMaxOnlineSlaves() + ") reached at hypervisor \"" + vmC.getHypervisorDescription() + "\", slave commissioning delayed.");
        }
    }

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {

        private String hypervisorDescription;
        private String virtualMachineName;
        private String snapshotName;
        
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
            Hypervisor hypervisor = getHypervisorByDescription(hypervisorDescription);
            if (hypervisor != null)
            	virtualMachinesList.addAll(hypervisor.getVirtualMachines());
            return virtualMachinesList;
        }

        public String[] getDefinedSnapshots(String hypervisorDescription, String virtualMachineName) {
        	Hypervisor hypervisor = getHypervisorByDescription(hypervisorDescription);
            if (hypervisor != null) {
            	String[] snapS = hypervisor.getSnapshots(virtualMachineName);
            	return snapS;
            }
            return new String[0];
        }

        public ListBoxModel doFillHypervisorDescriptionItems() {
            ListBoxModel items = new ListBoxModel();
            for (Cloud cloud : Hudson.getInstance().clouds) {
                if (cloud instanceof Hypervisor) {
                    items.add(((Hypervisor) cloud).getHypervisorURI(), ((Hypervisor) cloud).getHypervisorDescription());
                }
            }
            return items;
        }
        
        public String getHypervisorDescription() {
            return hypervisorDescription;
        }

        public String getVirtualMachineName() {
            return virtualMachineName;
        }

        public String getSnapshotName() {
            return snapshotName;
        }
        
        private Hypervisor getHypervisorByDescription (String hypervisorDescription) {
        	if (hypervisorDescription != null && !hypervisorDescription.equals("")) {
	        	for (Cloud cloud : Hudson.getInstance().clouds) {
	                if (cloud instanceof Hypervisor && ((Hypervisor) cloud).getHypervisorDescription().equals(hypervisorDescription)) {
	                    return (Hypervisor) cloud;
	                }
	            }
        	}
            return null;
        }
    }
}
