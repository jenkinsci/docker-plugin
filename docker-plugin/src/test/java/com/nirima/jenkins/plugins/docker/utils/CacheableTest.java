package com.nirima.jenkins.plugins.docker.utils;

import junit.framework.TestCase;

import java.util.concurrent.Callable;

public class CacheableTest extends TestCase {

    boolean value;

    public void testGet() throws Exception {
        Cacheable<Boolean> c = new Cacheable<Boolean>( 0,
                new Callable<Boolean>() {
                    public Boolean call() throws Exception {

                        System.out.println("Called.");
                        value = !value;
                        return value;
                    }
                }
        );

        value = true;
        assertFalse(c.get());
        Thread.sleep(100);
        assertTrue(c.get());
        Thread.sleep(100);
        assertFalse(c.get());
    }

    public void testGet2() throws Exception {
        Cacheable<Boolean> c = new Cacheable<Boolean>( 5000,
                new Callable<Boolean>() {
                    public Boolean call() throws Exception {

                        System.out.println("Called.");
                        value = !value;
                        return value;
                    }
                }
        );

        value = true;
        assertFalse(c.get());
        Thread.sleep(100);
        assertFalse(c.get());
        Thread.sleep(5000);
        assertTrue(c.get());
    }
}