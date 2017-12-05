package com.nirima.jenkins.plugins.docker.utils;

import com.trilead.ssh2.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static com.google.common.base.Preconditions.checkState;

public class PortUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortUtils.class);



    /**
     * @param host hostname to connect to
     * @param port port to open socket
     *
     * @return util class to check connection
     */
    public static ConnectionCheck connectionCheck(String host, int port) {
        return new ConnectionCheck(host, port);
    }

    public static ConnectionCheck connectionCheck(InetSocketAddress address) {
        return new ConnectionCheck(address.getHostString(), address.getPort());
    }

    public static class ConnectionCheck {
        private final String host;
        private final int port;

        private int retries = 10;
        private long retryDelay = (long) SECONDS.toMillis(2);

        private ConnectionCheck(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public ConnectionCheck withRetries(int retries) {
            this.retries = retries;
            return this;
        }

        public ConnectionCheck withEveryRetryWaitFor(int time, TimeUnit units) {
            retryDelay = units.toMillis(time);
            return this;
        }

        public ConnectionCheckSSH useSSH() {

            return new ConnectionCheckSSH(this);
        }

        /**
         * @return true if socket opened successfully, false otherwise
         */
        public boolean executeOnce() {
            try (Socket ignored = new Socket(host, port)) {
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        public boolean execute() {

            LOGGER.trace("Testing connectivity to {} port {}", host, port );

            for (int i = 1; i <= retries; i++) {
                if (executeOnce()) {
                    return true;
                }
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException e) {
                    return false; // quit if interrupted
                }
            }

            LOGGER.warn("Could not connect to {} port {}. Are you sure this location is contactable from Jenkins?", host, port);

            return false;
        }
    }

    public static class ConnectionCheckSSH {
        private final ConnectionCheck parent;
        private int sshTimeoutMillis = (int) SECONDS.toMillis(2);

        ConnectionCheckSSH(ConnectionCheck connectionCheck) {
            this.parent = connectionCheck;
        }

        public ConnectionCheckSSH withSSHTimeout(int time, TimeUnit units) {
            sshTimeoutMillis = (int)units.toMillis(time);
            return this;
        }

        /**
         * Connects to sshd on host:port
         * Retries while attempts reached with delay
         * First with tcp port wait, then with ssh connection wait
         */
        public boolean execute() {
            checkState(parent.execute(), "Port %s is not opened to connect to", parent.port);

            for (int i = 1; i <= parent.retries; i++) {
                Connection connection = new Connection(parent.host, parent.port);
                try {
                    connection.connect(null, 0, sshTimeoutMillis, sshTimeoutMillis);
                    LOGGER.info("SSH port is open on {}:{}", parent.host, parent.port);
                    return true;
                } catch (IOException e) {
                    LOGGER.error("Failed to connect to {}:{} (try {}/{}) - {}", parent.host, parent.port, i, parent.retries, e.getMessage());
                } finally {
                    connection.close();
                }
                try {
                    Thread.sleep(parent.retryDelay);
                } catch (InterruptedException e) {
                    return false; // Quit if interrupted
                }
            }
            // Tried over and again. no joy.
            return false;
        }
    }




}
