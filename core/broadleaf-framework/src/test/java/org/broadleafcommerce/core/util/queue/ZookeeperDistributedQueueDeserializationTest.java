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

import com.broadleafcommerce.test.security.MaliciousPayload;

import org.apache.solr.common.SolrInputDocument;
import org.apache.zookeeper.ZooKeeper;
import org.broadleafcommerce.core.util.lock.DistributedLock;
import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;
import org.easymock.classextension.EasyMock;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that {@link ZookeeperDistributedQueue#deserialize(byte[])} guards against deserialization of untrusted data
 * (CWE-502). Legitimate payloads (JDK types, Broadleaf types, and Solr command payloads) must round-trip, while classes
 * outside the allow-list must be rejected before {@code readObject()} can instantiate them.
 *
 * @author Devin
 */
public class ZookeeperDistributedQueueDeserializationTest {

    /**
     * A {@link ZookeeperDistributedQueue} whose constructor is prevented from touching a live Zookeeper so that the
     * serialization/deserialization logic can be exercised in isolation.
     */
    private static class TestableZookeeperDistributedQueue extends ZookeeperDistributedQueue<Serializable> {

        TestableZookeeperDistributedQueue() {
            super("/test-queue", EasyMock.createNiceMock(ZooKeeper.class), 500, true, null);
        }

        @Override
        protected void intializeQueueFolders() {
            // no-op: avoid contacting Zookeeper during construction
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
        protected void determineMaxCapacity() {
            // no-op: avoid contacting Zookeeper during construction
        }

        Object roundTrip(Serializable value) {
            return deserialize(serialize(value));
        }

        Object deserializeRaw(byte[] bytes) {
            return deserialize(bytes);
        }
    }

    private static byte[] rawSerialize(Serializable value) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(value);
        }
        return baos.toByteArray();
    }

    @Test
    public void testAllowedJdkTypesRoundTrip() {
        TestableZookeeperDistributedQueue queue = new TestableZookeeperDistributedQueue();

        assertEquals("hello", queue.roundTrip("hello"));
        assertEquals(Integer.valueOf(42), queue.roundTrip(42));

        ArrayList<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        assertEquals(list, queue.roundTrip(list));
    }

    @Test
    public void testAllowedSolrPayloadRoundTrips() {
        TestableZookeeperDistributedQueue queue = new TestableZookeeperDistributedQueue();

        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "123");
        doc.addField("name", "Test Product");

        Object result = queue.roundTrip(doc);
        assertNotNull(result);
        assertTrue(result instanceof SolrInputDocument);
        assertEquals("123", ((SolrInputDocument) result).getFieldValue("id"));
    }

    @Test
    public void testDisallowedClassIsRejected() throws Exception {
        TestableZookeeperDistributedQueue queue = new TestableZookeeperDistributedQueue();

        byte[] malicious = rawSerialize(new MaliciousPayload("rm -rf /"));

        try {
            queue.deserializeRaw(malicious);
            fail("Expected deserialization of a non-allow-listed class to be rejected");
        } catch (DistributedQueueException e) {
            assertTrue("Rejection should be caused by an InvalidClassException from the ObjectInputFilter",
                    e.getCause() instanceof InvalidClassException);
        }
    }

    @Test
    public void testCustomFilterCanExtendAllowList() {
        TestableZookeeperDistributedQueue queue = new TestableZookeeperDistributedQueue();

        // Allow the otherwise-rejected payload package explicitly and confirm it now round-trips.
        queue.setDeserializationFilter(
                java.io.ObjectInputFilter.Config.createFilter("java.**;com.broadleafcommerce.test.security.**;!*"));

        Object result = queue.roundTrip(new MaliciousPayload("harmless-when-trusted"));
        assertTrue(result instanceof MaliciousPayload);
        assertEquals("harmless-when-trusted", ((MaliciousPayload) result).getCommand());
    }

    @Test
    public void testNestedDisallowedClassInsideAllowedCollectionIsRejected() throws Exception {
        TestableZookeeperDistributedQueue queue = new TestableZookeeperDistributedQueue();

        List<Serializable> payload = new ArrayList<>();
        payload.add("safe");
        payload.add(new MaliciousPayload("nested"));

        try {
            queue.deserializeRaw(rawSerialize((Serializable) payload));
            fail("Expected a disallowed class nested inside an allowed collection to be rejected");
        } catch (DistributedQueueException e) {
            assertTrue(e.getCause() instanceof InvalidClassException);
        }
    }
}
