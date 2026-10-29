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
 */
@Component("blAdminAccountRecoveryRateLimiter")
public class AdminAccountRecoveryRateLimiter {

    protected static final int MAX_TRACKED_KEYS = 10000;

    protected final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    @Value("${admin.account.recovery.rateLimit.maxPerClient:10}")
    protected int maxAttemptsPerClient = 10;

    @Value("${admin.account.recovery.rateLimit.maxPerIdentifier:3}")
    protected int maxAttemptsPerIdentifier = 3;

    @Value("${admin.account.recovery.rateLimit.windowSeconds:900}")
    protected long windowSeconds = 900;

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
        pruneIfNeeded(windowStart);

        boolean clientAllowed = record(action + "|client|" + StringUtils.defaultString(clientAddress),
                maxAttemptsPerClient, now, windowStart);
        boolean identifierAllowed = record(action + "|id|" + normalize(identifier),
                maxAttemptsPerIdentifier, now, windowStart);
        return clientAllowed && identifierAllowed;
    }

    protected boolean record(String key, int max, long now, long windowStart) {
        if (max <= 0) {
            return true;
        }
        Deque<Long> timestamps = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            evictOlderThan(timestamps, windowStart);
            if (timestamps.size() >= max) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    protected void pruneIfNeeded(long windowStart) {
        if (attempts.size() < MAX_TRACKED_KEYS) {
            return;
        }
        Iterator<Map.Entry<String, Deque<Long>>> it = attempts.entrySet().iterator();
        while (it.hasNext()) {
            Deque<Long> timestamps = it.next().getValue();
            synchronized (timestamps) {
                evictOlderThan(timestamps, windowStart);
                if (timestamps.isEmpty()) {
                    it.remove();
                }
            }
        }
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

}
