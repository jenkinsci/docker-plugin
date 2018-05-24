package com.nirima.jenkins.plugins.docker.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class UniqueIdGeneratorTest {

    @Test
    public void getUniqueIdWhenCalledMultipleTimesThenReturnsUniqueIds() {
        // Given
        final UniqueIdGenerator instance = new UniqueIdGenerator(16);

        // When
        final String actual1 = instance.getUniqueId();
        final String actual2 = instance.getUniqueId();
        final String actual3 = instance.getUniqueId();
        final String actual4 = instance.getUniqueId();
        final String actual5 = instance.getUniqueId();

        // Then
        assertThat(actual1, not(equalTo(actual2)));
        assertThat(actual1, not(equalTo(actual3)));
        assertThat(actual1, not(equalTo(actual4)));
        assertThat(actual1, not(equalTo(actual5)));
        assertThat(actual2, not(equalTo(actual3)));
        assertThat(actual2, not(equalTo(actual4)));
        assertThat(actual2, not(equalTo(actual5)));
        assertThat(actual3, not(equalTo(actual4)));
        assertThat(actual3, not(equalTo(actual5)));
        assertThat(actual4, not(equalTo(actual5)));
    }

    @Test
    public void getUniqueIdWhenCalledFromMultipleThreadsAtOnceThenReturnsUniqueIds() throws InterruptedException {
        // Given
        final UniqueIdGenerator instance = new UniqueIdGenerator(16);
        final Object startingGun = new Object();
        final AtomicInteger numberOfThreadsReady = new AtomicInteger(0);
        final List<String> results = new ArrayList<>();
        final Runnable codeToRunInMultipleThreads = new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (startingGun) {
                        numberOfThreadsReady.incrementAndGet();
                        startingGun.wait();
                    }
                    final String actual = instance.getUniqueId();
                    synchronized (results) {
                        results.add(actual);
                    }
                } catch (InterruptedException e) {
                    // should not happen
                }
            }
        };
        final int numberOfThreads = 10;
        final List<Thread> threads = new ArrayList<>(numberOfThreads);
        for( int i=0 ; i<numberOfThreads ; i++) {
            final Thread t = new Thread(codeToRunInMultipleThreads, "testThread#"+i);
            threads.add(t);
            t.start();
        }
        while( numberOfThreadsReady.get()!=numberOfThreads) {
            Thread.yield();
        }
        
        // When
        synchronized(startingGun) {
            startingGun.notifyAll();
        }
        for( final Thread t : threads ) {
            t.join();
        }
        
        // Then
        assertThat(results, hasSize(numberOfThreads));
        final Set<String> uniqueResults = new TreeSet<>(results);
        assertThat(uniqueResults, hasSize(numberOfThreads));
    }

    @Test
    public void getUniqueIdWhenCalledWithRadixThenReturnsPaddedString() {
        // Given
        final UniqueIdGenerator i2 = new UniqueIdGenerator(2);
        final UniqueIdGenerator i10 = new UniqueIdGenerator(10);
        final UniqueIdGenerator i16 = new UniqueIdGenerator(16);
        final UniqueIdGenerator i36 = new UniqueIdGenerator(36);
        // long is 64 bits, and we're using unsigned numbers here, so
        final int expectedLength2 = 64; // = 64 chars in base 2
        final int expectedLength10 = 20; // = 20 chars in base 10
        final int expectedLength16 = 16; // = 16 chars in base 16
        final int expectedLength36 = 13; // = 13 chars in base 36

        // When
        final String actual2 = i2.getUniqueId();
        final String actual10 = i10.getUniqueId();
        final String actual16 = i16.getUniqueId();
        final String actual36 = i36.getUniqueId();

        // Then
        assertThat(actual2.length(), equalTo(expectedLength2));
        assertThat(actual10.length(), equalTo(expectedLength10));
        assertThat(actual16.length(), equalTo(expectedLength16));
        assertThat(actual36.length(), equalTo(expectedLength36));
        // Note: These tests will fail if nanoTime is over 4231600058744700927,
        // but that'll only happen if system uptime is over 134 years.
        assertThat(actual2, startsWith("0"));
        assertThat(actual10, startsWith("0"));
        assertThat(actual16, startsWith("0"));
        assertThat(actual36, startsWith("0"));
    }
}
