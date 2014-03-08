package com.nirima.jenkins.plugins.docker;


import static com.nirima.jenkins.plugins.docker.utils.Consts.PLUGIN_IMAGES_URL;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import hudson.Extension;
import hudson.model.*;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;


/**
 * Manage the docker images.
 */
@Extension
public class DockerManagement extends ManagementLink implements StaplerProxy, Describable<DockerManagement>, Saveable {

    private static final Logger logger = LoggerFactory.getLogger(DockerManagement.class);

    @Override
    public String getIconFileName() {
        return PLUGIN_IMAGES_URL + "/48x48/docker.png";
    }

    @Override
    public String getUrlName() {
        return "docker-plugin";
    }

    public String getDisplayName() {
        return Messages.DisplayName();
    }

    @Override
    public String getDescription() {
        return Messages.PluginDescription();
    }

    public static DockerManagement get() {
        return ManagementLink.all().get(DockerManagement.class);
    }


    public DescriptorImpl getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(DescriptorImpl.class);
    }

    /**
     * Descriptor is only used for UI form bindings.
     */
    @Extension
    public static final class DescriptorImpl extends Descriptor<DockerManagement> {

        @Override
        public String getDisplayName() {
            return null; // unused
        }

//        /**
//         * Returns the list containing the GerritServer descriptor.
//         *
//         * @return the list of descriptors containing GerritServer's descriptor.
//         */
//        public static DescriptorExtensionList<GerritServer, GerritServer.DescriptorImpl> serverDescriptorList() {
//            return Jenkins.getInstance()
//                    .<GerritServer, GerritServer.DescriptorImpl>getDescriptorList(GerritServer.class);
//        }
//
//        /**
//         * Auto-completion for the "copy from" field in the new server page.
//         *
//         * @param value the value that the user has typed in the textbox.
//         * @return the list of server names, depending on the current value in the textbox.
//         */
//        public AutoCompletionCandidates doAutoCompleteCopyNewItemFrom(@QueryParameter final String value) {
//            final AutoCompletionCandidates r = new AutoCompletionCandidates();
//
//            for (String s : PluginImpl.getInstance().getServerNames()) {
//                if (s.startsWith(value)) {
//                    r.add(s);
//                }
//            }
//            return r;
//        }
    }
//    /**
//     * Used when redirected to a server.
//     * @param serverName the name of the server.
//     * @return the GerritServer object.
//     */
    public DockerManagementServer getServer(String serverName) {
        return new DockerManagementServer(serverName);
    }
//
//    /**
//     * Add a new server.
//     *
//     * @param req the StaplerRequest
//     * @param rsp the StaplerResponse
//     * @throws IOException when error sending redirect back to the list of servers
//     * @return the new GerritServer
//     */
//    public GerritServer doAddNewServer(StaplerRequest req, StaplerResponse rsp) throws IOException {
//        String serverName = req.getParameter("name");
//        PluginImpl plugin = PluginImpl.getInstance();
//        if (plugin.containsServer(serverName)) {
//            throw new Failure("A server already exists with the name '" + serverName + "'");
//        } else if (GerritServer.ANY_SERVER.equals(serverName)) {
//            throw new Failure("Illegal server name '" + serverName + "'");
//        }
//        GerritServer server = new GerritServer(serverName);
//
//        String mode = req.getParameter("mode");
//        if (mode != null && mode.equals("copy")) { //"Copy Existing Server Configuration" has been chosen
//            String from = req.getParameter("from");
//            GerritServer fromServer = plugin.getServer(from);
//            if (fromServer != null) {
//                server.setConfig(new Config(fromServer.getConfig()));
//                plugin.addServer(server);
//                server.start();
//            } else {
//                throw new Failure("Server '" + from + "' does not exist!");
//            }
//        } else {
//            plugin.addServer(server);
//            server.start();
//        }
//        plugin.save();
//
//        rsp.sendRedirect("./server/" + serverName);
//        return server;
//    }
//

    public Object getTarget() {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        return this;
    }


    public void save() throws IOException {

    }
//
//    /**
//     * Returns this singleton.
//     * @return the single loaded instance if this class.
//     */
//    public static GerritManagement get() {
//        return ManagementLink.all().get(GerritManagement.class);
//    }
//
//    /**
//     * Get the config of a server.
//     *
//     * @param serverName the name of the server for which we want to get the config.
//     * @return the config.
//     * @see GerritServer#getConfig()
//     */
//    public static IGerritHudsonTriggerConfig getConfig(String serverName) {
//        GerritServer server = PluginImpl.getInstance().getServer(serverName);
//        if (server != null) {
//            return server.getConfig();
//        } else {
//            logger.error("Could not find the Gerrit Server: {}", serverName);
//            return null;
//        }
//    }
//
//    /**
//     * The AdministrativeMonitor related to Gerrit.
//     * convenience method for the jelly page.
//     *
//     * @return the monitor if it could be found, or null otherwise.
//     */
//    @SuppressWarnings("unused") //Called from Jelly
//    public GerritAdministrativeMonitor getAdministrativeMonitor() {
//        for (AdministrativeMonitor monitor : AdministrativeMonitor.all()) {
//            if (monitor instanceof GerritAdministrativeMonitor) {
//                return (GerritAdministrativeMonitor)monitor;
//            }
//        }
//        return null;
//    }
//
//    /**
//     * Convenience method for jelly. Get the list of Gerrit server names.
//     *
//     * @return the list of server names as a list.
//     */
    public Collection<String> getServerNames() {
        return Collections2.transform(PluginImpl.getInstance().getServers(), new Function<DockerCloud, String>() {
            public String apply(@Nullable DockerCloud input) {
                return input.getDisplayName();
            }
        } );
    }

//    /**
//     * Checks whether server name already exists.
//     *
//     * @param value the value of the name field.
//     * @return ok or error.
//     */
//    public FormValidation doNameFreeCheck(@QueryParameter("value") final String value) {
//        if (PluginImpl.getInstance().containsServer(value)) {
//            return FormValidation.error("The server name " + value + " is already in use!");
//        } else if (GerritServer.ANY_SERVER.equals(value)) {
//            return FormValidation.error("Illegal name " + value + "!");
//        } else {
//            return FormValidation.ok();
//        }
//    }
}
