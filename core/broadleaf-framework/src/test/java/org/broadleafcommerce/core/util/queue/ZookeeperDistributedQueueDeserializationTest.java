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
import org.broadleafcommerce.core.search.service.solr.indexer.CatalogReindexCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand;
import org.broadleafcommerce.core.util.lock.DistributedLock;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.InvalidClassException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that {@link ZookeeperDistributedQueue#deserialize(byte[])} guards against insecure deserialization (CWE-502).
 * Legitimate queue payloads must round-trip, while classes outside the allowlist must be rejected before they are
 * instantiated.
 *
 * @author Devin
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private TestableZookeeperDistributedQueue queue;

    @Before
    public void setUp() {
        ZooKeeper zk = EasyMock.createNiceMock(ZooKeeper.class);
        queue = new TestableZookeeperDistributedQueue(zk);
    }

    @Test
    public void testAllowedIntegerRoundTrips() {
        Integer original = 42;
        byte[] bytes = queue.serialize(original);
        assertEquals(original, queue.deserialize(bytes));
    }

    @Test
    public void testAllowedSolrUpdateCommandRoundTrips() {
        CatalogReindexCommand original = new CatalogReindexCommand(123L);
        byte[] bytes = queue.serialize(original);
        Object result = queue.deserialize(bytes);
        assertTrue(result instanceof CatalogReindexCommand);
        assertEquals(Long.valueOf(123L), ((CatalogReindexCommand) result).getCatalogId());
    }

    @Test
    public void testAllowedIncrementalUpdateCommandRoundTrips() {
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "product-1");
        List<SolrInputDocument> docs = new ArrayList<>();
        docs.add(doc);
        List<String> deleteQueries = new ArrayList<>();
        deleteQueries.add("id:stale");

        IncrementalUpdateCommand original = new IncrementalUpdateCommand(docs, deleteQueries);
        byte[] bytes = queue.serialize(original);
        Object result = queue.deserialize(bytes);

        assertTrue(result instanceof IncrementalUpdateCommand);
        IncrementalUpdateCommand deserialized = (IncrementalUpdateCommand) result;
        assertEquals(1, deserialized.getSolrInputDocuments().size());
        assertEquals("product-1", deserialized.getSolrInputDocuments().get(0).getFieldValue("id"));
        assertEquals("id:stale", deserialized.getDeleteQueries().get(0));
    }

    @Test
    public void testDisallowedClassIsRejected() {
        //java.io.File is Serializable but lives outside the deserialization allowlist and must be rejected.
        byte[] bytes = queue.serialize(new File("/tmp/should-not-deserialize"));
        try {
            queue.deserialize(bytes);
            fail("Expected deserialization of a disallowed class to be rejected.");
        } catch (DistributedBlockingQueue.DistributedQueueException e) {
            assertTrue("Rejection should be caused by the object input filter (InvalidClassException).",
                    hasCause(e, InvalidClassException.class));
        }
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> causeType) {
        Throwable current = t;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Test subclass that bypasses the Zookeeper interactions performed in the constructor so that the
     * serialization/deserialization logic can be exercised in isolation.
     */
    private static class TestableZookeeperDistributedQueue extends ZookeeperDistributedQueue<Serializable> {

        TestableZookeeperDistributedQueue(ZooKeeper zk) {
            super("/deserialization-test-queue", zk, DEFAULT_MAX_QUEUE_SIZE, true, null);
        }

        @Override
        protected synchronized void intializeQueueFolders() {
            //no-op for tests; avoids talking to Zookeeper
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
        protected void seMaxCapacity(int size) {
            //no-op for tests; avoids talking to Zookeeper
        }

        @Override
        protected synchronized void determineMaxCapacity() {
            //no-op for tests; avoids talking to Zookeeper
        }
    }
}
