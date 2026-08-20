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
import java.io.File;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

/**
 * Verifies the allow list that {@link ZookeeperDistributedQueue} applies when reading elements from Zookeeper.
 */
public class ZookeeperDistributedQueueDeserializationFilterTest extends TestCase {

    public void testAllowsExpectedQueueElementTypes() throws Exception {
        Map<String, List<String>> element = new HashMap<>();
        element.put("commands", new ArrayList<>(java.util.Arrays.asList("a", "b")));

        Object result = readWithFilter(serialize((Serializable) element));

        assertEquals(element, result);
    }

    public void testRejectsTypesOutsideOfTheAllowList() throws Exception {
        byte[] bytes = serialize(new File("/tmp/gadget"));

        try {
            readWithFilter(bytes);
            fail("Expected the deserialization filter to reject java.io.File.");
        } catch (InvalidClassException e) {
            assertTrue(e.getMessage().contains("filter status: REJECTED"));
        }
    }

    private byte[] serialize(Serializable object) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
        }
        return baos.toByteArray();
    }

    private Object readWithFilter(byte[] bytes) throws Exception {
        ObjectInputFilter filter =
                ObjectInputFilter.Config.createFilter(ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN);
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            ois.setObjectInputFilter(filter);
            return ois.readObject();
        }
    }

}
