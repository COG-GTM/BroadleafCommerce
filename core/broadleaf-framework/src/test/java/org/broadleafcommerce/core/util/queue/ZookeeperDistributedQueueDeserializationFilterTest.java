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
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that the {@link ObjectInputFilter} used by {@link ZookeeperDistributedQueue#deserialize(byte[])}
 * rejects classes that are not on the allow-list. This mitigates the insecure deserialization
 * vulnerability (CWE-502) where {@code ObjectInputStream.readObject()} was previously called on
 * Zookeeper-supplied bytes without any class filtering.
 */
public class ZookeeperDistributedQueueDeserializationFilterTest {

    private static byte[] serialize(Serializable obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    private static Object readWithFilter(byte[] bytes, ObjectInputFilter filter) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            ois.setObjectInputFilter(filter);
            return ois.readObject();
        }
    }

    private static ObjectInputFilter defaultFilter() {
        return ObjectInputFilter.Config.createFilter(
                ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN);
    }

    @Test
    public void defaultFilterAllowsCommonJdkTypes() throws Exception {
        ObjectInputFilter filter = defaultFilter();

        assertEquals(Integer.valueOf(42), readWithFilter(serialize(42), filter));
        assertEquals("hello", readWithFilter(serialize("hello"), filter));

        ArrayList<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        @SuppressWarnings("unchecked")
        List<String> deserialized = (List<String>) readWithFilter(serialize(list), filter);
        assertEquals(list, deserialized);
    }

    @Test
    public void defaultFilterRejectsUnknownClasses() throws Exception {
        ObjectInputFilter filter = defaultFilter();

        byte[] bytes = serialize(new UnauthorizedPayload("malicious"));
        try {
            readWithFilter(bytes, filter);
            fail("Expected InvalidClassException for non-allow-listed type");
        } catch (InvalidClassException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("filter") || expected.getMessage().contains("REJECTED"));
        }
    }

    @Test
    public void filterCanBeExtendedWithAdditionalPatterns() throws Exception {
        String extended = UnauthorizedPayload.class.getName() + ';'
                + ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN;
        ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(extended);

        Object out = readWithFilter(serialize(new UnauthorizedPayload("ok")), filter);
        assertEquals("ok", ((UnauthorizedPayload) out).value);
    }

    /**
     * Stand-in for an application class an attacker might attempt to deserialize. It is intentionally
     * harmless but is not on the default allow-list, so the filter must reject it.
     */
    private static final class UnauthorizedPayload implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String value;

        UnauthorizedPayload(String value) {
            this.value = value;
        }
    }
}
