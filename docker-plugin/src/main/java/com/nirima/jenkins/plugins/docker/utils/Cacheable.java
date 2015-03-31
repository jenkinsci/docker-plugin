package com.nirima.jenkins.plugins.docker.utils;

import shaded.com.google.common.base.Function;
import shaded.com.google.common.base.Predicate;

import java.util.Date;
import java.util.concurrent.Callable;

public class Cacheable<T> {
    final long timeout;
    final Callable<T> updateFunction;

    private T nodeExistenceStatus;
    private long nodeExistenceStatusTimestamp;

    public Cacheable(long timeout, Callable<T> updateFunction) {
        this.timeout = timeout;
        this.updateFunction = updateFunction;
    }

    public T get() throws Exception {

        if( expiredCheck() ) {
            nodeExistenceStatus = updateFunction.call();
            nodeExistenceStatusTimestamp = new Date().getTime();
        }

        return nodeExistenceStatus;
    }

    private boolean expiredCheck() {
        return ( (nodeExistenceStatusTimestamp + timeout ) < new Date().getTime() );

    }

}
