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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.security.MaliciousSerializablePayload;

import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;
import org.easymock.EasyMock;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Verifies that {@link ZookeeperDistributedQueue#deserialize(byte[])} applies a restrictive
 * {@link java.io.ObjectInputFilter} allow-list so that untrusted data stored in Zookeeper cannot be leveraged to
 * instantiate arbitrary classes (insecure deserialization / CWE-502).
 */
public class ZookeeperDistributedQueueDeserializationTest {

    /**
     * Builds an instance without invoking the (Zookeeper-dependent) constructor. EasyMock uses Objenesis to
     * instantiate the class, and every method other than the explicitly mocked one runs its real implementation,
     * so {@code serialize}/{@code deserialize}/{@code getDeserializationFilter} are exercised for real.
     */
    private ZookeeperDistributedQueue<Serializable> newQueue() {
        ZookeeperDistributedQueue<Serializable> queue = EasyMock.partialMockBuilder(ZookeeperDistributedQueue.class)
                .addMockedMethod("getZookeeperClient")
                .createMock();
        EasyMock.replay(queue);
        return queue;
    }

    private static byte[] javaSerialize(Serializable obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    @Test
    public void testAllowedJdkCollectionRoundTrips() {
        ZookeeperDistributedQueue<Serializable> queue = newQueue();

        ArrayList<String> payload = new ArrayList<>();
        payload.add("alpha");
        payload.add("beta");

        byte[] bytes = queue.serialize(payload);
        Object result = queue.deserialize(bytes);

        assertEquals(payload, result);
    }

    @Test
    public void testAllowedNestedJdkTypesRoundTrip() {
        ZookeeperDistributedQueue<Serializable> queue = newQueue();

        HashMap<String, Integer> payload = new HashMap<>();
        payload.put("count", 42);

        byte[] bytes = queue.serialize(payload);

        @SuppressWarnings("unchecked")
        Map<String, Integer> result = (Map<String, Integer>) queue.deserialize(bytes);

        assertNotNull(result);
        assertEquals(Integer.valueOf(42), result.get("count"));
    }

    @Test
    public void testDisallowedClassIsRejected() throws Exception {
        ZookeeperDistributedQueue<Serializable> queue = newQueue();

        byte[] malicious = javaSerialize(new MaliciousSerializablePayload("payload"));

        try {
            queue.deserialize(malicious);
            fail("Expected deserialization of a class outside the allow-list to be rejected.");
        } catch (DistributedQueueException e) {
            assertTrue("Expected the underlying cause to be an InvalidClassException, but was: " + e.getCause(),
                    e.getCause() instanceof InvalidClassException);
        }
    }

    @Test
    public void testAdditionalAllowedClassesViaSystemProperty() throws Exception {
        ZookeeperDistributedQueue<Serializable> queue = newQueue();

        byte[] payload = javaSerialize(new MaliciousSerializablePayload("payload"));

        System.setProperty(ZookeeperDistributedQueue.ADDITIONAL_ALLOWED_CLASSES_PROPERTY, "com.example.security.*");
        try {
            Object result = queue.deserialize(payload);
            assertTrue(result instanceof MaliciousSerializablePayload);
            assertEquals("payload", ((MaliciousSerializablePayload) result).getValue());
        } finally {
            System.clearProperty(ZookeeperDistributedQueue.ADDITIONAL_ALLOWED_CLASSES_PROPERTY);
        }
    }
}
