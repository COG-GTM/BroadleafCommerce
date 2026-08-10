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

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;

public class ZookeeperDistributedQueueDeserializationFilterTest extends TestCase {

    public void testAllowedTypesAreDeserialized() throws Exception {
        final ArrayList<String> payload = new ArrayList<>();
        payload.add("some-queue-entry");

        assertEquals(payload, readFiltered(serialize(payload)));
        assertEquals(Integer.valueOf(500), readFiltered(serialize(Integer.valueOf(500))));
    }

    public void testDisallowedTypesAreRejected() throws Exception {
        final byte[] bytes = serialize(new File("/tmp/not-a-queue-entry"));

        try {
            readFiltered(bytes);
            fail("Expected the deserialization filter to reject java.io.File");
        } catch (InvalidClassException e) {
            assertTrue(e.getMessage().contains("filter status: REJECTED"));
        }
    }

    public void testAdditionalAllowedClassesProperty() throws Exception {
        System.setProperty(ZookeeperDistributedQueue.ADDITIONAL_ALLOWED_CLASSES_PROPERTY, "java.io.File");
        try {
            assertEquals(new File("/tmp/allowed"), readFiltered(serialize(new File("/tmp/allowed"))));
        } finally {
            System.clearProperty(ZookeeperDistributedQueue.ADDITIONAL_ALLOWED_CLASSES_PROPERTY);
        }
    }

    private Object readFiltered(byte[] bytes) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            ois.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
                    ZookeeperDistributedQueue.buildDeserializationFilterPattern()));
            return ois.readObject();
        }
    }

    private byte[] serialize(Serializable obj) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }
}
