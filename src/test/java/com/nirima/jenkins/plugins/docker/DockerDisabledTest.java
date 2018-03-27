package com.nirima.jenkins.plugins.docker;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class DockerDisabledTest {

    @Test
    public void testHashCodeAndEquals() {
        final DockerDisabled i1 = new DockerDisabled();
        final DockerDisabled i2 = new DockerDisabled();
        i2.setDisabledByChoice(true);
        final DockerDisabled e1 = new DockerDisabled();
        final DockerDisabled e2 = new DockerDisabled();
        e2.setDisabledByChoice(true);
        e2.disableBySystem("non", 100L, null);

        assertEquals(i1, i1);
        assertEquals(i1, e1);
        assertEquals(i1.hashCode(), e1.hashCode());
        assertEquals(i2, e2);
        assertEquals(i2.hashCode(), e2.hashCode());
        assertNotEquals(i1, i2);
        assertNotEquals(i1.hashCode(), i2.hashCode());
        assertNotEquals(i1, null);
        assertNotEquals(i1, "foo");
    }

    @Test
    public void isDisabledGivenDefaultsThenReturnsFalse() {
        final TestClass i = new TestClass();

        final boolean actual = i.isDisabled();

        assertFalse(actual);
    }

    @Test
    public void isDisabledGivenUserNotDisabledThenReturnsFalse() {
        final TestClass i = new TestClass();
        i.setDisabledByChoice(false);

        final boolean actual = i.isDisabled();

        assertFalse(actual);
    }

    @Test
    public void isDisabledGivenUserDisabledThenReturnsTrue() {
        final TestClass i = new TestClass();
        i.setDisabledByChoice(true);

        final boolean actual = i.isDisabled();

        assertTrue(actual);
    }

    @Test
    public void isDisabledGivenSystemDisabledThenReturnsTrue() {
        final TestClass i = new TestClass();
        final long duration = 10000L;
        i.disableBySystem("non", duration, null);

        final boolean actual = i.isDisabled();

        assertTrue(actual);
    }

    @Test
    public void isDisabledGivenSystemDisabledButTimeHasPassesThenReturnsFalseAfterTimeHasPassed() {
        final TestClass i = new TestClass();
        final long duration = 10000L;
        i.disableBySystem("non", duration, null);

        final boolean actual1 = i.isDisabled();
        i.moveTimeForwards(duration - 1);
        final boolean actual2 = i.isDisabled();
        i.moveTimeForwards(1);
        final boolean actual3 = i.isDisabled();
        i.moveTimeForwards(Long.MAX_VALUE);
        final boolean actual4 = i.isDisabled();

        assertTrue(actual1);
        assertTrue(actual2);
        assertFalse(actual3);
        assertFalse(actual4);
    }

    @Test
    public void testEnabledByChoiceIsOppositeOfDisabledByChoice() {
        final DockerDisabled i1 = new DockerDisabled();
        final DockerDisabled i2 = new DockerDisabled();
        i1.setDisabledByChoice(false);
        i2.setDisabledByChoice(true);
        final DockerDisabled e1 = new DockerDisabled();
        final DockerDisabled e2 = new DockerDisabled();
        e1.setEnabledByChoice(true);
        e2.setEnabledByChoice(false);

        assertFalse(i1.getDisabledByChoice());
        assertTrue(i1.getEnabledByChoice());
        assertFalse(e1.getDisabledByChoice());
        assertTrue(e1.getEnabledByChoice());

        assertTrue(i2.getDisabledByChoice());
        assertFalse(i2.getEnabledByChoice());
        assertTrue(e2.getDisabledByChoice());
        assertFalse(e2.getEnabledByChoice());

        assertEquals(i1, e1);
        assertEquals(i2, e2);
    }

    @Test
    public void getReasonWhyDisabledBySystemGivenGivenDefaultsThenReturnsEmptyString() {
        final TestClass i = new TestClass();

        final String actual = i.getReasonWhyDisabledBySystem();

        assertEquals("", actual);
    }

    @Test
    public void getReasonWhyDisabledBySystemGivenSystemDisabledThenReturnsReasonUntilTimeHasPassed() {
        final TestClass i = new TestClass();
        final long duration = 10000L;
        final String reasonGiven = "SomeError";
        i.disableBySystem(reasonGiven, duration, null);

        final String actual1 = i.getReasonWhyDisabledBySystem();
        i.moveTimeForwards(duration - 1);
        final String actual2 = i.getReasonWhyDisabledBySystem();
        i.moveTimeForwards(1);
        final String actual3 = i.getReasonWhyDisabledBySystem();
        i.moveTimeForwards(Long.MAX_VALUE);
        final String actual4 = i.getReasonWhyDisabledBySystem();

        assertEquals(reasonGiven, actual1);
        assertEquals(reasonGiven, actual2);
        assertEquals("", actual3);
        assertEquals("", actual4);
    }

    @Test
    public void getWhenDisabledBySystemStringGivenDefaultsThenReturnsEmptyString() {
        final TestClass i = new TestClass();

        final String actual = i.getWhenDisabledBySystemString();

        assertEquals("", actual);
    }

    @Test
    public void getWhenDisabledBySystemStringGivenSystemDisabledThenReturnsReasonUntilTimeHasPassed() {
        final TestClass i = new TestClass();
        final long duration = 60001L;
        final String expected1 = "0 ms";
        final String expected2 = "1 min 0 sec";
        i.disableBySystem("a reason", duration, null);

        final String actual1 = i.getWhenDisabledBySystemString();
        i.moveTimeForwards(duration - 1);
        final String actual2 = i.getWhenDisabledBySystemString();
        i.moveTimeForwards(1);
        final String actual3 = i.getWhenDisabledBySystemString();
        i.moveTimeForwards(Long.MAX_VALUE);
        final String actual4 = i.getWhenDisabledBySystemString();

        assertEquals(expected1, actual1);
        assertEquals(expected2, actual2);
        assertEquals("", actual3);
        assertEquals("", actual4);
    }

    @Test
    public void getWhenReEnableBySystemStringGivenDefaultsThenReturnsEmptyString() {
        final TestClass i = new TestClass();

        final String actual = i.getWhenReEnableBySystemString();

        assertEquals("", actual);
    }

    @Test
    public void getWhenReEnableBySystemStringGivenSystemDisabledThenReturnsReasonUntilTimeHasPassed() {
        final TestClass i = new TestClass();
        final long duration = 120000L;
        final String expected1 = "2 min 0 sec";
        final String expected2 = "1 ms";
        i.disableBySystem("another reason", duration, null);

        final String actual1 = i.getWhenReEnableBySystemString();
        i.moveTimeForwards(duration - 1);
        final String actual2 = i.getWhenReEnableBySystemString();
        i.moveTimeForwards(1);
        final String actual3 = i.getWhenReEnableBySystemString();
        i.moveTimeForwards(Long.MAX_VALUE);
        final String actual4 = i.getWhenReEnableBySystemString();

        assertEquals(expected1, actual1);
        assertEquals(expected2, actual2);
        assertEquals("", actual3);
        assertEquals("", actual4);
    }

    @Test
    public void getExceptionWhenDisabledBySystemStringGivenGivenDefaultsThenReturnsEmptyString() {
        final TestClass i = new TestClass();

        final String actual = i.getExceptionWhenDisabledBySystemString();

        assertEquals("", actual);
    }

    @Test
    public void getExceptionWhenDisabledBySystemStringGivenSystemDisabledThenReturnsReasonUntilTimeHasPassed() {
        final TestClass i = new TestClass();
        final long duration = 10000L;
        final String reasonGiven = "SomeError";
        final Throwable ex = new RuntimeException("FakeException");
        final String expected = ex.toString();
        i.disableBySystem(reasonGiven, duration, ex);

        final String actual1 = i.getExceptionWhenDisabledBySystemString();
        i.moveTimeForwards(duration - 1);
        final String actual2 = i.getExceptionWhenDisabledBySystemString();
        i.moveTimeForwards(1);
        final String actual3 = i.getExceptionWhenDisabledBySystemString();
        i.moveTimeForwards(Long.MAX_VALUE);
        final String actual4 = i.getExceptionWhenDisabledBySystemString();

        assertThat(actual1, startsWith(expected));
        assertThat(actual2, startsWith(expected));
        assertEquals("", actual3);
        assertEquals("", actual4);
    }

    private static class TestClass extends DockerDisabled {
        long now = System.nanoTime();

        @Override
        protected long readTimeNowInNanoseconds() {
            return now;
        }

        public void moveTimeForwards(long milliseconds) {
            final long nanos = TimeUnit.MILLISECONDS.toNanos(milliseconds);
            now += nanos;
        }
    }
}
