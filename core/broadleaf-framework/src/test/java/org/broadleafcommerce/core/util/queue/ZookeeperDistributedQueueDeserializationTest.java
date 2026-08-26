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

import org.apache.zookeeper.ZooKeeper;
import org.broadleafcommerce.core.util.lock.DistributedLock;
import org.easymock.EasyMock;

import java.io.File;
import java.io.ObjectInputFilter;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import junit.framework.TestCase;

public class ZookeeperDistributedQueueDeserializationTest extends TestCase {

    private TestableZookeeperDistributedQueue queue;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        queue = new TestableZookeeperDistributedQueue(EasyMock.createNiceMock(ZooKeeper.class));
    }

    public void testAllowedTypesRoundTrip() {
        assertEquals("queue entry", queue.deserialize(queue.serialize("queue entry")));
        assertEquals(Integer.valueOf(42), queue.deserialize(queue.serialize(Integer.valueOf(42))));
    }

    public void testDisallowedTypeIsRejected() {
        try {
            queue.deserialize(queue.serialize(new File("/tmp/x")));
            fail("Expected a disallowed type to be rejected");
        } catch (DistributedBlockingQueue.DistributedQueueException expected) {
            // Expected.
        }
    }

    public void testTypeRestrictedFilter() {
        ObjectInputFilter filter = queue.createTypeRestrictedDeserializationFilter(Integer.class);

        assertEquals(Integer.valueOf(42), queue.deserialize(queue.serialize(Integer.valueOf(42)), filter));

        try {
            queue.deserialize(queue.serialize("not an Integer"), filter);
            fail("Expected the type-restricted filter to reject String");
        } catch (DistributedBlockingQueue.DistributedQueueException expected) {
            // Expected.
        }
    }

    public void testReflectiveTypesAreRejected() {
        assertFalse(queue.isDeserializationAllowed(java.lang.reflect.Proxy.class));
        assertTrue(queue.isDeserializationAllowed(String.class));
    }

    private static class TestableZookeeperDistributedQueue extends ZookeeperDistributedQueue<Serializable> {

        private TestableZookeeperDistributedQueue(ZooKeeper zk) {
            super("/deserialization-test", zk);
        }

        @Override
        protected void intializeQueueFolders() {
            // No-op for serialization tests.
        }

        @Override
        protected DistributedLock initializeQueueAccessLock() {
            return new NoOpDistributedLock();
        }

        @Override
        protected DistributedLock initializeConfigLock() {
            return new NoOpDistributedLock();
        }

        @Override
        protected void determineMaxCapacity() {
            // No-op for serialization tests.
        }
    }

    private static class NoOpDistributedLock implements DistributedLock {

        @Override
        public void lock() {
        }

        @Override
        public void lockInterruptibly() {
        }

        @Override
        public boolean tryLock() {
            return true;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) {
            return true;
        }

        @Override
        public void unlock() {
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean canParticipate() {
            return true;
        }

        @Override
        public boolean currentThreadHoldsLock() {
            return true;
        }
    }
}
