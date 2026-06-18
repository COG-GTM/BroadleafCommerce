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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.attack.MaliciousPayload;

import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.io.InvalidClassException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that {@link ZookeeperDistributedQueue#deserialize(byte[])} is hardened against insecure
 * deserialization (CWE-502): only allowlisted classes are deserialized and any other class (e.g. a
 * gadget chain read from Zookeeper) is rejected before its {@code readObject} can run.
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private ZookeeperDistributedQueue<Serializable> queue;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        // Bypass the Zookeeper-dependent constructor via Objenesis while still invoking the real
        // serialize()/deserialize()/filter accessor implementations.
        queue = EasyMock.partialMockBuilder(ZookeeperDistributedQueue.class).createMock();
        EasyMock.replay(queue);
        MaliciousPayload.executed = false;
    }

    @Test
    public void defaultFilterIsInstalled() {
        assertNotNull("A deserialization filter must be installed by default.", queue.getDeserializationFilter());
    }

    @Test
    public void allowlistedTypesRoundTrip() {
        assertEquals("hello", queue.deserialize(queue.serialize("hello")));
        assertEquals(Integer.valueOf(42), queue.deserialize(queue.serialize(42)));

        ArrayList<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        @SuppressWarnings("unchecked")
        List<Integer> roundTripped = (List<Integer>) queue.deserialize(queue.serialize(list));
        assertEquals(list, roundTripped);
    }

    @Test
    public void disallowedClassIsRejectedAndNotExecuted() {
        byte[] payload = queue.serialize(new MaliciousPayload());

        try {
            queue.deserialize(payload);
            fail("Deserialization of a non-allowlisted class must be rejected.");
        } catch (DistributedQueueException e) {
            assertTrue("Expected the rejection to be caused by an InvalidClassException, but was: " + e.getCause(),
                    e.getCause() instanceof InvalidClassException);
        }

        assertFalse("The malicious payload's readObject must never execute.", MaliciousPayload.executed);
    }

    @Test
    public void filterCanBeOverriddenWithCustomPattern() {
        queue.setDeserializationFilterPattern("com.example.attack.*;!*");
        byte[] payload = queue.serialize(new MaliciousPayload());

        Object result = queue.deserialize(payload);

        assertTrue(result instanceof MaliciousPayload);
        assertTrue("An explicitly allowlisted class is permitted to deserialize.", MaliciousPayload.executed);
    }
}
