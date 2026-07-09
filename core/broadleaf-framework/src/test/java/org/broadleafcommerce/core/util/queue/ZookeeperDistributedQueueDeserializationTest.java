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

import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * Verifies that {@link ZookeeperDistributedQueue} guards deserialization of queue entries with an allowlist
 * {@link ObjectInputFilter} so that classes outside the expected set cannot be instantiated from Zookeeper data
 * (CWE-502, insecure deserialization).
 *
 * @author Devin
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private static byte[] serialize(Serializable obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    private ObjectInputFilter defaultFilter() {
        return ObjectInputFilter.Config.createFilter(ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_ALLOWLIST);
    }

    @Test
    public void testAllowlistPermitsExpectedQueueTypes() throws IOException {
        // Integer is used for the queue's maxCapacity config value.
        assertEquals(Integer.valueOf(42), ZookeeperDistributedQueue.deserialize(serialize(42), defaultFilter()));

        // Strings and common collections are legitimate queue payloads.
        assertEquals("payload", ZookeeperDistributedQueue.deserialize(serialize("payload"), defaultFilter()));

        ArrayList<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        assertEquals(list, ZookeeperDistributedQueue.deserialize(serialize(list), defaultFilter()));
    }

    @Test
    public void testAllowlistRejectsDisallowedClass() throws IOException {
        // java.io.File is Serializable but is not on the allowlist; it stands in for an unexpected/malicious class.
        byte[] bytes = serialize(new File("/etc/passwd"));
        try {
            ZookeeperDistributedQueue.deserialize(bytes, defaultFilter());
            fail("Expected deserialization of a disallowed class to be rejected.");
        } catch (DistributedQueueException e) {
            assertTrue("Rejection should be caused by the ObjectInputFilter (InvalidClassException).",
                    e.getCause() instanceof InvalidClassException);
        }
    }

    @Test
    public void testDisallowedClassDeserializesWithoutFilter() throws IOException {
        // Sanity check: without the filter the same payload deserializes, proving the filter is what blocks it.
        byte[] bytes = serialize(new File("/etc/passwd"));
        Object result = ZookeeperDistributedQueue.deserialize(bytes, null);
        assertTrue(result instanceof File);
    }

    @Test
    public void testCustomAllowlistIsEnforced() throws IOException {
        // A narrow allowlist that only permits Integer (and its Number superclass descriptor) must reject other types.
        ObjectInputFilter integersOnly =
                ObjectInputFilter.Config.createFilter("java.lang.Integer;java.lang.Number;!*");
        assertEquals(Integer.valueOf(7), ZookeeperDistributedQueue.deserialize(serialize(7), integersOnly));

        try {
            ZookeeperDistributedQueue.deserialize(serialize(new File("/tmp/x")), integersOnly);
            fail("Expected a class outside the custom allowlist to be rejected.");
        } catch (DistributedQueueException e) {
            assertTrue(e.getCause() instanceof InvalidClassException);
        }
    }
}
