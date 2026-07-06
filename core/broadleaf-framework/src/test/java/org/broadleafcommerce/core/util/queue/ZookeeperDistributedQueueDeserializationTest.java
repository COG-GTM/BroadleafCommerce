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

import org.apache.solr.common.SolrInputDocument;
import org.apache.zookeeper.ZooKeeper;
import org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand;
import org.broadleafcommerce.core.util.lock.DistributedLock;
import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.InvalidClassException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that {@link ZookeeperDistributedQueue#deserialize(byte[])} only reconstructs the types that
 * legitimately flow through the queue and rejects arbitrary classes, mitigating insecure deserialization
 * (CWE-502). The queue is instantiated through a test subclass that stubs out all Zookeeper interactions so
 * that the serialization/deserialization logic can be exercised in isolation.
 *
 * @author Devin
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private TestableZookeeperDistributedQueue<Serializable> queue;

    @Before
    public void setup() {
        ZooKeeper zk = EasyMock.createNiceMock(ZooKeeper.class);
        EasyMock.replay(zk);
        queue = new TestableZookeeperDistributedQueue<>("/test/deserialization-queue", zk);
    }

    @Test
    public void testIntegerRoundTrips() {
        // Integers are used internally for the queue's maxCapacity config node.
        Object result = queue.deserialize(queue.serialize(42));
        assertTrue(result instanceof Integer);
        assertEquals(Integer.valueOf(42), result);
    }

    @Test
    public void testStringAndCollectionRoundTrip() {
        ArrayList<String> list = new ArrayList<>();
        list.add("alpha");
        list.add("beta");

        Object result = queue.deserialize(queue.serialize(list));
        assertTrue(result instanceof ArrayList);
        assertEquals(list, result);
    }

    @Test
    public void testSolrUpdateCommandRoundTrips() {
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "product:1");
        List<SolrInputDocument> docs = new ArrayList<>();
        docs.add(doc);
        List<String> deleteQueries = new ArrayList<>();
        deleteQueries.add("id:product:2");

        IncrementalUpdateCommand command = new IncrementalUpdateCommand(docs, deleteQueries);

        Object result = queue.deserialize(queue.serialize(command));
        assertTrue(result instanceof IncrementalUpdateCommand);
        IncrementalUpdateCommand roundTripped = (IncrementalUpdateCommand) result;
        assertEquals(1, roundTripped.getSolrInputDocuments().size());
        assertEquals("product:1", roundTripped.getSolrInputDocuments().get(0).getFieldValue("id"));
        assertEquals(deleteQueries, roundTripped.getDeleteQueries());
    }

    @Test
    public void testDisallowedTypeIsRejected() {
        // java.io.File is Serializable but is NOT on the allowlist and must be rejected.
        byte[] payload = queue.serialize(new File("/etc/passwd"));
        assertRejected(payload);
    }

    @Test
    public void testDisallowedTypeNestedInAllowedContainerIsRejected() {
        // A disallowed class hidden inside an allowed collection must still be rejected, because the
        // ObjectInputFilter is consulted for every class encountered during the object graph traversal.
        ArrayList<Serializable> list = new ArrayList<>();
        list.add("safe");
        list.add(new File("/etc/passwd"));

        byte[] payload = queue.serialize(list);
        assertRejected(payload);
    }

    private void assertRejected(byte[] payload) {
        try {
            queue.deserialize(payload);
            fail("Expected deserialization of a disallowed class to be rejected.");
        } catch (DistributedQueueException e) {
            Throwable cause = e.getCause();
            assertTrue("Expected the underlying cause to be an InvalidClassException but was " + cause,
                    cause instanceof InvalidClassException);
        }
    }

    /**
     * Test-only subclass that bypasses all Zookeeper interactions so that {@link #serialize(Serializable)} and
     * {@link #deserialize(byte[])} can be tested without a live Zookeeper ensemble.
     */
    private static class TestableZookeeperDistributedQueue<T extends Serializable> extends ZookeeperDistributedQueue<T> {

        TestableZookeeperDistributedQueue(String queuePath, ZooKeeper zk) {
            super(queuePath, zk, DEFAULT_MAX_QUEUE_SIZE, false, null);
        }

        @Override
        protected synchronized void intializeQueueFolders() {
            // no-op: do not touch Zookeeper
        }

        @Override
        protected synchronized void determineMaxCapacity() {
            // no-op: do not touch Zookeeper
        }

        @Override
        protected DistributedLock initializeQueueAccessLock() {
            return newLockMock();
        }

        @Override
        protected DistributedLock initializeConfigLock() {
            return newLockMock();
        }

        private DistributedLock newLockMock() {
            DistributedLock lock = EasyMock.createNiceMock(DistributedLock.class);
            EasyMock.replay(lock);
            return lock;
        }
    }
}
