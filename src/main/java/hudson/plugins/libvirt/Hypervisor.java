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

import hudson.util.FormValidation;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.Extension;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import java.util.ArrayList;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.LibvirtException;

/**
 * Represents a virtual datacenter.
 */
public class Hypervisor extends Cloud {

    private static final Logger LOGGER = Logger.getLogger(Hypervisor.class.getName());
    private final String hypervisorType;
    private final String hypervisorHost;
    private final String hypervisorSystemUrl;
    private final int hypervisorSshPort;
    private final String username;
    private transient Map<String, Domain> domains = null;
    private transient List<VirtualMachine> virtualMachineList = null;
    private transient Connect hypervisorConnection = null;

    @DataBoundConstructor
    public Hypervisor(String hypervisorType, String hypervisorHost, int hypervisorSshPort, String hypervisorSystemUrl, String username) {
        super("Hypervisor(libvirt)");
        this.hypervisorType = hypervisorType;
        this.hypervisorHost = hypervisorHost;
        if (hypervisorSystemUrl != null && !hypervisorSystemUrl.equals("")) {
            this.hypervisorSystemUrl = hypervisorSystemUrl;
        } else {
            this.hypervisorSystemUrl = "system";
        }
        this.hypervisorSshPort = hypervisorSshPort <= 0 ? 22 : hypervisorSshPort;
        this.username = username;
        virtualMachineList = retrieveVirtualMachines();
    }

    private Connect makeConnection() {
        String hypervisorUri = constructHypervisorURI();
        LOGGER.log(Level.INFO, "Trying to establish a connection to hypervisor URI: {0} as {1}/******",
                new Object[]{hypervisorUri, username});
        if (hypervisorConnection == null) {
            try {
                hypervisorConnection = new Connect(hypervisorUri, false);
                LOGGER.log(Level.INFO, "Established connection to hypervisor URI: {0} as {1}/******",
                        new Object[]{hypervisorUri, username});
            } catch (LibvirtException e) {
                LogRecord rec = new LogRecord(Level.WARNING,
                        "Failed to establish connection to hypervisor URI: {0} as {1}/******");
                rec.setThrown(e);
                rec.setParameters(new Object[]{hypervisorUri, username});
                LOGGER.log(rec);
            }
        }
        return hypervisorConnection;
    }

    private List<VirtualMachine> retrieveVirtualMachines() {
        List<VirtualMachine> vmList = new ArrayList<VirtualMachine>();
        try {
            domains = getDomains();
            for (String domainName : domains.keySet()) {
                vmList.add(new VirtualMachine(this, domainName));
            }
        } catch (Exception e) {
            LogRecord rec = new LogRecord(Level.SEVERE, "Cannot connect to datacenter {0} as {1}/******");
            rec.setThrown(e);
            rec.setParameters(new Object[]{hypervisorHost, username});
            LOGGER.log(rec);
        }
        return vmList;
    }

    public String getHypervisorHost() {
        return hypervisorHost;
    }

    public int getHypervisorSshPort() {
        return hypervisorSshPort;
    }

    public String getHypervisorType() {
        return hypervisorType;
    }

    public String getHypervisorSystemUrl() {
        return hypervisorSystemUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getHypervisorDescription() {
        return getHypervisorType() + " - " + getHypervisorHost();
    }

    public synchronized Map<String, Domain> getDomains() throws LibvirtException {
        Map<String, Domain> domains = new HashMap<String, Domain>();
        hypervisorConnection = makeConnection();
        LogRecord info = new LogRecord(Level.INFO, "Getting hypervisor domains");
        LOGGER.log(info);
        if (hypervisorConnection != null) {
            for (String c : hypervisorConnection.listDefinedDomains()) {
                if (c != null && !c.equals("")) {
                    Domain domain = null;
                    try {
                        domain = hypervisorConnection.domainLookupByName(c);
                        domains.put(domain.getName(), domain);
                    } catch (Exception e) {
                        LogRecord rec = new LogRecord(Level.INFO, "Error retreiving information for domain with name: {0}");
                        rec.setParameters(new Object[]{c});
                        rec.setThrown(e);
                        LOGGER.log(rec);
                    }
                }
            }
            for (int c : hypervisorConnection.listDomains()) {
                Domain domain = null;
                try {
                    domain = hypervisorConnection.domainLookupByID(c);
                    domains.put(domain.getName(), domain);
                } catch (Exception e) {
                    LogRecord rec = new LogRecord(Level.INFO, "Error retreiving information for domain with id: {0}");
                    rec.setParameters(new Object[]{c});
                    rec.setThrown(e);
                    LOGGER.log(rec);
                }
            }           
        } else {
            LogRecord rec = new LogRecord(Level.SEVERE, "Cannot connect to datacenter {0} as {1}/******");
            rec.setParameters(new Object[]{hypervisorHost, username});
            LOGGER.log(rec);
        }

        return domains;
    }

    public synchronized List<VirtualMachine> getVirtualMachines() {
        if (virtualMachineList == null) {
            virtualMachineList = retrieveVirtualMachines();
        }
        return virtualMachineList;
    }

    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int i) {
        return Collections.emptySet();
    }

