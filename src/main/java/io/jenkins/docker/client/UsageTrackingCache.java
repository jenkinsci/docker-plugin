package io.jenkins.docker.client;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

/**
 * A cache that keep things until they haven't been used for a given duration.
 * Things will be kept in the cache until they have been inactive for too long.
 * Things will not be dropped from the cache while they are active, no matter
 * how long that is.
 *
 * @param <K>
 *            The type of key by which cache entries can be indexed. This must
 *            implement {@link #hashCode()} and {@link #equals(Object)}.
 * @param <V>
 *            The type of entry being cached.
 */
public class UsageTrackingCache<K, V> {

    /**
     * Callback API to handle things that are no longer in use when they
     * eventually fall out of the cache.
     *
     * @param <K>
     *            The type of key used by the cache.
     * @param <V>
     *            The type of value stored by the cache.
     */
    public interface ExpiryHandler<K, V> {
        void entryDroppedFromCache(K key, V value);
    }

    /** Holds all active records, indexed by key */
    private final Map<K, CacheEntry<K, V>> activeCacheByKey;
    /** Holds all active records, indexed by value */
    private final Map<V, CacheEntry<K, V>> activeCacheByValue;
    /** Holds all inactive records for a period, indexed by key */
    private final Cache<K, CacheEntry<K, V>> durationCache;

    /**
     * Full constructor.
     * 
     * @param duration
     *            How long inactive things should be kept in the cache.
     * @param unit
     *            The <code>duration</code>'s unit of measurement.
     * @param expiryHandler
     *            Callback that is given all expired values from the cache just
     *            before they are thrown away.
     */
    UsageTrackingCache(final long duration, @Nonnull final TimeUnit unit,
            @Nonnull final ExpiryHandler<K, V> expiryHandler) {
        activeCacheByKey = new HashMap<>();
        activeCacheByValue = new IdentityHashMap();
        CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder();
        cacheBuilder = cacheBuilder.expireAfterAccess(duration, unit);
        final RemovalListener removalHandler = new RemovalListener<K, CacheEntry<K, V>>() {
            @Override
            public void onRemoval(RemovalNotification<K, CacheEntry<K, V>> notification) {
                final K key = notification.getKey();
                if (!activeCacheByKey.containsKey(key)) {
                    final CacheEntry<K, V> record = notification.getValue();
                    final V value = record.getValue();
                    expiryHandler.entryDroppedFromCache(key, value);
                }
            }
        };
        cacheBuilder = cacheBuilder.removalListener(removalHandler);
        durationCache = cacheBuilder.build();
    }

    /**
     * Looks up an existing entry in the cache. If it finds an entry then it
     * returns the entry and increments the usage count for that entry, and the
     * caller MUST ensure that {@link #decrementUsage(Object)} is later called
     * on the result. If it doesn't find an entry in the cache then it returns
     * null (and the caller will most likely decide to call
     * {@link #cacheAndIncrementUsage(Object, Object)} ).
     * 
     * @param key
     *            The key used to look up the entry in the cache.
     * @return An existing cache entry, or null.
     */
    @CheckForNull
    public V getAndIncrementUsage(@Nonnull K key) {
        final CacheEntry<K, V> activeRecord = activeCacheByKey.get(key);
        if (activeRecord != null) {
            // we have an entry that's currently in use
            activeRecord.incrementUsageCount(); // bump activity count
            final V value = activeRecord.getValue();
            durationCache.cleanUp(); // force cleanup activity
            return value;
        }
        final CacheEntry<K, V> durationRecord = durationCache.getIfPresent(key);
        if (durationRecord != null) {
            final V value = durationRecord.getValue();
            // while we don't have an entry that's currently in use, we do have
            // one that we stopped using recently.
            durationRecord.incrementUsageCount(); // bump its activity count
            // put it in the "active" cache
            activeCacheByKey.put(key, durationRecord);
            activeCacheByValue.put(value, durationRecord);
            // remove it from the duration cache
            durationCache.invalidate(key);
            durationCache.cleanUp();
            return value;
        }
        durationCache.cleanUp(); // force cleanup activity
        return null;
    }

    /**
     * Puts an entry in the cache with a usage count of 1. The caller MUST
     * ensure that {@link #decrementUsage(Object)} is later called on the entry
     * that has been cached.
     * 
     * @param key
     *            The key used to look up the entry in the cache.
     * @param entry
     *            The entry to be cached.
     */
    public void cacheAndIncrementUsage(@Nonnull K key, @Nonnull V entry) {
        final CacheEntry<K, V> record = new CacheEntry<>(key, entry, 1);
        final CacheEntry<K, V> oldKeyRecord = activeCacheByKey.put(key, record);
        final CacheEntry<K, V> oldValueRecord = activeCacheByValue.put(entry, record);
        if (oldKeyRecord != null || oldValueRecord != null) {
            final CacheEntry<K, V> oldRecord = oldKeyRecord != null ? oldKeyRecord : oldValueRecord;
            activeCacheByKey.put(key, oldRecord);
            activeCacheByValue.put(entry, oldRecord);
            throw new IllegalStateException("Cannot cache " + record + " because there's already a record " + oldRecord
                    + " present in the activeCache.");
        }
    }

    /**
     * Decrements the usage count on a cache entry, potentially removing it from
     * active usage. This method MUST be called once and only once for every
     * time that {@link #getAndIncrementUsage(Object)} returned a non-null value
     * and for every time that {@link #cacheAndIncrementUsage(Object, Object)}
     * was called.
     * 
     * @param entry
     *            The entry that is no longer in use.
     */
    public void decrementUsage(@Nonnull V entry) {
        durationCache.cleanUp(); // force cleanup activity
        final CacheEntry<K, V> record = activeCacheByValue.get(entry);
        if (record == null) {
            throw new IllegalStateException("No active record for entry " + entry);
        }
        final boolean stillActive = record.decrementUsageCount();
        if (stillActive) {
            return;
        }
        // if we got this far then the entry has just ceased to be active.
        final K key = record.getKey();
        activeCacheByKey.remove(key);
        activeCacheByValue.remove(entry);
        durationCache.put(key, record);
        durationCache.cleanUp();
    }

    private static class CacheEntry<K, V> {
        private final K mKey;
        private final V mValue;
        private int mUsageCount;

        CacheEntry(K key, V value, int usageCount) {
            this.mKey = key;
            this.mValue = value;
            this.mUsageCount = usageCount;
        }

        void incrementUsageCount() {
            mUsageCount++;
        }

        boolean decrementUsageCount() {
            mUsageCount--;
            return mUsageCount > 0;
        }

        K getKey() {
            return mKey;
        }

        V getValue() {
            return mValue;
        }

        @Override
        public String toString() {
            return "CacheEntry[key=" + mKey + ", value=" + mValue + ", usageCount=" + mUsageCount + "]";
        }
    }
}
