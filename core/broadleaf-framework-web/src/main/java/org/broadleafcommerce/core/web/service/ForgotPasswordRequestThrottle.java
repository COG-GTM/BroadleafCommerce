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
 * Client addresses and usernames are counted in two separate count-min sketches: each key maps to one cell in each
 * of {@link #ROWS} rows via a keyed hash (HMAC with a per-instance random key), and its count is the minimum across
 * those cells. Memory is fixed and counters are never evicted or pooled, yet a key is only over-counted when every
 * one of its cells collides with busier keys, and an attacker cannot aim collisions at a particular username.
 */
@Service("blForgotPasswordRequestThrottle")
public class ForgotPasswordRequestThrottle {

    protected static final int ROWS = 4;

    protected static final int DEFAULT_BUCKETS_PER_ROW = 32_768;

    protected static final String HASH_ALGORITHM = "HmacSHA256";

    @Value("${forgot.password.throttle.window.seconds:900}")
    protected long windowSeconds = 900;

    @Value("${forgot.password.throttle.max.requests.per.client:20}")
    protected int maxRequestsPerClient = 20;

    @Value("${forgot.password.throttle.max.requests.per.username:5}")
    protected int maxRequestsPerUsername = 5;

    protected CountMinSketch clientCounts;

    protected CountMinSketch usernameCounts;

    protected SecretKeySpec hashKey;

    public ForgotPasswordRequestThrottle() {
        setBucketsPerRow(DEFAULT_BUCKETS_PER_ROW);
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
                && clientCounts.increment(request.getRemoteAddr(), now) > maxRequestsPerClient) {
            return false;
        }
        if (maxRequestsPerUsername > 0 && StringUtils.isNotBlank(username)) {
            return usernameCounts.increment(username.trim().toLowerCase(Locale.ROOT), now) <= maxRequestsPerUsername;
        }
        return true;
    }

    protected long[] hash(String key) {
        try {
            Mac mac = Mac.getInstance(HASH_ALGORITHM);
            mac.init(hashKey);
            ByteBuffer digest = ByteBuffer.wrap(mac.doFinal(key.getBytes(StandardCharsets.UTF_8)));
            long[] hashes = new long[ROWS];
            for (int row = 0; row < ROWS; row++) {
                hashes[row] = digest.getLong();
            }
            return hashes;
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

    public void setBucketsPerRow(int bucketsPerRow) {
        this.clientCounts = new CountMinSketch(bucketsPerRow);
        this.usernameCounts = new CountMinSketch(bucketsPerRow);
    }

    protected void setHashKey(byte[] key) {
        this.hashKey = new SecretKeySpec(key, HASH_ALGORITHM);
    }

    protected class CountMinSketch {

        protected final long[][] windowStarts;

        protected final int[][] counts;

        protected CountMinSketch(int bucketsPerRow) {
            windowStarts = new long[ROWS][bucketsPerRow];
            counts = new int[ROWS][bucketsPerRow];
        }

        /**
         * Records one request for {@code key} and returns its estimated count in the current window.
         */
        protected synchronized int increment(String key, long now) {
            long[] hashes = hash(key);
            long windowMillis = windowSeconds * 1000L;
            int estimate = Integer.MAX_VALUE;
            for (int row = 0; row < ROWS; row++) {
                int cell = (int) Long.remainderUnsigned(hashes[row], counts[row].length);
                if (counts[row][cell] == 0 || now - windowStarts[row][cell] >= windowMillis) {
                    windowStarts[row][cell] = now;
                    counts[row][cell] = 1;
                } else if (counts[row][cell] < Integer.MAX_VALUE) {
                    counts[row][cell]++;
                }
                estimate = Math.min(estimate, counts[row][cell]);
            }
            return estimate;
        }
    }

}
