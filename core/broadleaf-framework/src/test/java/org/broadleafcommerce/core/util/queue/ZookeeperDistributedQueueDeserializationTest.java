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

import org.broadleafcommerce.core.search.service.solr.indexer.FullReindexCommand;
import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;
import org.junit.Test;

import javax.management.BadAttributeValueExpException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Verifies that {@link ZookeeperDistributedQueue#deserialize(byte[], java.io.ObjectInputFilter)} enforces the
 * allowlist that mitigates insecure deserialization (CWE-502): expected queue payload types round-trip, while any
 * class outside the allowlist is rejected before it is instantiated.
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private static byte[] serialize(Serializable obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    private static Object deserialize(Serializable obj) throws IOException {
        return ZookeeperDistributedQueue.deserialize(serialize(obj),
                ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER);
    }

    @Test
    public void testAllowsBoxedIntegerCapacityValue() throws IOException {
        // The configs/maxCapacity node stores a serialized Integer.
        assertEquals(Integer.valueOf(500), deserialize(500));
    }

    @Test
    public void testAllowsStringAndCollectionPayloads() throws IOException {
        ArrayList<String> value = new ArrayList<>(Arrays.asList("a", "b", "c"));
        assertEquals(value, deserialize(value));
    }

    @Test
    public void testAllowsBroadleafQueueCommand() throws IOException {
        Object result = deserialize(new FullReindexCommand());
        assertTrue(result instanceof FullReindexCommand);
    }

    @Test
    public void testRejectsClassOutsideAllowlist() throws IOException {
        // java.io.File is Serializable but is not part of the allowlist; it stands in for an attacker-controlled
        // gadget class. Deserialization must be refused before the object is constructed.
        byte[] bytes = serialize(new File("/tmp/attacker"));
        try {
            ZookeeperDistributedQueue.deserialize(bytes, ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER);
            fail("Expected deserialization of a disallowed class to be refused.");
        } catch (DistributedQueueException e) {
            assertTrue("Rejection should be caused by an InvalidClassException from the ObjectInputFilter.",
                    e.getCause() instanceof InvalidClassException);
        }
    }

    @Test
    public void testRejectsKnownGadgetTriggerClass() throws IOException {
        // BadAttributeValueExpException is the classic entry point of several deserialization gadget chains and lives in
        // the javax.management package, which is not on the allowlist. It must be rejected before instantiation.
        byte[] bytes = serialize(new BadAttributeValueExpException(null));
        try {
            ZookeeperDistributedQueue.deserialize(bytes, ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER);
            fail("Expected deserialization of a known gadget-trigger class to be refused.");
        } catch (DistributedQueueException e) {
            assertTrue(e.getCause() instanceof InvalidClassException);
        }
    }
}
