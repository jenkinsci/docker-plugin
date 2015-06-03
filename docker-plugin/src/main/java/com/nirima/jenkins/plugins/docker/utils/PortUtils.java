package com.nirima.jenkins.plugins.docker.utils;

import com.trilead.ssh2.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static shaded.com.google.common.base.Preconditions.checkState;

public class PortUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortUtils.class);

    private final String host;
    private final int port;

    private int retries = 10;
    private int sshTimeoutMillis = (int) SECONDS.toMillis(2);

    private PortUtils(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * @param host hostname to connect to
     * @param port port to open socket
     *
     * @return util class to check connection
     */
    public static PortUtils canConnect(String host, int port) {
        return new PortUtils(host, port);
    }

    public PortUtils withRetries(int retries) {
        this.retries = retries;
        return this;
    }

    public PortUtils withSshTimeout(int time, TimeUnit units) {
        this.sshTimeoutMillis = (int) units.toMillis(time);
        return this;
    }

    /**
     * @return true if socket opened successfully, false otherwise
     */
    public boolean now() {
        try (Socket ignored = new Socket(host, port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Use {@link #now()} to check.
     * Retries while attempts reached with delay
     *
     * @return true if socket opened successfully, false otherwise
     */
    public boolean withEveryRetryWaitFor(int time, TimeUnit units) {
        for (int i = 1; i <= retries; i++) {
            if (now()) {
                return true;
            }
            sleepFor(time, units);
        }
        return false;
    }

    /**
     * Connects to sshd on host:port
     * Retries while attempts reached with delay
     * First with tcp port wait, then with ssh connection wait
     *
     * @throws IOException if no retries left
     */
    public void bySshWithEveryRetryWaitFor(int time, TimeUnit units) throws IOException {
        checkState(withEveryRetryWaitFor(time, units), "Port %s is not opened to connect to", port);

        for (int i = 1; i <= retries; i++) {
            Connection connection = new Connection(host, port);
            try {
                connection.connect(null, 0, sshTimeoutMillis, sshTimeoutMillis);
                return;
            } catch (IOException e) {
                LOGGER.info("Failed to connect to {}:{} (try {}/{}) - {}", host, port, i, retries, e.getMessage());
                if (i == retries) {
                    throw e;
                }
            } finally {
                connection.close();
            }
            sleepFor(time, units);
        }
    }

    /**
     * Blocks current thread for {@code time} of {@code units}
     *
     * @param time  number of units
     * @param units to convert to millis
     */
    public static void sleepFor(int time, TimeUnit units) {
        try {
            Thread.sleep(units.toMillis(time));
        } catch (InterruptedException e) {
            // no-op
        }
    }

}
