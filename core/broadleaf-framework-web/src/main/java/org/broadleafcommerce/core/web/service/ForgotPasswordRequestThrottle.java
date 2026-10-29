/*-
 * #%L
 * BroadleafCommerce Framework Web
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
package org.broadleafcommerce.core.web.service;

import org.apache.commons.lang3.StringUtils;
import org.broadleafcommerce.common.time.SystemTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.HttpServletRequest;

/**
 * In-memory, per-node throttle for unauthenticated forgot-password requests, keyed by client address and by
 * username. This is a first line of defense; {@code CustomerServiceImpl} also caps the number of reset tokens a
 * customer can be issued, which holds across nodes.
 * <p>
 * Counters live in a fixed number of buckets selected by a keyed hash (HMAC with a per-instance random key), so
 * memory is bounded without ever dropping or sharing counters wholesale. Flooding with many distinct keys only
 * raises counts in the buckets those keys land in, and an attacker cannot aim collisions at a particular username.
 */
@Service("blForgotPasswordRequestThrottle")
public class ForgotPasswordRequestThrottle {

    protected static final int DEFAULT_BUCKET_COUNT = 131_072;

    protected static final String HASH_ALGORITHM = "HmacSHA256";

    @Value("${forgot.password.throttle.window.seconds:900}")
    protected long windowSeconds = 900;

    @Value("${forgot.password.throttle.max.requests.per.client:20}")
    protected int maxRequestsPerClient = 20;

    @Value("${forgot.password.throttle.max.requests.per.username:5}")
    protected int maxRequestsPerUsername = 5;

    protected long[] windowStarts;

    protected int[] counts;

    protected SecretKeySpec hashKey;

    public ForgotPasswordRequestThrottle() {
        setBucketCount(DEFAULT_BUCKET_COUNT);
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        setHashKey(key);
    }

    /**
     * Records a forgot-password attempt and returns false when the client address or the username has exceeded its
     * allowance for the current window.
     */
    public boolean tryAcquire(HttpServletRequest request, String username) {
        long now = SystemTime.asMillis();
        if (maxRequestsPerClient > 0 && request != null && request.getRemoteAddr() != null
                && increment("client:" + request.getRemoteAddr(), now) > maxRequestsPerClient) {
            return false;
        }
        if (maxRequestsPerUsername > 0 && StringUtils.isNotBlank(username)) {
            String key = "username:" + username.trim().toLowerCase(Locale.ROOT);
            return increment(key, now) <= maxRequestsPerUsername;
        }
        return true;
    }

    /**
     * Returns the request count in the current window for the bucket that {@code key} hashes to.
     */
    protected synchronized int increment(String key, long now) {
        int bucket = bucketFor(key);
        if (counts[bucket] == 0 || now - windowStarts[bucket] >= windowSeconds * 1000L) {
            windowStarts[bucket] = now;
            counts[bucket] = 1;
        } else if (counts[bucket] < Integer.MAX_VALUE) {
            counts[bucket]++;
        }
        return counts[bucket];
    }

    protected int bucketFor(String key) {
        try {
            Mac mac = Mac.getInstance(HASH_ALGORITHM);
            mac.init(hashKey);
            byte[] digest = mac.doFinal(key.getBytes(StandardCharsets.UTF_8));
            return (int) Long.remainderUnsigned(ByteBuffer.wrap(digest).getLong(), counts.length);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public void setMaxRequestsPerClient(int maxRequestsPerClient) {
        this.maxRequestsPerClient = maxRequestsPerClient;
    }

    public void setMaxRequestsPerUsername(int maxRequestsPerUsername) {
        this.maxRequestsPerUsername = maxRequestsPerUsername;
    }

    public synchronized void setBucketCount(int bucketCount) {
        this.windowStarts = new long[bucketCount];
        this.counts = new int[bucketCount];
    }

    protected void setHashKey(byte[] key) {
        this.hashKey = new SecretKeySpec(key, HASH_ALGORITHM);
    }

}
