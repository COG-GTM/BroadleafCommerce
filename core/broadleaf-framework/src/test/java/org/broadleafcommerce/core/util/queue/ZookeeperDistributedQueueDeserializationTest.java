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

import org.apache.solr.common.SolrInputDocument;
import org.apache.zookeeper.ZooKeeper;
import org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand;
import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;
import org.broadleafcommerce.core.util.lock.DistributedLock;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import com.example.security.UnexpectedSerializableType;

import java.io.InvalidClassException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Verifies that {@link ZookeeperDistributedQueue#deserialize(byte[])} is guarded by an allow-list (CWE-502 mitigation):
 * the types the queue actually stores round-trip successfully, while any other serialized class is rejected before it is
 * instantiated.
 *
 * @author Devin
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private TestQueue queue;

    @Before
    public void setUp() {
        queue = new TestQueue();
    }

    @Test
    public void testIntegerConfigValueRoundTrips() {
        // The queue stores the max-capacity config as an Integer.
        Object result = queue.deserialize(queue.serialize(42));
        assertEquals(Integer.valueOf(42), result);
    }

    @Test
    public void testQueueCommandRoundTrips() {
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "1");

        List<SolrInputDocument> docs = new ArrayList<>();
        docs.add(doc);

        IncrementalUpdateCommand command =
                new IncrementalUpdateCommand(docs, Collections.singletonList("deleteQuery"));

        Object result = queue.deserialize(queue.serialize(command));

        assertNotNull(result);
        assertTrue(result instanceof IncrementalUpdateCommand);
        IncrementalUpdateCommand deserialized = (IncrementalUpdateCommand) result;
        assertEquals(1, deserialized.getSolrInputDocuments().size());
        assertEquals(Collections.singletonList("deleteQuery"), deserialized.getDeleteQueries());
    }

    @Test
    public void testUnexpectedTypeIsRejected() {
        byte[] bytes = queue.serialize(new UnexpectedSerializableType("payload"));

        try {
            queue.deserialize(bytes);
            fail("Expected deserialization of a non-allow-listed type to be rejected.");
        } catch (DistributedQueueException e) {
            assertTrue(
                    "Rejection should be caused by the ObjectInputFilter (InvalidClassException), but was: " + e.getCause(),
                    e.getCause() instanceof InvalidClassException);
        }
    }

    /**
     * Test double that exercises the real {@link ZookeeperDistributedQueue#serialize(Serializable)} /
     * {@link ZookeeperDistributedQueue#deserialize(byte[])} logic without requiring a live Zookeeper connection. The
     * Zookeeper-backed initialization performed by the super constructor is overridden away.
     */
    private static class TestQueue extends ZookeeperDistributedQueue<Serializable> {

        TestQueue() {
            super("/test/deserialization-queue", mockZookeeper(), 1, false, null);
        }

        private static ZooKeeper mockZookeeper() {
            return EasyMock.createNiceMock(ZooKeeper.class);
        }

        private static DistributedLock mockLock() {
            return EasyMock.createNiceMock(DistributedLock.class);
        }

        @Override
        protected void intializeQueueFolders() {
            // no-op: avoid touching Zookeeper during construction
        }

        @Override
        protected DistributedLock initializeQueueAccessLock() {
            return mockLock();
        }

        @Override
        protected DistributedLock initializeConfigLock() {
            return mockLock();
        }

        @Override
        protected void determineMaxCapacity() {
            // no-op: avoid touching Zookeeper during construction
        }
    }
}
