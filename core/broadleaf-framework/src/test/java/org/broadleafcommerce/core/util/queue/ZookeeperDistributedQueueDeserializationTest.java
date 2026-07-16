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

import org.broadleafcommerce.core.util.queue.ZookeeperDistributedQueue.ValidatingObjectInputStream;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Verifies that {@link ZookeeperDistributedQueue} guards against insecure deserialization (CWE-502) by
 * only reconstructing classes that are on the configured allowlist.
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private static byte[] serialize(Serializable obj) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
            return baos.toByteArray();
        }
    }

    private static Object deserialize(byte[] bytes) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                ObjectInputStream ois = new ValidatingObjectInputStream(bais,
                        ZookeeperDistributedQueue.DEFAULT_ALLOWED_DESERIALIZATION_PACKAGES)) {
            return ois.readObject();
        }
    }

    @Test
    public void testAllowedScalarTypesAreDeserialized() throws Exception {
        assertEquals(Integer.valueOf(42), deserialize(serialize(Integer.valueOf(42))));
        assertEquals("hello", deserialize(serialize("hello")));
    }

    @Test
    public void testAllowedCollectionTypesAreDeserialized() throws Exception {
        ArrayList<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        @SuppressWarnings("unchecked")
        ArrayList<String> result = (ArrayList<String>) deserialize(serialize(list));
        assertEquals(list, result);

        Map<String, Integer> map = new HashMap<>();
        map.put("x", 1);
        assertEquals(map, deserialize(serialize((Serializable) map)));
    }

    @Test
    public void testAllowedArrayTypesAreDeserialized() throws Exception {
        int[] primitives = new int[] {1, 2, 3};
        int[] primitiveResult = (int[]) deserialize(serialize(primitives));
        assertEquals(3, primitiveResult.length);
        assertEquals(2, primitiveResult[1]);

        String[] strings = new String[] {"a", "b"};
        String[] stringResult = (String[]) deserialize(serialize(strings));
        assertEquals(2, stringResult.length);
        assertEquals("b", stringResult[1]);
    }

    @Test
    public void testDisallowedClassIsRejected() throws Exception {
        byte[] payload = serialize(new File("/etc/passwd"));
        try {
            deserialize(payload);
            fail("Expected deserialization of a non-allowlisted class (java.io.File) to be rejected.");
        } catch (InvalidClassException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("java.io.File"));
        }
    }

    @Test
    public void testDisallowedClassNestedInAllowedCollectionIsRejected() throws Exception {
        ArrayList<Object> list = new ArrayList<>();
        list.add(new File("/etc/passwd"));
        try {
            deserialize(serialize(list));
            fail("Expected deserialization of a non-allowlisted class nested in a collection to be rejected.");
        } catch (InvalidClassException e) {
            assertTrue(e.getMessage() != null && e.getMessage().contains("java.io.File"));
        }
    }
}
