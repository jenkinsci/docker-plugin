package io.jenkins.docker.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class UsageTrackingCacheTest {

    @Test
    void getAndIncrementUsageGivenEmptyCacheThenReturnsNull() {
        final String key = "key";
        final List<Object> expiryList = new ArrayList<>();
        final UsageTrackingCache.ExpiryHandler<String, Object> expiryHandler = expiryTracker(expiryList);
        final UsageTrackingCache<String, Object> instance = new UsageTrackingCache<>(1, TimeUnit.DAYS, expiryHandler);

        final Object actual = instance.getAndIncrementUsage(key);

        assertNull(actual);
        assertNothingExpired(expiryList);
    }

    @Test
    void cacheAndIncrementUsageGivenClashingEntryThenThrows() {
        final String key = "key";
        final Object value = value("value");
        final List<Object> expiryList = new ArrayList<>();
        final UsageTrackingCache.ExpiryHandler<String, Object> expiryHandler = expiryTracker(expiryList);
        final UsageTrackingCache<String, Object> instance = new UsageTrackingCache<>(1, TimeUnit.DAYS, expiryHandler);
        instance.cacheAndIncrementUsage(key, value);

        assertThrows(IllegalStateException.class, () -> instance.cacheAndIncrementUsage(key, value));
        assertNothingExpired(expiryList);
    }

    @Test
    void getAndIncrementUsageGivenActiveDataThenReturnsSameDataEveryTime() {
        final String key = "key";
        final Object value = value("value");
        final List<Object> expiryList = new ArrayList<>();
        final UsageTrackingCache.ExpiryHandler<String, Object> expiryHandler = expiryTracker(expiryList);
        final UsageTrackingCache<String, Object> instance = new UsageTrackingCache<>(1, TimeUnit.DAYS, expiryHandler);
        instance.cacheAndIncrementUsage(key, value);

        final Object actual1 = instance.getAndIncrementUsage(key);
        final Object actual2 = instance.getAndIncrementUsage(key);
        final Object actual3 = instance.getAndIncrementUsage(key);

        assertEquals(value, actual1);
        assertEquals(value, actual2);
        assertEquals(value, actual3);
        assertNothingExpired(expiryList);
    }

    @Test
    void decrementUsageGivenNoActivityThenThrows() {
        final Object value = value("value");
        final List<Object> expiryList = new ArrayList<>();
        final UsageTrackingCache.ExpiryHandler<String, Object> expiryHandler = expiryTracker(expiryList);
        final UsageTrackingCache<String, Object> instance = new UsageTrackingCache<>(1, TimeUnit.DAYS, expiryHandler);

        assertThrows(IllegalStateException.class, () -> instance.decrementUsage(value));
        assertNothingExpired(expiryList);
    }

    @Test
    void decrementUsageGivenOneTooManyCallsThenThrows() {
        final String key = "key";
        final Object value = value("value");
        final List<Object> expiryList = new ArrayList<>();
        final UsageTrackingCache.ExpiryHandler<String, Object> expiryHandler = expiryTracker(expiryList);
        final UsageTrackingCache<String, Object> instance = new UsageTrackingCache<>(1, TimeUnit.DAYS, expiryHandler);
        instance.cacheAndIncrementUsage(key, value); // count=1
        final Object actual1 = instance.getAndIncrementUsage(key); // count=2
        assertEquals(value, actual1);
        instance.decrementUsage(value); // count=1
        instance.decrementUsage(value); // count=0 so inactive

        assertThrows(IllegalStateException.class, () -> instance.decrementUsage(value));
        assertNothingExpired(expiryList);
    }

    @Test
    void getAndIncrementUsageGivenRecentButInactiveDataInCacheThenReturnsCachedData() {
        final String key = "key";
        final Object value = value("value");
        final List<Object> expiryList = new ArrayList<>();
        final UsageTrackingCache.ExpiryHandler<String, Object> expiryHandler = expiryTracker(expiryList);
        final UsageTrackingCache<String, Object> instance = new UsageTrackingCache<>(1, TimeUnit.DAYS, expiryHandler);
        instance.cacheAndIncrementUsage(key, value); // count=1
        instance.decrementUsage(value); // count=0 so inactive

        final Object actual = instance.getAndIncrementUsage(key);

        assertEquals(value, actual);
        assertNothingExpired(expiryList);
    }

    @Test
    void getAndIncrementUsageGivenOldInactiveDataInCacheThenDiscardsOldDataAndReturnsNull() throws Exception {
        final String key = "key";
        final Object value = value("value");
        final List<Object> expiryList = new ArrayList<>();
        final UsageTrackingCache.ExpiryHandler<String, Object> expiryHandler = expiryTracker(expiryList);
        final UsageTrackingCache<String, Object> instance =
                new UsageTrackingCache<>(1, TimeUnit.MILLISECONDS, expiryHandler);
        instance.cacheAndIncrementUsage(key, value); // count=1
        instance.decrementUsage(value); // count=0 so inactive
        // cache could expire the entry any time from now onwards
        Thread.sleep(50L); // force cache to expire

        final Object actual = instance.getAndIncrementUsage(key);
        assertNull(actual);

        assertExpired(expiryList, key, value);
    }

    @Test
    void expiryHandlerGivenOldActiveDataInCacheThenNotCalled() throws Exception {
        final String key = "key";
        final Object value = value("value");
        final List<Object> expiryList = new ArrayList<>();
        final UsageTrackingCache.ExpiryHandler<String, Object> expiryHandler = expiryTracker(expiryList);
        final UsageTrackingCache<String, Object> instance =
                new UsageTrackingCache<>(1, TimeUnit.MILLISECONDS, expiryHandler);
        instance.cacheAndIncrementUsage(key, value); // count=1
        // cache could expire the entry any time from now onwards, but shouldn't as it's active
        Thread.sleep(50L); // force cache to expire

        final Object actual = instance.getAndIncrementUsage(key);
        assertEquals(value, actual);

        assertNothingExpired(expiryList);
    }

    private static Object value(final String s) {
        return new Object() {
            @Override
            public String toString() {
                return s;
            }
        };
    }

    private static <K extends L, V extends L, L> UsageTrackingCache.ExpiryHandler<K, V> expiryTracker(
            final List<L> expiryList) {
        return (key, value) -> {
            expiryList.add(key);
            expiryList.add(value);
        };
    }

    private static <L> void assertNothingExpired(List<L> list) {
        assertNotNull(list);
        assertEquals(0, list.size(), "Number of keys and values in the expiryList");
    }

    private static <K extends L, V extends L, L> void assertExpired(List<L> expiryList, K key, V value) {
        assertNotNull(expiryList);
        assertEquals(2, expiryList.size(), "Number of keys and values in the expiryList");
        final L actualExpiredKey = expiryList.get(0);
        assertEquals(key, actualExpiredKey, "Expired key");
        final L actualExpiredValue = expiryList.get(1);
        assertEquals(value, actualExpiredValue, "Expired value");
    }
}
