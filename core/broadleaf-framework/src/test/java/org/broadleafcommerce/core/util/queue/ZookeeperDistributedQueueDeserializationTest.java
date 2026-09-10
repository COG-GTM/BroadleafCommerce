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
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ZookeeperDistributedQueueDeserializationTest {

    private static final Set<String> ALLOWED_CLASSES = new HashSet<>(Arrays.asList(
            String.class.getName(),
            Integer.class.getName(),
            Number.class.getName(),
            Object.class.getName(),
            ArrayList.class.getName()
    ));
    private static final Set<String> ALLOWED_PREFIXES = new HashSet<>(Arrays.asList("org.broadleafcommerce."));

    @Test
    public void allowedObjectsRoundTrip() throws Exception {
        assertEquals(Integer.valueOf(42), roundTrip(Integer.valueOf(42)));
        assertEquals("allowed", roundTrip("allowed"));

        ArrayList<String> values = new ArrayList<>();
        values.add("one");
        values.add("two");
        assertEquals(values, roundTrip(values));

        BroadleafSerializable value = new BroadleafSerializable("allowed");
        assertEquals(value, roundTrip(value));
    }

    @Test
    public void disallowedObjectsAreRejected() throws Exception {
        byte[] serialized;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(new AtomicReference<>("rejected"));
            objectOutput.flush();
            serialized = output.toByteArray();
        }

        try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            objectInput.setObjectInputFilter(createFilter());
            objectInput.readObject();
            fail("Expected InvalidClassException");
        } catch (InvalidClassException expected) {
        }
    }

    private static ObjectInputFilter createFilter() {
        return ZookeeperDistributedQueue.buildDeserializationFilter(ALLOWED_CLASSES, ALLOWED_PREFIXES);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        byte[] serialized;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
            objectOutput.flush();
            serialized = output.toByteArray();
        }

        try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            objectInput.setObjectInputFilter(createFilter());
            return (T) objectInput.readObject();
        }
    }

    private static class BroadleafSerializable implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String value;

        private BroadleafSerializable(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof BroadleafSerializable)) {
                return false;
            }
            BroadleafSerializable that = (BroadleafSerializable) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