    public boolean canProvision(Label label) {
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Hypervisor");
        sb.append("{hypervisorUri='").append(hypervisorHost).append('\'');
        sb.append(", username='").append(username).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public String constructHypervisorURI() {
        return hypervisorType.toLowerCase() + "+ssh://" + username + "@" + hypervisorHost + ":" + hypervisorSshPort + "/" + hypervisorSystemUrl + "?no_tty=1";
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<Cloud> {

        public final ConcurrentMap<String, Hypervisor> hypervisors = new ConcurrentHashMap<String, Hypervisor>();
        private String hypervisorType;
        private String hypervisorHost;
        private String hypervisorSystemUrl;
        private int hypervisorSshPort;
        private String username;

        public String getDisplayName() {
            return "Hypervisor (via libvirt)";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject o) throws FormException {
            hypervisorType = o.getString("hypervisorType");
            hypervisorHost = o.getString("hypervisorHost");
            hypervisorSystemUrl = o.getString("hypervisorSystemUrl");
            hypervisorSshPort = o.getInt("hypervisorSshPort");
            username = o.getString("username");
            save();
            return super.configure(req, o);
        }

        public FormValidation doTestConnection(
                @QueryParameter String hypervisorType, @QueryParameter String hypervisorHost, @QueryParameter String hypervisorSshPort,
                @QueryParameter String username, @QueryParameter String hypervisorSystemUrl) throws Exception, ServletException {
            try {
                if (hypervisorHost == null) {
                    return FormValidation.error("Hypervisor Host is not specified");
                }
                if (hypervisorType == null) {
                    return FormValidation.error("Hypervisor type is not specified");
                }
                if (username == null) {
                    return FormValidation.error("Username is not specified");
                }

                String hypervisorUri = hypervisorType.toLowerCase() + "+ssh://" + username + "@" + hypervisorHost + ":" + hypervisorSshPort + "/" + hypervisorSystemUrl + "?no_tty=1";

                LogRecord rec = new LogRecord(Level.INFO,
                        "Testing connection to hypervisor: {0}");
                rec.setParameters(new Object[]{hypervisorUri});
                LOGGER.log(rec);
                Connect hypervisorConnection = new Connect(hypervisorUri, false);
                hypervisorConnection.close();
                return FormValidation.ok("Connected successfully");
            } catch (LibvirtException e) {
                LogRecord rec = new LogRecord(Level.WARNING,
                        "Failed to check hypervisor connection to {0} as {1}/******");
                rec.setThrown(e);
                rec.setParameters(new Object[]{hypervisorHost, username});
                LOGGER.log(rec);
                return FormValidation.error(e.getMessage());
            } catch (UnsatisfiedLinkError e) {
                LogRecord rec = new LogRecord(Level.WARNING,
                        "Failed to connect to hypervisor. Check libvirt installation on hudson machine!");
                rec.setThrown(e);
                rec.setParameters(new Object[]{hypervisorHost, username});
                LOGGER.log(rec);
                return FormValidation.error(e.getMessage());
            }
        }

        public String getHypervisorHost() {
            return hypervisorHost;
        }

        public int getHypervisorSshPort() {
            return hypervisorSshPort;
        }

        public String getHypervisorSystemUrl() {
            return hypervisorSystemUrl;
        }

        public String getHypervisorType() {
            return hypervisorType;
        }

        public String getUsername() {
            return username;
        }

        public List<String> getHypervisorTypes() {
            List<String> types = new ArrayList<String>();
            types.add("QEMU");
            types.add("XEN");
            return types;
        }
    }
}
