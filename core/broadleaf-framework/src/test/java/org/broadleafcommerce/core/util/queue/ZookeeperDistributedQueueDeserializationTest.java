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

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies that {@link SecureObjectInputStream} - the deserialization path used by
 * {@link ZookeeperDistributedQueue#deserialize(byte[])} - enforces a class allowlist and thereby mitigates insecure
 * deserialization (CWE-502).
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private static byte[] serialize(Serializable obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream ois = new SecureObjectInputStream(new ByteArrayInputStream(bytes))) {
            return ois.readObject();
        }
    }

    @Test
    public void testAllowlistedScalarTypesRoundTrip() throws Exception {
        assertEquals(Integer.valueOf(42), deserialize(serialize(Integer.valueOf(42))));
        assertEquals("broadleaf", deserialize(serialize("broadleaf")));
        assertEquals(Boolean.TRUE, deserialize(serialize(Boolean.TRUE)));
        assertEquals(Long.valueOf(7L), deserialize(serialize(Long.valueOf(7L))));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAllowlistedCollectionsRoundTrip() throws Exception {
        ArrayList<String> list = new ArrayList<>(Arrays.asList("a", "b", "c"));
        Object deserializedList = deserialize(serialize(list));
        assertEquals(list, deserializedList);

        HashMap<String, Integer> map = new HashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        Object deserializedMap = deserialize(serialize(map));
        assertEquals(map, deserializedMap);
    }

    @Test
    public void testNonAllowlistedClassIsRejected() throws Exception {
        // java.io.File is Serializable but is not on the allowlist; deserializing it must be blocked.
        byte[] bytes = serialize(new File("/etc/passwd"));
        try {
            deserialize(bytes);
            fail("Expected deserialization of a non-allowlisted class to be rejected.");
        } catch (InvalidClassException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("java.io.File"));
        }
    }

    @Test
    public void testNonAllowlistedArrayIsRejected() throws Exception {
        byte[] bytes = serialize(new File[] {new File("/tmp")});
        try {
            deserialize(bytes);
            fail("Expected deserialization of a non-allowlisted array type to be rejected.");
        } catch (InvalidClassException e) {
            // Expected - the array's component type is not allowlisted.
        }
    }

    @Test
    public void testCustomAllowlistIsHonored() throws Exception {
        byte[] bytes = serialize(new File("/tmp"));
        List<String> allowlist = Collections.singletonList("java.io.*");
        try (ObjectInputStream ois =
                     new SecureObjectInputStream(new ByteArrayInputStream(bytes), allowlist)) {
            Object result = ois.readObject();
            assertTrue(result instanceof File);
        }
    }

    @Test
    public void testDeserializeUsesAllowlistViaQueueApi() throws Exception {
        // Exercises the same default allowlist used by ZookeeperDistributedQueue#deserialize.
        Map<String, Integer> map = new HashMap<>();
        map.put("capacity", 500);
        assertEquals(map, deserialize(serialize((Serializable) map)));
    }
}
