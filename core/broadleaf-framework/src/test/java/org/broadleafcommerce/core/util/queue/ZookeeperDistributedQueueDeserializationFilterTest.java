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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 * Verifies that {@link ZookeeperDistributedQueue#buildDeserializationFilter(Set, Set, long, long, long, long)}
 * permits the expected payloads while rejecting classes outside its allowlist and streams that exceed the
 * configured resource limits. Together these guard against the insecure deserialization vector
 * (CWE-502) that previously existed in {@link ZookeeperDistributedQueue#deserialize(byte[])}.
 */
public class ZookeeperDistributedQueueDeserializationFilterTest {

    private static final Set<String> ALLOWED_PACKAGES = Set.of("java.lang", "java.util");
    private static final Set<String> ALLOWED_CLASSES = Set.of();
    private static final long MAX_DEPTH = 64L;
    private static final long MAX_REFS = 1_000_000L;
    private static final long MAX_BYTES = 1_048_576L;
    private static final long MAX_ARRAY_LENGTH = 100_000L;

    @Test
    public void allowsAllowlistedPackages() throws Exception {
        Object roundTripped = roundTrip(buildFilter(), 42);
        assertEquals(Integer.valueOf(42), roundTripped);

        assertEquals("hello", roundTrip(buildFilter(), "hello"));

        ArrayList<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);
        Object deserializedList = roundTrip(buildFilter(), list);
        assertEquals(list, deserializedList);

        HashMap<String, Integer> map = new HashMap<>();
        map.put("a", 1);
        Object deserializedMap = roundTrip(buildFilter(), map);
        assertEquals(map, deserializedMap);
    }

    @Test
    public void rejectsClassOutsideAllowlist() throws Exception {
        byte[] bytes = serialize(new NotAllowedPayload("evil"));
        try {
            roundTripBytes(buildFilter(), bytes);
            fail("Expected InvalidClassException for non-allowlisted class");
        } catch (InvalidClassException expected) {
            // expected: filter rejected NotAllowedPayload
        }
    }

    @Test
    public void rejectsClassOutsideAllowlistWithCustomPackageHook() throws Exception {
        // Sanity check: when the caller adds the payload's package to the allowlist (the documented
        // extension hook for subclasses), the same payload deserializes successfully.
        Set<String> extendedPackages = Set.of("java.lang", "java.util",
                NotAllowedPayload.class.getPackageName());
        ObjectInputFilter extendedFilter = ZookeeperDistributedQueue.buildDeserializationFilter(
                extendedPackages, ALLOWED_CLASSES,
                MAX_DEPTH, MAX_REFS, MAX_BYTES, MAX_ARRAY_LENGTH);

        Object result = roundTrip(extendedFilter, new NotAllowedPayload("ok"));
        assertEquals(new NotAllowedPayload("ok"), result);
    }

    @Test
    public void rejectsStreamsExceedingDepthLimit() throws Exception {
        // Construct a deep chain of nested ArrayLists that exceeds the configured max depth.
        ArrayList<Object> head = new ArrayList<>();
        ArrayList<Object> current = head;
        for (int i = 0; i < 200; i++) {
            ArrayList<Object> next = new ArrayList<>();
            current.add(next);
            current = next;
        }
        byte[] bytes = serialize(head);

        // Build a filter with a tiny depth limit; the rest of the limits stay generous.
        ObjectInputFilter shallowFilter = ZookeeperDistributedQueue.buildDeserializationFilter(
                ALLOWED_PACKAGES, ALLOWED_CLASSES,
                /* maxDepth */ 8L, MAX_REFS, MAX_BYTES, MAX_ARRAY_LENGTH);

        try {
            roundTripBytes(shallowFilter, bytes);
            fail("Expected InvalidClassException because depth limit was exceeded");
        } catch (InvalidClassException expected) {
            // expected: depth check rejected the deeply nested graph
        }
    }

    @Test
    public void filterStatusWhenByteLimitExceededIsRejected() {
        ObjectInputFilter filter = buildFilter();
        ObjectInputFilter.FilterInfo info = filterInfoFor(Integer.class, 1, 1, MAX_BYTES + 1, -1);
        assertSame(ObjectInputFilter.Status.REJECTED, filter.checkInput(info));
    }

    @Test
    public void filterStatusWhenReferenceLimitExceededIsRejected() {
        ObjectInputFilter filter = buildFilter();
        ObjectInputFilter.FilterInfo info = filterInfoFor(Integer.class, 1, MAX_REFS + 1, 100, -1);
        assertSame(ObjectInputFilter.Status.REJECTED, filter.checkInput(info));
    }

    @Test
    public void filterStatusForAllowlistedClassIsAllowed() {
        ObjectInputFilter filter = buildFilter();
        ObjectInputFilter.FilterInfo info = filterInfoFor(Integer.class, 1, 1, 100, -1);
        assertSame(ObjectInputFilter.Status.ALLOWED, filter.checkInput(info));
    }

    @Test
    public void filterStatusForUnknownClassIsRejected() {
        ObjectInputFilter filter = buildFilter();
        ObjectInputFilter.FilterInfo info = filterInfoFor(NotAllowedPayload.class, 1, 1, 100, -1);
        assertSame(ObjectInputFilter.Status.REJECTED, filter.checkInput(info));
    }

    @Test
    public void filterStatusForArrayUnwrapsComponentType() {
        ObjectInputFilter filter = buildFilter();
        // String[] should be allowed because String resides in java.lang.
        ObjectInputFilter.FilterInfo allowed = filterInfoFor(String[].class, 1, 1, 100, 4);
        assertSame(ObjectInputFilter.Status.ALLOWED, filter.checkInput(allowed));

        // NotAllowedPayload[] should be rejected because the component type is not allowed.
        ObjectInputFilter.FilterInfo rejected = filterInfoFor(NotAllowedPayload[].class, 1, 1, 100, 4);
        assertSame(ObjectInputFilter.Status.REJECTED, filter.checkInput(rejected));
    }

    @Test
    public void filterStatusForArrayLengthOverLimitIsRejected() {
        ObjectInputFilter filter = buildFilter();
        ObjectInputFilter.FilterInfo info = filterInfoFor(Integer[].class, 1, 1, 100, MAX_ARRAY_LENGTH + 1);
        assertSame(ObjectInputFilter.Status.REJECTED, filter.checkInput(info));
    }

    @Test
    public void filterStatusForResourceCheckWithoutClassIsUndecided() {
        ObjectInputFilter filter = buildFilter();
        ObjectInputFilter.FilterInfo info = filterInfoFor(null, 1, 1, 100, -1);
        assertSame(ObjectInputFilter.Status.UNDECIDED, filter.checkInput(info));
    }

    private static ObjectInputFilter buildFilter() {
        return ZookeeperDistributedQueue.buildDeserializationFilter(
                ALLOWED_PACKAGES, ALLOWED_CLASSES,
                MAX_DEPTH, MAX_REFS, MAX_BYTES, MAX_ARRAY_LENGTH);
    }

    private static byte[] serialize(Serializable obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    private static Object roundTrip(ObjectInputFilter filter, Serializable obj) throws Exception {
        return roundTripBytes(filter, serialize(obj));
    }

    private static Object roundTripBytes(ObjectInputFilter filter, byte[] bytes) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            ois.setObjectInputFilter(filter);
            return ois.readObject();
        }
    }

    private static ObjectInputFilter.FilterInfo filterInfoFor(final Class<?> serialClass,
                                                              final long depth,
                                                              final long references,
                                                              final long streamBytes,
                                                              final long arrayLength) {
        return new ObjectInputFilter.FilterInfo() {
            @Override
            public Class<?> serialClass() {
                return serialClass;
            }

            @Override
            public long arrayLength() {
                return arrayLength;
            }

            @Override
            public long depth() {
                return depth;
            }

            @Override
            public long references() {
                return references;
            }

            @Override
            public long streamBytes() {
                return streamBytes;
            }
        };
    }

    /**
     * A serializable payload deliberately placed outside the allowlisted packages used in this
     * test so that we can verify that the filter rejects unknown types.
     */
    private static final class NotAllowedPayload implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String value;

        private NotAllowedPayload(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof NotAllowedPayload)) {
                return false;
            }
            return java.util.Objects.equals(value, ((NotAllowedPayload) o).value);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hashCode(value);
        }
    }

}
