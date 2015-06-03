package com.nirima.jenkins.plugins.docker.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.nirima.jenkins.plugins.docker.utils.PortUtils.canConnect;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

/**
 * @author lanwen (Merkushev Kirill)
 */
public class PortUtilsTest {

    public static final int RETRY_COUNT = 2;
    public static final int DELAY = (int) SECONDS.toMillis(1);

    @Rule
    public SomeServerRule server = new SomeServerRule();

    @Rule
    public ExpectedException ex = ExpectedException.none();

    @Test
    public void shouldConnectToServerSuccessfully() throws Exception {
        assertThat("Server is up and should connect", canConnect(server.host(), server.port()).now(), is(true));
    }

    @Test
    public void shouldNotConnectToUnusedPort() throws Exception {
        assertThat("Unused port should not be connectible", canConnect("localhost", 0).now(), is(false));
    }

    @Test
    public void shouldWaitForPortAvailableUntilTimeout() throws Exception {
        long before = currentTimeMillis();
        assertThat("Unused port should not be connectible",
                canConnect("localhost", 0).withRetries(RETRY_COUNT)
                        .withEveryRetryWaitFor(DELAY, MILLISECONDS), is(false));
        assertThat("Should wait for timeout", new Date(currentTimeMillis()),
                greaterThanOrEqualTo(new Date(before + RETRY_COUNT * DELAY)));
    }

    @Test
    public void shouldThrowIllegalStateExOnNotAvailPort() throws Exception {
        ex.expect(IllegalStateException.class);
        canConnect("localhost", 0).withRetries(RETRY_COUNT).bySshWithEveryRetryWaitFor(DELAY, MILLISECONDS);
    }

    @Test
    public void shouldWaitIfPortAvailableButNotSshUntilTimeoutAndThrowEx() throws Exception {
        ex.expect(IOException.class);
        long before = currentTimeMillis();
        try {
            canConnect(server.host(), server.port()).withRetries(RETRY_COUNT)
                    .bySshWithEveryRetryWaitFor(DELAY, MILLISECONDS);
        } catch (IOException e) {
            assertThat("Should wait for timeout", new Date(currentTimeMillis()),
                    greaterThanOrEqualTo(new Date(before + RETRY_COUNT * DELAY)));
            throw e;
        }
    }

    @Test
    public void shouldReturnWithoutWaitIfPortAvailable() throws Exception {
        long before = currentTimeMillis();
        assertThat("Used port should be connectible",
                canConnect(server.host(), server.port()).withEveryRetryWaitFor(DELAY, MILLISECONDS), is(true));
        assertThat("Should not wait", new Date(currentTimeMillis()), lessThan(new Date(before + DELAY)));
    }

    @Test
    public void shouldRetryIfPortIsNotAvailableNow() throws Exception {
        int retries = RETRY_COUNT * 2;

        long before = currentTimeMillis();
        server.stopAndRebindAfter(2 * DELAY, MILLISECONDS);

        assertThat("Used port should be connectible",
                canConnect(server.host(), server.port())
                        .withRetries(retries).withEveryRetryWaitFor(DELAY, MILLISECONDS), is(true));

        assertThat("Should wait then retry", new Date(currentTimeMillis()),
                both(greaterThanOrEqualTo(new Date(before + 2 * DELAY)))
                        .and(lessThan(new Date(before + retries * DELAY))));
    }

    private class SomeServerRule extends ExternalResource {
        private ServerSocket socket;

        public int port() {
            return socket.getLocalPort();
        }

        public String host() {
            return socket.getInetAddress().getHostAddress();
        }

        public void stopAndRebindAfter(long delay, TimeUnit unit) throws IOException {
            final int port = port();
            socket.close();
            Executors.newSingleThreadScheduledExecutor().schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        socket = new ServerSocket(port);
                    } catch (IOException e) {
                        throw new RuntimeException("Can't rebind socket", e);
                    }
                }
            }, delay, unit);
        }

        @Override
        protected void before() throws Throwable {
            socket = new ServerSocket(0);
            socket.setReuseAddress(true);
        }

        @Override
        protected void after() {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
