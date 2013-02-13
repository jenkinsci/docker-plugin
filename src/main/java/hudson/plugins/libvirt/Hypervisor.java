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

import hudson.util.FormValidation;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.TaskListener;
import hudson.Extension;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import java.util.ArrayList;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.util.Map;
import java.util.HashMap;
import java.util.Hashtable;
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
    private final int maxOnlineSlaves;
    private transient int currentOnlineSlaveCount = 0;
    private transient Hashtable<String, String> currentOnline;

    @DataBoundConstructor
    public Hypervisor(String hypervisorType, String hypervisorHost, int hypervisorSshPort, String hypervisorSystemUrl, String username, int maxOnlineSlaves) {
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
        this.maxOnlineSlaves = maxOnlineSlaves;
    }

    protected void EnsureLists() {
        if (currentOnline == null)
            currentOnline = new Hashtable<String, String>();
    }

    private Connect makeConnection() {
        String hypervisorUri = getHypervisorURI();
        LOGGER.log(Level.INFO, "Trying to establish a connection to hypervisor URI: {0} as {1}/******",
                new Object[]{hypervisorUri, username});
        Connect hypervisorConnection = null;
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
        return hypervisorConnection;
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
    
    public int getMaxOnlineSlaves() {
        return maxOnlineSlaves;
    }

    public synchronized int getCurrentOnlineSlaveCount() {
        return currentOnlineSlaveCount;
    }

    public String getHypervisorDescription() {
        return getHypervisorType() + " - " + getHypervisorHost();
    }

    public synchronized Map<String, Domain> getDomains() throws LibvirtException {
        Map<String, Domain> domains = new HashMap<String, Domain>();
        Connect hypervisorConnection = makeConnection();
        LogRecord info = new LogRecord(Level.INFO, "Getting hypervisor domains.");
        LOGGER.log(info);
        if (hypervisorConnection != null) {
            for (String c : hypervisorConnection.listDefinedDomains()) {
                if (c != null && !c.equals("")) {
                    Domain domain = null;
                    try {
                        domain = hypervisorConnection.domainLookupByName(c);
                        domains.put(domain.getName(), domain);
                    } catch (Exception e) {
                        LogRecord rec = new LogRecord(Level.INFO, "Error retreiving information for domain with name: {0}.");
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
                    LogRecord rec = new LogRecord(Level.INFO, "Error retreiving information for domain with id: {0}.");
                    rec.setParameters(new Object[]{c});
                    rec.setThrown(e);
                    LOGGER.log(rec);
                }
            }      
            LogRecord rec = new LogRecord(Level.INFO, "Closing hypervisor connection.");
            LOGGER.log(rec);
            hypervisorConnection.close();
        } else {
            LogRecord rec = new LogRecord(Level.SEVERE, "Cannot connect to datacenter {0} as {1}/******");
            rec.setParameters(new Object[]{hypervisorHost, username});
            LOGGER.log(rec);
        }

        return domains;
    }

    /**
     * Returns a <code>List</code> of VMs configured on the hypervisor. This method always retrieves the current list of
     * VMs to ensure that newly available instances show up right away.
     * 
     * @return the virtual machines
     */
    public synchronized List<VirtualMachine> getVirtualMachines() {
    	List<VirtualMachine> vmList = new ArrayList<VirtualMachine>();
        try {
        	Map<String, Domain> domains = getDomains();
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

    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int i) {
        return Collections.emptySet();
    }

    public boolean canProvision(Label label) {
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Hypervisor");
        sb.append("{hypervisorUri='").append(hypervisorHost).append('\'');
        sb.append(", username='").append(username).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public synchronized Boolean canMarkVMOnline(String slaveName, String vmName) {
        EnsureLists();
        
        // Don't allow more than max.
        if ((maxOnlineSlaves > 0) && (currentOnline.size() == maxOnlineSlaves))
            return Boolean.FALSE;
        
        // Don't allow two slaves to the same VM to fire up.
        if (currentOnline.containsValue(vmName))
            return Boolean.FALSE;
        
        // Don't allow two instances of the same slave, although Jenkins will
        // probably not encounter this.
        if (currentOnline.containsKey(slaveName))
            return Boolean.FALSE;
        
        // Don't allow a misconfigured slave to try start
        if ("".equals(vmName) || "".equals(slaveName)) {
            LogRecord rec = new LogRecord(Level.WARNING, "Slave '"+slaveName+"' (using VM '"+vmName+"') appears to be misconfigured.");
            LOGGER.log(rec);
            return Boolean.FALSE;
        }
        
        return Boolean.TRUE;
    }
    
    public synchronized Boolean markVMOnline(String slaveName, String vmName) {
        EnsureLists();
        
        // If the combination is already in the list, it's good.
        if (currentOnline.containsKey(slaveName) && currentOnline.get(slaveName).equals(vmName))
            return Boolean.TRUE;
        
        if (!canMarkVMOnline(slaveName, vmName))
            return Boolean.FALSE;
        
        currentOnline.put(slaveName, vmName);
        currentOnlineSlaveCount++;
        
        return Boolean.TRUE;
    }

    public synchronized void markVMOffline(String slaveName, String vmName) {
        EnsureLists();
        
        if (currentOnline.remove(slaveName) != null)
            currentOnlineSlaveCount--;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public String getHypervisorURI() {
    	return constructHypervisorURI(hypervisorType, "ssh://", username, hypervisorHost, hypervisorSshPort, hypervisorSystemUrl);
    }

    private static String constructHypervisorURI (String hypervisorType, String protocol, String userName, String hypervisorHost, int hypervisorPort, String hypervisorSysUrl) {
    	// Fixing JENKINS-14617
    	final String separator = (hypervisorSysUrl.contains("?")) ? "&" : "?";
    	return hypervisorType.toLowerCase() + "+" + protocol + userName + "@" + hypervisorHost + ":" + hypervisorPort + "/" + hypervisorSysUrl + separator + "no_tty=1";
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
                    return FormValidation.error("Hypervisor Host is not specified!");
                }
                if (hypervisorType == null) {
                    return FormValidation.error("Hypervisor type is not specified!");
                }
                if (username == null) {
                    return FormValidation.error("Username is not specified!");
                }

                String hypervisorUri = constructHypervisorURI (hypervisorType, "ssh://", username, hypervisorHost, Integer.parseInt(hypervisorSshPort), hypervisorSystemUrl);

                LogRecord rec = new LogRecord(Level.INFO,
                        "Testing connection to hypervisor: {0}");
                rec.setParameters(new Object[]{hypervisorUri});
                LOGGER.log(rec);
                Connect hypervisorConnection = new Connect(hypervisorUri, false);
                hypervisorConnection.close();
                return FormValidation.ok("Successfully connected to: " + hypervisorUri);
            } catch (LibvirtException e) {
                LogRecord rec = new LogRecord(Level.WARNING,
                        "Failed to check hypervisor connection to {0} as {1}/******");
                rec.setThrown(e);
                rec.setParameters(new Object[]{hypervisorHost, username});
                LOGGER.log(rec);
                return FormValidation.error(e.getMessage());
            } catch (UnsatisfiedLinkError e) {
                LogRecord rec = new LogRecord(Level.WARNING,
                        "Failed to connect to hypervisor. Check libvirt installation on jenkins machine!");
                rec.setThrown(e);
                rec.setParameters(new Object[]{hypervisorHost, username});
                LOGGER.log(rec);
                return FormValidation.error(e.getMessage());
            } catch (Exception e) {
                LogRecord rec = new LogRecord(Level.WARNING,
                        "Failed to connect to hypervisor. Check libvirt installation on jenkins machine!");
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
