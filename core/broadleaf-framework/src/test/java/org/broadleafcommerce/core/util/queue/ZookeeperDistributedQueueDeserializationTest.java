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

import javax.management.ObjectName;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;

/**
 * Unit tests covering the {@link ObjectInputFilter} pattern used by
 * {@link ZookeeperDistributedQueue#deserialize(byte[])} to mitigate insecure Java deserialization
 * (CWE-502) of data read back from Zookeeper.
 * <p>
 * These tests exercise the
 * {@link ZookeeperDistributedQueue#DEFAULT_DESERIALIZATION_FILTER_PATTERN} directly so they do not
 * require a live Zookeeper connection. They confirm that:
 * <ul>
 *     <li>well-known JDK collection / primitive wrapper types deserialize successfully,</li>
 *     <li>arbitrary attacker-controlled types are rejected with an {@link InvalidClassException},
 *     and</li>
 *     <li>the pattern still ends with the {@code !*} deny-all token, preventing accidental
 *     regressions.</li>
 * </ul>
 *
 * @author Devin
 */
public class ZookeeperDistributedQueueDeserializationTest extends TestCase {

    private static byte[] javaSerialize(Serializable obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    private static Object deserializeWithDefaultFilter(byte[] bytes) throws Exception {
        ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
                ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN);
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            ois.setObjectInputFilter(filter);
            return ois.readObject();
        }
    }

    public void testAllowedJavaTypesAreDeserialized() throws Exception {
        ArrayList<String> list = new ArrayList<>();
        list.add("alpha");
        list.add("beta");
        Object listResult = deserializeWithDefaultFilter(javaSerialize(list));
        assertTrue("ArrayList should be deserialized", listResult instanceof ArrayList);
        assertEquals(list, listResult);

        HashMap<String, Integer> map = new HashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        assertEquals(map, deserializeWithDefaultFilter(javaSerialize(map)));

        LinkedHashMap<String, String> linkedMap = new LinkedHashMap<>();
        linkedMap.put("first", "value1");
        linkedMap.put("second", "value2");
        assertEquals(linkedMap, deserializeWithDefaultFilter(javaSerialize(linkedMap)));

        HashSet<Integer> set = new HashSet<>();
        set.add(1);
        set.add(2);
        set.add(3);
        assertEquals(set, deserializeWithDefaultFilter(javaSerialize(set)));

        assertEquals("hello", deserializeWithDefaultFilter(javaSerialize("hello")));
        assertEquals(Integer.valueOf(42), deserializeWithDefaultFilter(javaSerialize(Integer.valueOf(42))));
        assertEquals(Boolean.TRUE, deserializeWithDefaultFilter(javaSerialize(Boolean.TRUE)));
    }

    public void testDisallowedClassIsRejected() throws Exception {
        // javax.management.ObjectName is Serializable but lives in javax.management, which is
        // intentionally NOT covered by the default allow-list (the filter only allows java.**,
        // org.apache.solr.**, and org.broadleafcommerce.**). Deserializing it must therefore be
        // rejected by the filter.
        byte[] payload = javaSerialize(new ObjectName("broadleaf:type=DisallowedTest"));
        try {
            deserializeWithDefaultFilter(payload);
            fail("Expected deserialization of a non-allow-listed class to fail.");
        } catch (InvalidClassException e) {
            // Expected: the ObjectInputFilter should reject the unknown class.
            assertNotNull("InvalidClassException must include a message", e.getMessage());
        }
    }

    public void testNonSerializedBytesProduceIoException() {
        byte[] garbage = new byte[]{0x01, 0x02, 0x03, 0x04};
        try {
            deserializeWithDefaultFilter(garbage);
            fail("Expected deserialization of garbage bytes to fail.");
        } catch (IOException e) {
            // Expected: not a valid serialized stream header.
        } catch (Exception e) {
            fail("Expected IOException, got: " + e);
        }
    }

    /**
     * Sanity check that the static filter pattern is non-empty and ends with a deny-all clause,
     * preventing accidental regressions where a maintainer removes the {@code !*} terminator.
     */
    public void testFilterPatternEndsWithDenyAll() {
        String pattern = ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN;
        assertNotNull(pattern);
        assertTrue("Filter pattern must end with deny-all token to reject unknown classes",
                pattern.endsWith("!*"));
    }

    /**
     * Confirms that the pattern parses successfully via the JDK's filter factory; if the pattern
     * is malformed the test fails with the underlying IllegalArgumentException, providing a fast
     * signal during builds.
     */
    public void testFilterPatternIsValid() {
        ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
                ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN);
        assertNotNull("createFilter must return a non-null filter for the default pattern", filter);
    }

}
