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

import org.broadleafcommerce.common.time.FixedTimeSource;
import org.broadleafcommerce.common.time.SystemTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForgotPasswordRequestThrottleTest {

    private long now = 1_800_000_000_000L;
    private ForgotPasswordRequestThrottle throttle;

    @Before
    public void setUp() {
        SystemTime.setGlobalTimeSource(new FixedTimeSource(now));
        throttle = new ForgotPasswordRequestThrottle();
        throttle.setWindowSeconds(60);
        throttle.setMaxRequestsPerClient(3);
        throttle.setMaxRequestsPerUsername(2);
    }

    @After
    public void tearDown() {
        SystemTime.reset();
    }

    @Test
    public void limitsRequestsPerUsernameAcrossClients() {
        assertTrue(throttle.tryAcquire(request("10.0.0.1"), "alice"));
        assertTrue(throttle.tryAcquire(request("10.0.0.2"), "Alice "));
        assertFalse(throttle.tryAcquire(request("10.0.0.3"), "alice"));
        assertTrue(throttle.tryAcquire(request("10.0.0.3"), "bob"));
    }

    @Test
    public void limitsRequestsPerClientAcrossUsernames() {
        assertTrue(throttle.tryAcquire(request("10.0.0.1"), "u1"));
        assertTrue(throttle.tryAcquire(request("10.0.0.1"), "u2"));
        assertTrue(throttle.tryAcquire(request("10.0.0.1"), "u3"));
        assertFalse(throttle.tryAcquire(request("10.0.0.1"), "u4"));
        assertTrue(throttle.tryAcquire(request("10.0.0.2"), "u4"));
    }

    @Test
    public void allowanceResetsAfterWindow() {
        throttle.tryAcquire(request("10.0.0.1"), "alice");
        throttle.tryAcquire(request("10.0.0.1"), "alice");
        assertFalse(throttle.tryAcquire(request("10.0.0.1"), "alice"));

        now += 61_000L;
        SystemTime.setGlobalTimeSource(new FixedTimeSource(now));

        assertTrue(throttle.tryAcquire(request("10.0.0.1"), "alice"));
    }

    @Test
    public void floodOfDistinctKeysDoesNotResetActiveCounters() {
        throttle.tryAcquire(request("10.0.0.1"), "alice");
        throttle.tryAcquire(request("10.0.0.1"), "alice");
        assertFalse(throttle.tryAcquire(request("10.0.0.1"), "alice"));

        flood(20_000);

        assertFalse(throttle.tryAcquire(request("10.0.0.1"), "alice"));
        assertFalse(throttle.tryAcquire(request("10.9.9.9"), "alice"));
    }

    @Test
    public void floodOfDistinctKeysDoesNotBlockUnrelatedUsers() {
        throttle.setHashKey(new byte[32]);
        flood(2_000);

        int blocked = 0;
        for (int i = 0; i < 100; i++) {
            if (!throttle.tryAcquire(request("192.168.0." + i), "carol" + i)) {
                blocked++;
            }
        }
        assertTrue("blocked " + blocked, blocked == 0);
    }

    @Test
    public void usernamesSharingABucketInOneRowAreCountedSeparately() {
        throttle.setHashKey(new byte[32]);
        throttle.setBucketsPerRow(8);
        for (int i = 0; i < 2; i++) {
            throttle.tryAcquire(request("10.0.1." + i), "alice");
        }
        int blocked = 0;
        for (int i = 0; i < 8; i++) {
            if (!throttle.tryAcquire(request("10.0.2." + i), "user" + i)) {
                blocked++;
            }
        }
        assertTrue("blocked " + blocked, blocked < 4);
    }

    @Test
    public void clientAndUsernameCountsAreIndependent() {
        throttle.setMaxRequestsPerUsername(1);
        for (int i = 0; i < 3; i++) {
            throttle.tryAcquire(request("alice"), "u" + i);
        }
        assertTrue(throttle.tryAcquire(request("10.0.0.9"), "alice"));
    }

    private void flood(int distinctKeys) {
        for (int i = 0; i < distinctKeys; i++) {
            throttle.tryAcquire(request("172.16." + (i / 250) + "." + (i % 250)), "flood" + i);
        }
    }

    private static HttpServletRequest request(String remoteAddr) {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> "getRemoteAddr".equals(method.getName()) ? remoteAddr : null);
    }
}
