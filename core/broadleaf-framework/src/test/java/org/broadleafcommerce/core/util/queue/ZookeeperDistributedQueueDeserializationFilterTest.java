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

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ZookeeperDistributedQueueDeserializationFilterTest {

    @Test
    public void shouldAllowAllowlistedClasses() throws Exception {
        assertEquals(Integer.valueOf(42), deserialize(serialize(42), ZookeeperDistributedQueue.buildDeserializationFilter(null)));
        assertEquals("value", deserialize(serialize("value"), ZookeeperDistributedQueue.buildDeserializationFilter(null)));

        List<String> list = new ArrayList<>();
        list.add("value");
        assertEquals(list, deserialize(serialize(list), ZookeeperDistributedQueue.buildDeserializationFilter(null)));

        HashMap<String, Integer> map = new HashMap<>();
        map.put("value", 42);
        assertEquals(map, deserialize(serialize(map), ZookeeperDistributedQueue.buildDeserializationFilter(null)));
    }

    @Test(expected = InvalidClassException.class)
    public void shouldRejectNonAllowlistedClasses() throws Exception {
        deserialize(serialize(new File("value")), ZookeeperDistributedQueue.buildDeserializationFilter(null));
    }

    @Test
    public void shouldAllowAdditionalClassPatterns() throws Exception {
        Object result = deserialize(
                serialize(new File("value")),
                ZookeeperDistributedQueue.buildDeserializationFilter("java.io.File")
        );

        assertEquals(new File("value"), result);
    }

    private byte[] serialize(Object object) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
        }
        return baos.toByteArray();
    }

    private Object deserialize(byte[] bytes, ObjectInputFilter filter) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                ObjectInputStream ois = new ObjectInputStream(bais)) {
            ois.setObjectInputFilter(filter);
            return ois.readObject();
        }
    }
}
