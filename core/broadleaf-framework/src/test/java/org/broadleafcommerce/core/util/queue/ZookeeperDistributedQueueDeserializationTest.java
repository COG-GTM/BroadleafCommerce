/*-
 * #%L
 * BroadleafCommerce Framework
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
package org.broadleafcommerce.core.util.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies that {@link ZookeeperDistributedQueue#deserialize(byte[], ObjectInputFilter)} mitigates insecure
 * deserialization (CWE-502) by enforcing an {@link ObjectInputFilter} allowlist: allowlisted JDK/Broadleaf types
 * round-trip correctly, while classes that are not on the allowlist are rejected before they can be instantiated.
 *
 * @author Devin
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private static ObjectInputFilter defaultFilter() {
        return ObjectInputFilter.Config.createFilter(ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_ALLOWLIST);
    }

    private static byte[] toBytes(Serializable obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    @Test
    public void testAllowlistEndsWithRejectAll() {
        assertTrue("The allowlist must reject everything not explicitly listed.",
                ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_ALLOWLIST.trim().endsWith("!*"));
    }

    @Test
    public void testAllowlistedScalarRoundTrips() throws Exception {
        Object result = ZookeeperDistributedQueue.deserialize(toBytes("a-queue-entry"), defaultFilter());
        assertEquals("a-queue-entry", result);
    }

    @Test
    public void testAllowlistedCollectionGraphRoundTrips() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "order-123");
        payload.put("quantity", 42);
        List<String> skus = new ArrayList<>();
        skus.add("SKU-1");
        skus.add("SKU-2");
        payload.put("skus", skus);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) ZookeeperDistributedQueue.deserialize(toBytes((Serializable) payload), defaultFilter());

        assertEquals("order-123", result.get("name"));
        assertEquals(42, result.get("quantity"));
        assertEquals(skus, result.get("skus"));
    }

    @Test
    public void testNonAllowlistedClassIsRejected() throws Exception {
        // java.io.File is Serializable but is NOT on the allowlist; it stands in for any attacker-supplied
        // gadget class. Deserialization must fail rather than instantiate it.
        byte[] bytes = toBytes(new File("/etc/passwd"));
        try {
            ZookeeperDistributedQueue.deserialize(bytes, defaultFilter());
            fail("Expected deserialization of a non-allowlisted class to be rejected.");
        } catch (DistributedQueueException e) {
            assertTrue("Rejection should be caused by the ObjectInputFilter (InvalidClassException), but was: " + e.getCause(),
                    e.getCause() instanceof InvalidClassException);
        }
    }

    @Test
    public void testNonAllowlistedClassIsAllowedWhenFilterDisabled() throws Exception {
        // Sanity check: without the filter the same payload deserializes successfully, proving the filter (not some
        // unrelated failure) is what blocks the class above.
        Object result = ZookeeperDistributedQueue.deserialize(toBytes(new File("/etc/passwd")), null);
        assertEquals(new File("/etc/passwd"), result);
    }
}
