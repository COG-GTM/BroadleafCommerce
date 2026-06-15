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

import org.broadleafcommerce.core.search.service.solr.indexer.FullReindexCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand;
import org.junit.Test;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies the {@link java.io.ObjectInputFilter} allow-list that {@link ZookeeperDistributedQueue#deserialize(byte[])}
 * applies to data read out of Zookeeper. This is the mitigation for the insecure deserialization vulnerability (CWE-502):
 * only explicitly allow-listed classes may be deserialized; everything else is rejected.
 *
 * <p>The {@link ZookeeperDistributedQueue} constructor requires a live Zookeeper client (and its transitive Netty
 * dependency), which is not available in this unit-test context. These tests therefore build the filter from the exact
 * production constant ({@link ZookeeperDistributedQueue#DEFAULT_DESERIALIZATION_FILTER_PATTERN}) and exercise the same
 * filtered {@link ObjectInputStream} read path that {@code deserialize(byte[])} performs, so a regression in the
 * allow-list is caught here.
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private final ObjectInputFilter filter =
            ObjectInputFilter.Config.createFilter(ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN);

    @Test
    public void testDefaultFilterPatternRejectsByDefault() {
        // The allow-list must end with a reject-all token so anything not explicitly permitted is denied.
        assertTrue("The default deserialization filter must reject anything not explicitly allow-listed.",
                ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN.endsWith("!*"));
    }

    @Test
    public void testAllowListedSolrCommandRoundTrips() throws Exception {
        List<String> deleteQueries = new ArrayList<>();
        deleteQueries.add("id:123");
        IncrementalUpdateCommand command = new IncrementalUpdateCommand(new ArrayList<>(), deleteQueries);

        Object result = roundTrip(command);

        assertNotNull(result);
        assertTrue("Expected an IncrementalUpdateCommand but got " + result.getClass(),
                result instanceof IncrementalUpdateCommand);
        assertEquals(deleteQueries, ((IncrementalUpdateCommand) result).getDeleteQueries());
    }

    @Test
    public void testAllowListedSimpleCommandRoundTrips() throws Exception {
        Object result = roundTrip(new FullReindexCommand());
        assertTrue("Expected a FullReindexCommand but got " + result.getClass(),
                result instanceof FullReindexCommand);
    }

    @Test
    public void testAllowListedJavaCollectionsRoundTrip() throws Exception {
        Map<String, Integer> map = new HashMap<>();
        map.put("a", 1);

        Object result = roundTrip((Serializable) map);

        assertEquals(map, result);
    }

    @Test
    public void testNonAllowListedClassIsRejected() throws Exception {
        // java.io.File is Serializable but lives in a package that is NOT on the allow-list, so it must be rejected.
        assertRejected(new File("/etc/passwd"));
    }

    @Test
    public void testNonAllowListedSubpackageIsRejected() throws Exception {
        // java.util.regex.Pattern is Serializable; the allow-list permits the java.util package (java.util.*) but not
        // its subpackages, so a class from java.util.regex must be rejected.
        assertRejected(java.util.regex.Pattern.compile("a.*b"));
    }

    private void assertRejected(Serializable obj) throws Exception {
        byte[] bytes = serialize(obj);
        try {
            readWithFilter(bytes);
            fail("Expected deserialization of a non-allow-listed class (" + obj.getClass().getName()
                    + ") to be rejected.");
        } catch (InvalidClassException expected) {
            // expected: the filter rejected the class
        }
    }

    private Object roundTrip(Serializable obj) throws Exception {
        return readWithFilter(serialize(obj));
    }

    /**
     * Mirrors the filtered read performed by {@link ZookeeperDistributedQueue#deserialize(byte[])}.
     */
    private Object readWithFilter(byte[] bytes) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            ois.setObjectInputFilter(filter);
            return ois.readObject();
        }
    }

    private static byte[] serialize(Serializable obj) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
            return baos.toByteArray();
        }
    }
}
