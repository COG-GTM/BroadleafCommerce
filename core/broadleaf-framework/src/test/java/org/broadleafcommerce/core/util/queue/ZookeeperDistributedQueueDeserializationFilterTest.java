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
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies that the allow list used by {@link ZookeeperDistributedQueue#deserialize(byte[])} accepts the types that are legitimately
 * placed on the queue and rejects everything else.
 */
public class ZookeeperDistributedQueueDeserializationFilterTest {

    private Object deserialize(Serializable obj) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            ois.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
                    ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN));
            return ois.readObject();
        }
    }

    @Test
    public void testAllowedTypesAreDeserialized() throws Exception {
        assertEquals(Integer.valueOf(500), deserialize(Integer.valueOf(500)));
        assertEquals("queue-entry", deserialize("queue-entry"));

        final List<String> entries = new ArrayList<>();
        entries.add("first");
        entries.add("second");
        assertEquals(entries, deserialize((Serializable) entries));
    }

    @Test
    public void testTypesOutsideOfTheAllowListAreRejected() throws Exception {
        assertRejected(new File("/tmp/attacker-controlled"));
        assertRejected(new AtomicReference<>("not-allowed"));
    }

    private void assertRejected(Serializable obj) throws Exception {
        try {
            deserialize(obj);
            fail("Expected " + obj.getClass().getName() + " to be rejected by the deserialization filter.");
        } catch (InvalidClassException e) {
            //Expected.
        }
    }
}
