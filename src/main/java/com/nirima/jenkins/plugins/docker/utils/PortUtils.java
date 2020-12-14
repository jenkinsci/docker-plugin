package com.nirima.jenkins.plugins.docker.utils;

import com.trilead.ssh2.Connection;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
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

    @Restricted(NoExternalUse.class)
    public static class ConnectionCheck {
        private final String host;
        private final int port;

        protected static final int DEFAULT_RETRIES = 10;
        private int retries = DEFAULT_RETRIES;
        protected static final int DEFAULT_RETRY_DELAY_SECONDS = 2;
        private long retryDelay = SECONDS.toMillis(DEFAULT_RETRY_DELAY_SECONDS);

        private ConnectionCheck(String host, int port) {
            this.host = host;
            this.port = port;
        }

        /**
         * Sets the number of retries, such that {@link #execute()} will try
         * once more than this. If this is not set then a default of
         * {@value #DEFAULT_RETRIES} will be used.
         * 
         * @param retries
         *            Number of retries. Negative values will be treated as
         *            zero.
         * @return this
         */
        public ConnectionCheck withRetries(int retries) {
            this.retries = retries;
            return this;
        }

        /**
         * Sets the delay between tries. If this is not set then a default of
         * {@value #DEFAULT_RETRY_DELAY_SECONDS} seconds will be used.
         * 
         * @param time
         *            The lengthy of time.
         * @param units
         *            The units of that length.
         * @return this
         */
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

        /**
         * Tests the connection. If {@link #withRetries(int)} was set to more
         * than zero then more than one attempt will be made, waiting (for the
         * period specified by {@link #withEveryRetryWaitFor(int, TimeUnit)})
         * between attempts.
         * 
         * @return true if the connection succeeded, false if it failed despite
         *         any retries.
         * @throws InterruptedException
         *             if interrupted while waiting between retries.
         */
        public boolean execute() throws InterruptedException {
            LOGGER.trace("Testing connectivity to {} port {}", host, port );
            for (int i = 1; i <= retries; i++) {
                if (executeOnce()) {
                    return true;
                }
                Thread.sleep(retryDelay);
            }
            if (executeOnce()) {
                return true;
            }
            LOGGER.warn("Could not connect to {} port {}. Are you sure this location is contactable from Jenkins?", host, port);
            return false;
        }
    }

    @Restricted(NoExternalUse.class)
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
         * Tests the SSH connection. If the parent
         * {@link ConnectionCheck#withRetries(int)} was set to more than zero
         * then more than one attempt will be made, waiting (for the period
         * specified by the parent
         * {@link ConnectionCheck#withEveryRetryWaitFor(int, TimeUnit)}) between
         * attempts. Note that, prior to testing that the port accepts SSH
         * connection, it will first be tested to verify that it is open to TCP
         * connections using {@link ConnectionCheck#execute()}, and this will
         * also be subjected to retries, so that the total retry time for a port
         * that is initially unavailable and then slow to accept SSH connections
         * can be up to double what might be expected.
         * 
         * @return true if the connection succeeded, false if it failed despite
         *         any retries.
         * @throws InterruptedException
         *             if interrupted while waiting between retries. Connects to
         *             sshd on host:port. Retries while attempts reached with
         *             delay First with tcp port wait, then with ssh connection
         *             wait
         * 
         * @throws IllegalStateException
         *             if the TCP port is not reachable despite retries.
         * @throws InterruptedException
         *             if interrupted while waiting between retries.
         */
        public boolean execute() throws InterruptedException {
            checkState(parent.execute(), "Port %s is not opened to connect to", parent.port);

            final int retries = Math.max(0, parent.retries);
            final long retryDelay = parent.retryDelay;
            final int totalTriesIntended = retries + 1;
            int thisTryNumber;
            for (thisTryNumber = 1; thisTryNumber <= retries; thisTryNumber++) {
                if (executeOnce(thisTryNumber, totalTriesIntended)) {
                    return true;
                }
                Thread.sleep(retryDelay);
            }
            // last attempt
            return executeOnce(thisTryNumber, totalTriesIntended);
        }

        private boolean executeOnce(final int thisTryNumber, final int totalTriesIntended) {
            final Connection sshConnection = new Connection(parent.host, parent.port);
            try {
                sshConnection.connect(null, sshTimeoutMillis, sshTimeoutMillis, sshTimeoutMillis);
                LOGGER.info("SSH port is open on {}:{}", parent.host, parent.port);
                return true;
            } catch (IOException e) {
                LOGGER.error("Failed to connect to {}:{} (try {}/{}) - {}", parent.host, parent.port, thisTryNumber, totalTriesIntended, e.getMessage());
                return false;
            } finally {
                sshConnection.close();
            }
        }
    }
}
