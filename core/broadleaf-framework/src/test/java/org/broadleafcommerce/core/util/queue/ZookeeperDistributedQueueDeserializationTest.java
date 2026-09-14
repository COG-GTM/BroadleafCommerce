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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ZookeeperDistributedQueueDeserializationTest {

    @Test
    public void testAllowedValues() throws Exception {
        assertEquals(Integer.valueOf(42), deserialize(serialize(Integer.valueOf(42))));

        ArrayList<String> values = new ArrayList<>();
        values.add("one");
        values.add("two");
        assertEquals(values, deserialize(serialize(values)));
    }

    @Test(expected = InvalidClassException.class)
    public void testDisallowedClassIsRejected() throws Exception {
        Map<String, Object> values = new HashMap<>();
        values.put("url", new java.net.URL("https://example.com"));
        deserialize(serialize(values));
    }

    @Test
    public void testAllowedArrayComponentType() throws Exception {
        Integer[] values = {1, 2, 3};
        Integer[] deserialized = (Integer[]) deserialize(serialize(values));
        assertEquals(values.length, deserialized.length);
        for (int i = 0; i < values.length; i++) {
            assertEquals(values[i], deserialized[i]);
        }
    }

    private byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(value);
        }
        return baos.toByteArray();
    }

    private Object deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            ois.setObjectInputFilter(new ZookeeperDistributedQueue.AllowListObjectInputFilter(
                    ZookeeperDistributedQueue.DEFAULT_ALLOWED_DESERIALIZATION_CLASS_PATTERNS));
            return ois.readObject();
        }
    }
}
