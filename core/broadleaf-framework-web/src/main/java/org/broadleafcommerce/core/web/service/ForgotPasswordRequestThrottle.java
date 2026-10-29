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

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;

/**
 * In-memory, per-node throttle for unauthenticated forgot-password requests, keyed by client address and by
 * username. This is a first line of defense; {@code CustomerServiceImpl} also caps the number of reset tokens a
 * customer can be issued, which holds across nodes.
 */
@Service("blForgotPasswordRequestThrottle")
public class ForgotPasswordRequestThrottle {

    protected static final int MAX_TRACKED_KEYS = 100_000;

    protected final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Value("${forgot.password.throttle.window.seconds:900}")
    protected long windowSeconds = 900;

    @Value("${forgot.password.throttle.max.requests.per.client:20}")
    protected int maxRequestsPerClient = 20;

    @Value("${forgot.password.throttle.max.requests.per.username:5}")
    protected int maxRequestsPerUsername = 5;

    /**
     * Records a forgot-password attempt and returns false when the client address or the username has exceeded its
     * allowance for the current window.
     */
    public boolean tryAcquire(HttpServletRequest request, String username) {
        long now = SystemTime.asMillis();
        if (windows.size() > MAX_TRACKED_KEYS) {
            evictExpired(now);
        }
        boolean allowed = true;
        if (maxRequestsPerClient > 0 && request != null && request.getRemoteAddr() != null) {
            allowed = increment("client:" + request.getRemoteAddr(), now) <= maxRequestsPerClient;
        }
        if (maxRequestsPerUsername > 0 && StringUtils.isNotBlank(username)) {
            String key = "username:" + username.trim().toLowerCase(Locale.ROOT);
            allowed = increment(key, now) <= maxRequestsPerUsername && allowed;
        }
        return allowed;
    }

    protected int increment(String key, long now) {
        long windowMillis = windowSeconds * 1000L;
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start >= windowMillis) {
                return new Window(now, 1);
            }
            return new Window(existing.start, existing.count + 1);
        });
        return window.count;
    }

    protected void evictExpired(long now) {
        long windowMillis = windowSeconds * 1000L;
        windows.entrySet().removeIf(e -> now - e.getValue().start >= windowMillis);
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.clear();
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

    protected record Window(long start, int count) {
    }

}
