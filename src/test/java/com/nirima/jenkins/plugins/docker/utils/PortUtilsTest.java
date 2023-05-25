package com.nirima.jenkins.plugins.docker.utils;

import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

/**
 * @author lanwen (Merkushev Kirill)
 */
public class PortUtilsTest {
    /** number of tries minus 1 */
    public static final int RETRY_COUNT = 2;
    /** 1 second in milliseconds */
    public static final int DELAY = (int) SECONDS.toMillis(1);

    @Rule
    public SomeServerRule server = new SomeServerRule();

    @Test
    public void shouldConnectToServerSuccessfully() throws Exception {
        // When
        boolean actual = PortUtils.connectionCheck(server.host(), server.port()).executeOnce();

        // Then
        assertThat("Server is up and should connect", actual, equalTo(true));
    }

    @Test
    public void shouldNotConnectToUnusedPort() throws Exception {
        // When
        boolean actual = PortUtils.connectionCheck("localhost", 0).executeOnce();

        // Then
        assertThat("Unused port should not be connectible", actual, equalTo(false));
    }

    @Test
    public void shouldWaitForPortAvailableUntilTimeout() throws Exception {
        // Given
        // e.g. try, delay, try, delay, try = 3 tries, 2 delays.
        final long minExpectedTime = RETRY_COUNT * DELAY - minimumFudgeFactor(DELAY);
        final long maxExpectedTime = (RETRY_COUNT + 1) * DELAY - 1;

        // When
        final long before = currentTimeMillis();
        final boolean actual = PortUtils.connectionCheck("localhost", 0)
                .withRetries(RETRY_COUNT)
                .withEveryRetryWaitFor(DELAY, MILLISECONDS)
                .execute();
        final long after = currentTimeMillis();
        final long actualDuration = after - before;

        // Then
        assertThat("Unused port should not be connectible", actual, equalTo(false));
        assertThat(
                "Should wait for timeout",
                actualDuration,
                allOf(greaterThanOrEqualTo(minExpectedTime), lessThanOrEqualTo(maxExpectedTime)));
    }

    @Test
    public void shouldThrowIllegalStateExOnNotAvailPort() throws Exception {
        // Given
        final Class<IllegalStateException> expectedType = IllegalStateException.class;

        // When
        Throwable thrown = null;
        try {
            PortUtils.connectionCheck("localhost", 0)
                    .withRetries(RETRY_COUNT)
                    .withEveryRetryWaitFor(DELAY, MILLISECONDS)
                    .useSSH()
                    .execute();
        } catch (Throwable expected) {
            thrown = expected;
        }

        // Then
        assertThat(expectedType.getSimpleName() + " thrown", thrown, instanceOf(expectedType));
    }

    @Test
    public void shouldWaitIfPortAvailableButNotSshUntilTimeout() throws Exception {
        // Given
        final int retries = 3;
        final int waitBetweenTries = DELAY / 2;
        final int sshWaitDuringTry = DELAY / 3;
        // e.g. try, delay, try, delay, try, delay, try = 4 tries, 3 delays.
        final long minExpectedTime = sshWaitDuringTry
                + waitBetweenTries
                + sshWaitDuringTry
                + waitBetweenTries
                + sshWaitDuringTry
                + waitBetweenTries
                + sshWaitDuringTry;
        final long maxExpectedTime = minExpectedTime + waitBetweenTries - 1;
        final long minAllowedTime = minExpectedTime - minimumFudgeFactor(waitBetweenTries);

        // When
        final long before = currentTimeMillis();
        final boolean actual = PortUtils.connectionCheck(server.host(), server.port())
                .withRetries(retries)
                .withEveryRetryWaitFor(waitBetweenTries, MILLISECONDS)
                .useSSH()
                .withSSHTimeout(sshWaitDuringTry, MILLISECONDS)
                .execute();
        final long after = currentTimeMillis();
        final long actualDuration = after - before;

        // Then
        assertThat("Port is connectible", actual, equalTo(false));
        assertThat(
                "Should wait for timeout",
                actualDuration,
                allOf(greaterThanOrEqualTo(minAllowedTime), lessThanOrEqualTo(maxExpectedTime)));
    }

    @Test
    public void shouldReturnWithoutWaitIfPortAvailable() throws Exception {
        // Given
        final long maxExpectedTime = DELAY - 1;

        // When
        final long before = currentTimeMillis();
        final boolean actual = PortUtils.connectionCheck(server.host(), server.port())
                .withEveryRetryWaitFor(DELAY, MILLISECONDS)
                .execute();
        final long after = currentTimeMillis();
        final long actualDuration = after - before;

        // Then
        assertThat("Used port should be connectible", actual, equalTo(true));
        assertThat("Should not wait", actualDuration, lessThanOrEqualTo(maxExpectedTime));
    }

    @Test
    public void shouldRetryIfPortIsNotAvailableNow() throws Exception {
        // Given
        int retries = 4;
        // e.g. try, delay, try, delay, try, delay, try, delay, try = 5 tries, 4 delays.
        // expecting port to become available during the second delay.
        final long bringPortUpAfter = DELAY + DELAY / 2;
        final long minExpectedTime = 2L * DELAY;
        final long maxExpectedTime = minExpectedTime + DELAY - 1;
        // It can take so long to test for the port availability that it's available
        // by the time the test completes (this is especially common on Windows where the
        // "can we open this socket?" test takes a couple of seconds the first time it's
        // done) and hence the test might "pass" the instant we make the socket available
        // instead of forcing us to retry.
        final long minAllowedTime = bringPortUpAfter - minimumFudgeFactor(DELAY);
        server.stopAndRebindAfter(bringPortUpAfter, MILLISECONDS);

        // When
        final long before = currentTimeMillis();
        final boolean actual = PortUtils.connectionCheck(server.host(), server.port())
                .withRetries(retries)
                .withEveryRetryWaitFor(DELAY, MILLISECONDS)
                .execute();
        final long after = currentTimeMillis();
        final long actualDuration = after - before;

        // Then
        assertThat("Used port should be connectible", actual, equalTo(true));
        assertThat(
                "Should try, fail, wait " + DELAY + ", retry(1), fail, wait " + DELAY + ", retry(2) and succeed",
                actualDuration,
                allOf(greaterThanOrEqualTo(minAllowedTime), lessThanOrEqualTo(maxExpectedTime)));
    }

    /**
     * On Windows, timers seem to be less accurate and/or expire shortly before they should,
     * meaning that tests can complete faster than they should,
     * e.g. I've seen a timeout of 2000ms complete in 1998ms,
     * so the tests must allow for that and not complain.
     */
    private static int minimumFudgeFactor(int oneUnitOfExpectedDelay) {
        return oneUnitOfExpectedDelay / 20;
    }

    private static class SomeServerRule extends ExternalResource {
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
            Executors.newSingleThreadScheduledExecutor()
                    .schedule(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        socket = new ServerSocket(port);
                                    } catch (IOException e) {
                                        throw new UncheckedIOException("Can't rebind socket", e);
                                    }
                                }
                            },
                            delay,
                            unit);
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
            } catch (IOException ignored) {
                // ignore
            }
        }
    }
}
