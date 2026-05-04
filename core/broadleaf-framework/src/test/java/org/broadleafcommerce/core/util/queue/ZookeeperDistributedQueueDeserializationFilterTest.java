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
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

/**
 * Unit tests for the JEP 290 {@link ObjectInputFilter} that
 * {@link ZookeeperDistributedQueue#deserialize(byte[])} installs to mitigate CWE-502 (insecure deserialization /
 * RCE-via-gadget-chain) on bytes read from Zookeeper.
 *
 * @author Devin
 */
public class ZookeeperDistributedQueueDeserializationFilterTest extends TestCase {

    public void testDefaultAllowlistAcceptsInteger() throws Exception {
        ObjectInputFilter filter = ZookeeperDistributedQueue.createDeserializationFilter(
                Arrays.asList(Integer.class.getName(), Number.class.getName()),
                null
        );

        Object result = readWithFilter(serialize(42), filter);

        assertEquals(Integer.valueOf(42), result);
    }

    public void testDefaultAllowlistRejectsNonAllowedClass() throws Exception {
        ObjectInputFilter filter = ZookeeperDistributedQueue.createDeserializationFilter(
                Arrays.asList(Integer.class.getName(), Number.class.getName()),
                null
        );

        byte[] payload = serialize(new HashMap<String, String>(Collections.singletonMap("k", "v")));

        try {
            readWithFilter(payload, filter);
            fail("Expected InvalidClassException because java.util.HashMap is not in the allowlist");
        } catch (InvalidClassException expected) {
            // pass
        }
    }

    public void testAdditionalPatternsAllowAdditionalClass() throws Exception {
        ObjectInputFilter filter = ZookeeperDistributedQueue.createDeserializationFilter(
                Arrays.asList(Integer.class.getName(), Number.class.getName()),
                Arrays.asList("java.util.*", "java.lang.*")
        );

        HashMap<String, String> input = new HashMap<>();
        input.put("hello", "world");

        @SuppressWarnings("unchecked")
        HashMap<String, String> result = (HashMap<String, String>) readWithFilter(serialize(input), filter);

        assertEquals("world", result.get("hello"));
    }

    public void testAdditionalPatternsStillRejectClassesOutsideAllowlist() throws Exception {
        ObjectInputFilter filter = ZookeeperDistributedQueue.createDeserializationFilter(
                Arrays.asList(Integer.class.getName(), Number.class.getName()),
                Collections.singletonList("java.util.*")
        );

        try {
            readWithFilter(serialize(new MaliciousPayload("uh oh")), filter);
            fail("Expected InvalidClassException because MaliciousPayload is not in the allowlist");
        } catch (InvalidClassException expected) {
            // pass
        }
    }

    public void testNullAndBlankPatternsAreIgnored() throws Exception {
        ObjectInputFilter filter = ZookeeperDistributedQueue.createDeserializationFilter(
                Arrays.asList(Integer.class.getName(), null, "  ", Number.class.getName()),
                Arrays.asList(null, "")
        );

        Object result = readWithFilter(serialize(7), filter);
        assertEquals(Integer.valueOf(7), result);
    }

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

    /**
     * Serializable type that lives in this test package and is therefore guaranteed to fall outside any of the
     * package-prefix allowlists used by the production code.
     */
    private static class MaliciousPayload implements Serializable {

        private static final long serialVersionUID = 1L;

        @SuppressWarnings("unused")
        private final String message;

        MaliciousPayload(String message) {
            this.message = message;
        }
    }
}
