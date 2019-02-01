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

import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.allOf;

/**
 * @author lanwen (Merkushev Kirill)
 */
public class PortUtilsTest {
    /** number of tries minus 1 */
    public static final int RETRY_COUNT = 2;
    /** milliseconds */
    public static final int DELAY = (int) SECONDS.toMillis(1);

    @Rule
    public SomeServerRule server = new SomeServerRule();

    @Rule
    public ExpectedException ex = ExpectedException.none();

    @Test
    public void shouldConnectToServerSuccessfully() throws Exception {
        assertThat("Server is up and should connect",
                PortUtils.connectionCheck(server.host(), server.port()).executeOnce(), is(true));
    }

    @Test
    public void shouldNotConnectToUnusedPort() throws Exception {
        assertThat("Unused port should not be connectible", PortUtils.connectionCheck("localhost", 0).executeOnce(),
                is(false));
    }

    @Test
    public void shouldWaitForPortAvailableUntilTimeout() throws Exception {
        // Given
        // e.g. try, delay, try, delay, try = 3 tries, 2 delays.
        final long minExpectedTime = RETRY_COUNT * DELAY;
        final long maxExpectedTime = (RETRY_COUNT + 1) * DELAY - 1;

        // When
        final long before = currentTimeMillis();
        final boolean actual = PortUtils.connectionCheck("localhost", 0).withRetries(RETRY_COUNT)
                .withEveryRetryWaitFor(DELAY, MILLISECONDS).execute();
        final long after = currentTimeMillis();
        final long actualDuration = after - before;

        // Then
        assertThat("Unused port should not be connectible", actual, is(false));
        assertThat("Should wait for timeout", actualDuration,
                allOf(greaterThanOrEqualTo(minExpectedTime), lessThanOrEqualTo(maxExpectedTime)));
    }

    @Test
    public void shouldThrowIllegalStateExOnNotAvailPort() throws Exception {
        ex.expect(IllegalStateException.class);
        PortUtils.connectionCheck("localhost", 0).withRetries(RETRY_COUNT).withEveryRetryWaitFor(DELAY, MILLISECONDS)
                .useSSH().execute();
    }

    @Test
    public void shouldWaitIfPortAvailableButNotSshUntilTimeout() throws Exception {
        // Given
        // e.g. try, delay, try, delay, try = 3 tries, 2 delays.
        final long minExpectedTime = RETRY_COUNT * DELAY;
        final long maxExpectedTime = (RETRY_COUNT + 1) * DELAY - 1;

        // When
        final long before = currentTimeMillis();
        final boolean actual = PortUtils.connectionCheck(server.host(), server.port()).withRetries(RETRY_COUNT)
                .withEveryRetryWaitFor(DELAY, MILLISECONDS).useSSH().execute();
        final long after = currentTimeMillis();
        final long actualDuration = after - before;

        // Then
        assertThat(actual, is(false));
        assertThat("Should wait for timeout", actualDuration,
                allOf(greaterThanOrEqualTo(minExpectedTime), lessThanOrEqualTo(maxExpectedTime)));
    }

    @Test
    public void shouldReturnWithoutWaitIfPortAvailable() throws Exception {
        // Given
        final long maxExpectedTime = DELAY - 1;

        // When
        final long before = currentTimeMillis();
        final boolean actual = PortUtils.connectionCheck(server.host(), server.port())
                .withEveryRetryWaitFor(DELAY, MILLISECONDS).execute();
        final long after = currentTimeMillis();
        final long actualDuration = after - before;

        // Then
        assertThat("Used port should be connectible", actual, is(true));
        assertThat("Should not wait", actualDuration, lessThanOrEqualTo(maxExpectedTime));
    }

    @Test
    public void shouldRetryIfPortIsNotAvailableNow() throws Exception {
        // Given
        int retries = 4;
        // e.g. try, delay, try, delay, try, delay, try, delay, try = 5 tries, 4 delays.
        // expecting port to become available during the second delay. 
        final long bringPortUpAfter = DELAY + DELAY/2;
        final long minExpectedTime = 2 * DELAY;
        final long maxExpectedTime = minExpectedTime + DELAY - 1;
        server.stopAndRebindAfter(bringPortUpAfter, MILLISECONDS);

        // When
        final long before = currentTimeMillis();
        final boolean actual = PortUtils.connectionCheck(server.host(), server.port()).withRetries(retries)
                .withEveryRetryWaitFor(DELAY, MILLISECONDS).execute();
        final long after = currentTimeMillis();
        final long actualDuration = after - before;

        // Then
        assertThat("Used port should be connectible", actual, is(true));
        assertThat("Should wait then retry", actualDuration,
                allOf(greaterThanOrEqualTo(minExpectedTime), lessThanOrEqualTo(maxExpectedTime)));
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
