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

import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Verifies that queue entries are deserialized through a class allow list rather than an unrestricted
 * {@link ObjectInputStream}.
 */
public class ZookeeperDistributedQueueDeserializationTest {

    @After
    public void tearDown() {
        System.clearProperty(ZookeeperDistributedQueue.ADDITIONAL_ALLOWED_CLASSES_PROPERTY);
    }

    @Test
    public void testAllowedTypesAreDeserialized() throws Exception {
        ArrayList<String> list = new ArrayList<>();
        list.add("first");
        list.add("second");
        assertEquals(list, readWithFilter(serialize(list)));

        HashMap<String, Integer> map = new HashMap<>();
        map.put("count", 2);
        assertEquals(map, readWithFilter(serialize(map)));
    }

    @Test
    public void testDisallowedTypeIsRejected() throws Exception {
        assertRejected(serialize(new File("/tmp/some-file")));
    }

    @Test
    public void testDisallowedTypeNestedInAllowedCollectionIsRejected() throws Exception {
        ArrayList<Serializable> list = new ArrayList<>();
        list.add(new File("/tmp/some-file"));
        assertRejected(serialize(list));
    }

    @Test
    public void testAdditionalAllowedClassesProperty() throws Exception {
        byte[] payload = serialize(new File("/tmp/some-file"));
        assertRejected(payload);

        System.setProperty(ZookeeperDistributedQueue.ADDITIONAL_ALLOWED_CLASSES_PROPERTY, "java.io.File");
        assertEquals(new File("/tmp/some-file"), readWithFilter(payload));
    }

    private void assertRejected(byte[] payload) throws Exception {
        try {
            readWithFilter(payload);
            fail("Expected the deserialization filter to reject the payload.");
        } catch (InvalidClassException e) {
            //Expected
        }
    }

    private byte[] serialize(Serializable object) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
        }
        return baos.toByteArray();
    }

    private Object readWithFilter(byte[] bytes) throws Exception {
        ObjectInputFilter filter = ObjectInputFilter.Config
                .createFilter(ZookeeperDistributedQueue.buildDeserializationFilterPattern());
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            ois.setObjectInputFilter(filter);
            return ois.readObject();
        }
    }
}
