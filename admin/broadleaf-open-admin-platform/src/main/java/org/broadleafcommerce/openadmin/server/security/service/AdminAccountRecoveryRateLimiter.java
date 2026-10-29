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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * In-memory, per-node sliding window limiter for the anonymous admin forgot password / forgot username
 * endpoints. Requests are counted per client address and per submitted identifier (username or email).
 * Callers must treat a rejected request exactly like an accepted one from the user's point of view so the
 * limiter itself does not become an account enumeration oracle.
 * <p>
 * Identifiers are trimmed and case-folded so that case or whitespace variants which a case-insensitive
 * database would resolve to the same account cannot be used to get extra allowances for that account.
 * <p>
 * At most {@link #maxTrackedKeys} keys are tracked so that a flood of unique identifiers or addresses cannot
 * exhaust the heap. When the cap is reached the least recently used key is evicted, so a flood can neither
 * lock legitimate users out nor stop new callers from being counted; resetting a particular caller's
 * allowance requires pushing {@link #maxTrackedKeys} other keys through the limiter first.
 */
@Component("blAdminAccountRecoveryRateLimiter")
public class AdminAccountRecoveryRateLimiter {

    /**
     * Access ordered so the eldest entry is the least recently used key. Guarded by this instance's lock.
     */
    protected final LinkedHashMap<String, Deque<Long>> attempts = new LinkedHashMap<String, Deque<Long>>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Deque<Long>> eldest) {
            return size() > maxTrackedKeys;
        }
    };

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
    public synchronized boolean tryAcquire(String action, String clientAddress, String identifier) {
        long now = SystemTime.asMillis();
        long windowStart = now - windowSeconds * 1000L;

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
        Deque<Long> timestamps = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
            timestamps.pollFirst();
        }
        if (timestamps.size() >= max) {
            return false;
        }
        timestamps.addLast(now);
        return true;
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
