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
import java.util.Arrays;

/**
 * Verifies that {@link ZookeeperDistributedQueue#deserialize(byte[])} is hardened against insecure deserialization
 * (CWE-502): legitimate payloads still round-trip, while classes outside of the configured allow-list are rejected
 * instead of being instantiated.
 *
 * @author Broadleaf Commerce
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private TestableZookeeperDistributedQueue<Serializable> queue;

    @Before
    public void setUp() {
        ZooKeeper zk = EasyMock.createNiceMock(ZooKeeper.class);
        queue = new TestableZookeeperDistributedQueue<>("/test/queue", zk);
    }

    @Test
    public void testWhitelistedCollectionRoundTrips() {
        ArrayList<String> value = new ArrayList<>(Arrays.asList("alpha", "beta"));
        byte[] bytes = queue.serialize(value);
        assertEquals(value, queue.deserialize(bytes));
    }

    @Test
    public void testWhitelistedScalarRoundTrips() {
        byte[] bytes = queue.serialize(Integer.valueOf(42));
        assertEquals(Integer.valueOf(42), queue.deserialize(bytes));
    }

    @Test
    public void testNonWhitelistedClassIsRejected() {
        // java.io.File is Serializable but lives in a package that is not on the allow-list.
        byte[] bytes = queue.serialize(new File("/etc/passwd"));
        try {
            queue.deserialize(bytes);
            fail("Expected deserialization of a non-whitelisted class to be rejected.");
        } catch (DistributedQueueException expected) {
            // expected
        }
    }

    @Test
    public void testIsClassAllowedAcceptsWhitelistedTypes() {
        assertTrue(queue.isClassAllowed("java.lang.String"));
        assertTrue(queue.isClassAllowed("java.util.ArrayList"));
        assertTrue(queue.isClassAllowed("java.time.Instant"));
        assertTrue(queue.isClassAllowed("java.math.BigDecimal"));
        assertTrue(queue.isClassAllowed(
                "org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand"));
        assertTrue(queue.isClassAllowed("org.apache.solr.common.SolrInputDocument"));
    }

    @Test
    public void testIsClassAllowedHandlesArrays() {
        assertTrue(queue.isClassAllowed("[Ljava.lang.String;"));
        assertTrue(queue.isClassAllowed("[[Ljava.util.ArrayList;"));
        assertTrue(queue.isClassAllowed("[I"));
        assertTrue(queue.isClassAllowed("[[D"));
        assertFalse(queue.isClassAllowed("[Ljava.io.File;"));
    }

    @Test
    public void testIsClassAllowedRejectsGadgetAndUnknownTypes() {
        assertFalse(queue.isClassAllowed(null));
        assertFalse(queue.isClassAllowed("java.io.File"));
        assertFalse(queue.isClassAllowed("org.apache.commons.collections.functors.InvokerTransformer"));
        assertFalse(queue.isClassAllowed("org.apache.commons.collections4.functors.InvokerTransformer"));
        assertFalse(queue.isClassAllowed("bsh.XThis"));
    }

    /**
     * Test double that bypasses the Zookeeper interactions performed by the real constructor so that the
     * (de)serialization logic can be exercised in isolation.
     */
    private static class TestableZookeeperDistributedQueue<T extends Serializable> extends ZookeeperDistributedQueue<T> {

        TestableZookeeperDistributedQueue(String queuePath, ZooKeeper zk) {
            super(queuePath, zk);
        }

        @Override
        protected synchronized void intializeQueueFolders() {
            // no-op: avoid contacting Zookeeper
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
            // no-op: avoid contacting Zookeeper
        }

        @Override
        protected void seMaxCapacity(int size) {
            // no-op: avoid contacting Zookeeper
        }
    }
}
