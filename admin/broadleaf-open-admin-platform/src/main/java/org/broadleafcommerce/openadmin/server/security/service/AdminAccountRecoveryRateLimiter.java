/*-
 * #%L
 * BroadleafCommerce Open Admin Platform
 * %%
 * Copyright (C) 2009 - 2026 Broadleaf Commerce
 * %%
 * Licensed under the Broadleaf Fair Use License Agreement, Version 1.0
 * (the "Fair Use License" located  at http://license.broadleafcommerce.org/fair_use_license-1.0.txt)
 * unless the restrictions on use therein are violated and require payment to Broadleaf in which case
 * the Broadleaf End User License Agreement (EULA), Version 1.1
 * (the "Commercial License" located at http://license.broadleafcommerce.org/commercial_license-1.1.txt)
 * shall apply.
 * 
 * Alternatively, the Commercial License may be replaced with a mutually agreed upon license (the "Custom License")
 * between you and Broadleaf Commerce. You may not use this file except in compliance with the applicable license.
 * #L%
 */
package org.broadleafcommerce.openadmin.server.security.service;

import org.apache.commons.lang3.StringUtils;
import org.broadleafcommerce.common.time.SystemTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-node sliding window limiter for the anonymous admin forgot password / forgot username
 * endpoints. Requests are counted per client address and per submitted identifier (username or email).
 * Callers must treat a rejected request exactly like an accepted one from the user's point of view so the
 * limiter itself does not become an account enumeration oracle.
 * <p>
 * At most {@link #maxTrackedKeys} keys are tracked. When that cap is reached and no expired keys can be
 * pruned, requests that would need a new key are rejected so that a flood of unique identifiers or
 * addresses cannot exhaust the heap.
 */
@Component("blAdminAccountRecoveryRateLimiter")
public class AdminAccountRecoveryRateLimiter {

    protected final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    /**
     * Earliest time at which a prune can free a key; avoids rescanning a full map on every request.
     */
    protected volatile long nextPruneAt = 0L;

    @Value("${admin.account.recovery.rateLimit.maxPerClient:10}")
    protected int maxAttemptsPerClient = 10;

    @Value("${admin.account.recovery.rateLimit.maxPerIdentifier:3}")
    protected int maxAttemptsPerIdentifier = 3;

    @Value("${admin.account.recovery.rateLimit.windowSeconds:900}")
    protected long windowSeconds = 900;

    @Value("${admin.account.recovery.rateLimit.maxTrackedKeys:10000}")
    protected int maxTrackedKeys = 10000;

    /**
     * Records an attempt and returns whether it is within the configured limits.
     *
     * @param action        logical endpoint name, e.g. "resetPassword" or "forgotUsername"
     * @param clientAddress remote address of the caller
     * @param identifier    the submitted username or email address
     * @return true if the request may be processed, false if it should be silently dropped
     */
    public boolean tryAcquire(String action, String clientAddress, String identifier) {
        long now = SystemTime.asMillis();
        long windowStart = now - windowSeconds * 1000L;
        pruneIfNeeded(now, windowStart);

        if (!record(action + "|client|" + StringUtils.defaultString(clientAddress),
                maxAttemptsPerClient, now, windowStart)) {
            // Don't charge the identifier for requests that are already being dropped, otherwise a throttled
            // client could exhaust another user's recovery allowance without any email being sent.
            return false;
        }
        return record(action + "|id|" + normalize(identifier), maxAttemptsPerIdentifier, now, windowStart);
    }

    protected boolean record(String key, int max, long now, long windowStart) {
        if (max <= 0) {
            return true;
        }
        Deque<Long> timestamps = attempts.get(key);
        if (timestamps == null) {
            timestamps = trackNewKey(key);
            if (timestamps == null) {
                return false;
            }
        }
        synchronized (timestamps) {
            evictOlderThan(timestamps, windowStart);
            if (timestamps.size() >= max) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    /**
     * Keys are only added here and only removed in {@link #pruneIfNeeded}, both under this instance's lock, so
     * the capacity check is exact even under concurrent requests.
     *
     * @return the timestamps for the key, or null if the cap has been reached
     */
    protected synchronized Deque<Long> trackNewKey(String key) {
        Deque<Long> timestamps = attempts.get(key);
        if (timestamps == null) {
            if (attempts.size() >= maxTrackedKeys) {
                return null;
            }
            timestamps = new ArrayDeque<>();
            attempts.put(key, timestamps);
        }
        return timestamps;
    }

    protected synchronized void pruneIfNeeded(long now, long windowStart) {
        if (attempts.size() < maxTrackedKeys || now < nextPruneAt) {
            return;
        }
        long oldestRemaining = Long.MAX_VALUE;
        Iterator<Map.Entry<String, Deque<Long>>> it = attempts.entrySet().iterator();
        while (it.hasNext()) {
            Deque<Long> timestamps = it.next().getValue();
            synchronized (timestamps) {
                evictOlderThan(timestamps, windowStart);
                if (timestamps.isEmpty()) {
                    it.remove();
                } else {
                    oldestRemaining = Math.min(oldestRemaining, timestamps.peekFirst());
                }
            }
        }
        nextPruneAt = oldestRemaining == Long.MAX_VALUE ? 0L : oldestRemaining + windowSeconds * 1000L;
    }

    protected void evictOlderThan(Deque<Long> timestamps, long windowStart) {
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
            timestamps.pollFirst();
        }
    }

    protected String normalize(String identifier) {
        return StringUtils.trimToEmpty(identifier).toLowerCase(Locale.ROOT);
    }

    public void setMaxAttemptsPerClient(int maxAttemptsPerClient) {
        this.maxAttemptsPerClient = maxAttemptsPerClient;
    }

    public void setMaxAttemptsPerIdentifier(int maxAttemptsPerIdentifier) {
        this.maxAttemptsPerIdentifier = maxAttemptsPerIdentifier;
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public void setMaxTrackedKeys(int maxTrackedKeys) {
        this.maxTrackedKeys = maxTrackedKeys;
    }

}
