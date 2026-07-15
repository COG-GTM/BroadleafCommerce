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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Verifies that queue payload deserialization is hardened against insecure deserialization (CWE-502): only classes on
 * the configured allow-list are resolved, and resource limits are enforced.
 *
 * @author Broadleaf Commerce
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private static byte[] serialize(Serializable obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    @Test
    public void testAllowedValueTypesRoundTrip() throws Exception {
        SecureQueueDeserializer deserializer = new SecureQueueDeserializer();

        assertEquals(42, deserializer.deserialize(serialize(Integer.valueOf(42))));
        assertEquals("hello", deserializer.deserialize(serialize("hello")));

        ArrayList<Integer> list = new ArrayList<>();
        Collections.addAll(list, 1, 2, 3);
        assertEquals(list, deserializer.deserialize(serialize(list)));
    }

    @Test
    public void testNullBytesReturnsNull() throws Exception {
        assertNull(new SecureQueueDeserializer().deserialize(null));
    }

    /**
     * {@link File} is {@link Serializable} but lives under {@code java.io.}, which is not on the default allow-list, so
     * it stands in for any "gadget" class an attacker might smuggle into the stream.
     */
    @Test
    public void testDisallowedClassIsRejected() throws Exception {
        SecureQueueDeserializer deserializer = new SecureQueueDeserializer();
        byte[] payload = serialize(new File("/etc/passwd"));

        InvalidClassException ex = assertThrows(InvalidClassException.class, () -> deserializer.deserialize(payload));
        assertTrue(ex.getMessage().contains("java.io.File"));
    }

    @Test
    public void testDisallowedClassNestedInAllowedCollectionIsRejected() throws Exception {
        SecureQueueDeserializer deserializer = new SecureQueueDeserializer();
        ArrayList<Object> list = new ArrayList<>();
        list.add(new File("/etc/passwd"));

        assertThrows(InvalidClassException.class, () -> deserializer.deserialize(serialize(list)));
    }

    @Test
    public void testRegisteringPrefixAllowsPreviouslyBlockedClass() throws Exception {
        SecureQueueDeserializer deserializer = new SecureQueueDeserializer();
        byte[] payload = serialize(new File("relative/path"));

        assertThrows(InvalidClassException.class, () -> deserializer.deserialize(payload));

        deserializer.addAllowedClassNamePrefix("java.io.");
        Object result = deserializer.deserialize(payload);
        assertEquals(new File("relative/path"), result);
    }

    @Test
    public void testIsClassAllowed() {
        SecureQueueDeserializer deserializer = new SecureQueueDeserializer();

        assertTrue(deserializer.isClassAllowed("java.lang.Integer"));
        assertTrue(deserializer.isClassAllowed("java.util.ArrayList"));
        assertTrue(deserializer.isClassAllowed("org.broadleafcommerce.core.util.queue.Foo"));

        assertFalse(deserializer.isClassAllowed("java.io.File"));
        assertFalse(deserializer.isClassAllowed("org.apache.commons.collections.functors.InvokerTransformer"));
        assertFalse(deserializer.isClassAllowed(null));
    }

    @Test
    public void testDepthLimitRejectsDeeplyNestedPayload() throws Exception {
        // Small depth limit so a deeply nested (but otherwise allow-listed) graph trips the ObjectInputFilter.
        SecureQueueDeserializer deserializer = new SecureQueueDeserializer(
                new LinkedHashSet<>(SecureQueueDeserializer.DEFAULT_ALLOWED_CLASS_NAME_PREFIXES), 5, 10000L, 1024L * 1024L);

        List<Object> current = new ArrayList<>();
        List<Object> root = current;
        for (int i = 0; i < 50; i++) {
            List<Object> next = new ArrayList<>();
            current.add(next);
            current = next;
        }

        assertThrows(IOException.class, () -> deserializer.deserialize(serialize((Serializable) root)));
    }
}
