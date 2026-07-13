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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.zookeeper.ZooKeeper;
import org.broadleafcommerce.core.util.lock.DistributedLock;
import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Verifies that {@link ZookeeperDistributedQueue#deserialize(byte[])} is hardened against insecure deserialization
 * (CWE-502): only allow-listed classes may be reconstructed from the untrusted bytes that are read from Zookeeper.
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private TestZookeeperDistributedQueue queue;

    @Before
    public void setUp() {
        queue = new TestZookeeperDistributedQueue();
    }

    @Test
    public void testSafeJdkTypesRoundTrip() {
        assertEquals("hello", queue.deserialize(queue.serialize("hello")));
        assertEquals(Integer.valueOf(42), queue.deserialize(queue.serialize(Integer.valueOf(42))));

        ArrayList<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        assertEquals(list, queue.deserialize(queue.serialize(list)));

        Map<String, Integer> map = new HashMap<>();
        map.put("one", 1);
        assertEquals(map, queue.deserialize(queue.serialize((Serializable) map)));
    }

    @Test
    public void testDisallowedClassIsRejected() {
        // java.io.File is Serializable but is not part of the deserialization allow-list. It stands in for any
        // arbitrary (potentially gadget-chain) class an attacker could plant in Zookeeper.
        byte[] payload = queue.serialize(new File("/etc/passwd"));
        try {
            queue.deserialize(payload);
            fail("Expected deserialization of a disallowed class (java.io.File) to be rejected.");
        } catch (DistributedQueueException expected) {
            // The ObjectInputFilter rejected the class before it could be instantiated.
        }
    }

    @Test
    public void testAllowListChecks() {
        assertTrue(queue.isClassAllowedForDeserialization("java.lang.Integer"));
        assertTrue(queue.isClassAllowedForDeserialization("java.util.ArrayList"));
        assertTrue(queue.isClassAllowedForDeserialization("java.time.Instant"));
        assertTrue(queue.isClassAllowedForDeserialization("java.math.BigDecimal"));
        assertTrue(queue.isClassAllowedForDeserialization("org.broadleafcommerce.core.catalog.domain.ProductImpl"));

        assertFalse(queue.isClassAllowedForDeserialization("java.io.File"));
        assertFalse(queue.isClassAllowedForDeserialization("org.apache.commons.collections.functors.InvokerTransformer"));
        assertFalse(queue.isClassAllowedForDeserialization("com.evil.Gadget"));
    }

    /**
     * A test double that skips all Zookeeper interaction so the (de)serialization logic can be exercised in isolation.
     */
    private static class TestZookeeperDistributedQueue extends ZookeeperDistributedQueue<Serializable> {

        TestZookeeperDistributedQueue() {
            super("/deserialization-test", EasyMock.createNiceMock(ZooKeeper.class), 500, false, null);
        }

        @Override
        protected synchronized void intializeQueueFolders() {
            // no-op: avoid Zookeeper I/O during construction
        }

        @Override
        protected DistributedLock initializeQueueAccessLock() {
            return EasyMock.createNiceMock(DistributedLock.class);
        }

        @Override
        protected DistributedLock initializeConfigLock() {
            return EasyMock.createNiceMock(DistributedLock.class);
        }

        @Override
        protected synchronized void determineMaxCapacity() {
            // no-op: avoid Zookeeper I/O during construction
        }

        @Override
        protected void seMaxCapacity(int size) {
            // no-op: avoid Zookeeper I/O during construction
        }
    }
}
