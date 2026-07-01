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

import static org.easymock.EasyMock.createNiceMock;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.zookeeper.ZooKeeper;
import org.broadleafcommerce.core.util.lock.DistributedLock;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Verifies that {@link ZookeeperDistributedQueue#deserialize(byte[])} is hardened against insecure deserialization
 * (CWE-502): legitimate, allowlisted payloads round-trip correctly, while classes that are not on the allowlist
 * (and dynamic proxies) are rejected instead of being instantiated.
 *
 * @author Broadleaf Commerce
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private TestableZookeeperDistributedQueue queue;

    @Before
    public void setup() {
        queue = new TestableZookeeperDistributedQueue();
    }

    @Test
    public void testAllowedScalarRoundTrip() {
        assertEquals(Integer.valueOf(42), queue.deserialize(queue.serialize(42)));
        assertEquals("hello", queue.deserialize(queue.serialize("hello")));
        assertEquals(Long.valueOf(7L), queue.deserialize(queue.serialize(7L)));
        assertEquals(Boolean.TRUE, queue.deserialize(queue.serialize(Boolean.TRUE)));
    }

    @Test
    public void testAllowedCollectionRoundTrip() {
        ArrayList<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        Object result = queue.deserialize(queue.serialize(list));
        assertEquals(list, result);

        HashMap<String, Integer> map = new HashMap<>();
        map.put("x", 1);
        assertEquals(map, queue.deserialize(queue.serialize(map)));
    }

    @Test
    public void testAllowedArrayRoundTrip() {
        String[] array = new String[] {"a", "b", "c"};
        Object result = queue.deserialize(queue.serialize(array));
        assertArrayEquals(array, (String[]) result);
    }

    @Test
    public void testDisallowedClassIsRejected() {
        // java.io.File is Serializable but is not on the default allowlist; it stands in for an
        // attacker-controlled "gadget" payload.
        byte[] payload = serializeRaw(new File("/tmp/should-not-deserialize"));
        try {
            queue.deserialize(payload);
            fail("Expected deserialization of a non-allowlisted class to be rejected.");
        } catch (DistributedBlockingQueue.DistributedQueueException e) {
            assertTrue("Expected the cause to be an InvalidClassException but was: " + e.getCause(),
                    e.getCause() instanceof InvalidClassException);
        }
    }

    @Test
    public void testDisallowedClassCanBeAllowlisted() {
        queue.getAllowedDeserializationClassNames().add(File.class.getName());
        File file = new File("/tmp/allowed");
        Object result = queue.deserialize(serializeRaw(file));
        assertEquals(file, result);
    }

    @Test
    public void testClassAllowlistMatching() {
        assertTrue(queue.isDeserializationClassAllowed("java.lang.Integer"));
        assertTrue(queue.isDeserializationClassAllowed("java.util.ArrayList"));
        assertTrue(queue.isDeserializationClassAllowed("org.broadleafcommerce.core.Foo"));
        // Array encodings normalize to their base component type.
        assertTrue(queue.isDeserializationClassAllowed("[Ljava.lang.String;"));
        assertTrue(queue.isDeserializationClassAllowed("[[I"));
        // Well-known gadget classes must not be permitted.
        assertTrue(!queue.isDeserializationClassAllowed("org.apache.commons.collections.functors.InvokerTransformer"));
        assertTrue(!queue.isDeserializationClassAllowed("java.io.File"));
    }

    private static byte[] serializeRaw(Serializable obj) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Test-only subclass that bypasses the Zookeeper interactions performed in the constructor so that the
     * (de)serialization logic can be exercised in isolation.
     */
    private static class TestableZookeeperDistributedQueue extends ZookeeperDistributedQueue<Serializable> {

        TestableZookeeperDistributedQueue() {
            super("/test-queue", createNiceMock(ZooKeeper.class), DEFAULT_MAX_QUEUE_SIZE, true, null);
        }

        @Override
        protected synchronized void intializeQueueFolders() {
            // no-op: avoid contacting Zookeeper during tests
        }

        @Override
        protected synchronized void determineMaxCapacity() {
            // no-op: avoid contacting Zookeeper during tests
        }

        @Override
        protected DistributedLock initializeQueueAccessLock() {
            return createNiceMock(DistributedLock.class);
        }

        @Override
        protected DistributedLock initializeConfigLock() {
            return createNiceMock(DistributedLock.class);
        }
    }
}
