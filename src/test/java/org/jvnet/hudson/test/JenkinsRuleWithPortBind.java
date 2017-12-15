package org.jvnet.hudson.test;

import org.apache.commons.lang.SystemUtils;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.NoListenerConfiguration;
import org.jvnet.hudson.test.ThreadPoolImpl;
import org.jvnet.hudson.test.WarExploder;

import javax.servlet.ServletContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JenkinsRuleWithPortBind extends JenkinsRule {

    private static final Logger LOGGER = Logger.getLogger(HudsonTestCase.class.getName());

    /**
     * Prepares a webapp hosting environment to get {@link javax.servlet.ServletContext} implementation
     * that we need for testing.
     */
    @Override
    protected ServletContext createWebServer() throws Exception {
        server = new Server(new ThreadPoolImpl(new ThreadPoolExecutor(10, 10, 10L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("Jetty Thread Pool");
                return t;
            }
        })));

        WebAppContext context = new WebAppContext(WarExploder.getExplodedDir().getPath(), contextPath);
        context.setClassLoader(getClass().getClassLoader());
        context.setConfigurations(new Configuration[]{new WebXmlConfiguration()});
        context.addBean(new NoListenerConfiguration(context));
        server.setHandler(context);
        context.setMimeTypes(MIME_TYPES);
        context.getSecurityHandler().setLoginService(configureUserRealm());
        context.setResourceBase(WarExploder.getExplodedDir().getPath());

        ServerConnector connector = new ServerConnector(server, 1, 1);
        HttpConfiguration config = connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration();
        // use a bigger buffer as Stapler traces can get pretty large on deeply nested URL
        config.setRequestHeaderSize(12 * 1024);
        if (SystemUtils.IS_OS_MAC)
            connector.setHost("localhost");
        else
            connector.setHost(getLocalHostLANAddress().getHostAddress());

        if (System.getProperty("port") != null)
            connector.setPort(Integer.parseInt(System.getProperty("port")));

        server.addConnector(connector);
        server.start();

        localPort = connector.getLocalPort();
        LOGGER.log(Level.INFO, "Running on {0}", getURL());

        return context.getServletContext();
    }
    @Override
    public URL getURL() throws IOException {
        return new URL("http://"+((ServerConnector)server.getConnectors()[0]).getHost()+":"+localPort+contextPath+"/");
    }
    private InetAddress getLocalHostLANAddress() throws UnknownHostException {
        try {
            InetAddress candidateAddress = null;
            // Iterate all NICs (network interface cards)...
            for (Enumeration ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements(); ) {
                NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
                // Iterate all IP addresses assigned to each card...
                for (Enumeration inetAddrs = iface.getInetAddresses(); inetAddrs.hasMoreElements(); ) {
                    InetAddress inetAddr = (InetAddress) inetAddrs.nextElement();
                    if (!inetAddr.isLoopbackAddress()) {

                        if (inetAddr.isSiteLocalAddress()) {
                            // Found non-loopback site-local address. Return it immediately...
                            return inetAddr;
                        } else if (candidateAddress == null) {
                            // Found non-loopback address, but not necessarily site-local.
                            // Store it as a candidate to be returned if site-local address is not subsequently found...
                            candidateAddress = inetAddr;
                            // Note that we don't repeatedly assign non-loopback non-site-local addresses as candidates,
                            // only the first. For subsequent iterations, candidate will be non-null.
                        }
                    }
                }
            }
            if (candidateAddress != null) {
                // We did not find a site-local address, but we found some other non-loopback address.
                // Server might have a non-site-local address assigned to its NIC (or it might be running
                // IPv6 which deprecates the "site-local" concept).
                // Return this non-loopback candidate address...
                return candidateAddress;
            }
            // At this point, we did not find a non-loopback address.
            // Fall back to returning whatever InetAddress.getLocalHost() returns...
            InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
            if (jdkSuppliedAddress == null) {
                throw new UnknownHostException("The JDK InetAddress.getLocalHost() method unexpectedly returned null.");
            }
            return jdkSuppliedAddress;
        } catch (Exception e) {
            UnknownHostException unknownHostException = new UnknownHostException("Failed to determine LAN address: " + e);
            unknownHostException.initCause(e);
            throw unknownHostException;
        }
    }
}
