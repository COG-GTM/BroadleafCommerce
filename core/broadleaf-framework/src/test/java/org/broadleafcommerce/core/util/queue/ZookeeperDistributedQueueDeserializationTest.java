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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.solr.common.SolrInputDocument;
import org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies that {@link ZookeeperDistributedQueue#deserialize(byte[], java.io.ObjectInputFilter)} enforces the class
 * allowlist that hardens the queue against insecure deserialization (CWE-502), while still round-tripping the legitimate
 * element and config types the queue is used with.
 *
 * @author Devin
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private final ObjectInputFilter filter =
            ObjectInputFilter.Config.createFilter(ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_ALLOWED_CLASS_PATTERN);

    private static byte[] serialize(Serializable obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    private Object deserialize(Serializable obj) throws Exception {
        return ZookeeperDistributedQueue.deserialize(serialize(obj), filter);
    }

    @Test
    public void testAllowedJdkValueTypesDeserialize() throws Exception {
        assertEquals("hello", deserialize("hello"));
        assertEquals(Integer.valueOf(42), deserialize(42));
        assertEquals(Long.valueOf(500L), deserialize(500L));
        assertEquals(Boolean.TRUE, deserialize(Boolean.TRUE));
        assertEquals(new BigDecimal("19.99"), deserialize(new BigDecimal("19.99")));
        assertNotNull(deserialize(new Date()));
    }

    @Test
    public void testAllowedJdkCollectionsDeserialize() throws Exception {
        ArrayList<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        assertEquals(list, deserialize(list));

        HashMap<String, Integer> map = new HashMap<>();
        map.put("one", 1);
        assertEquals(map, deserialize(map));

        // java.util subpackage types (e.g. java.util.concurrent) must be allowed since Solr payloads can reference them.
        ConcurrentHashMap<String, Integer> concurrentMap = new ConcurrentHashMap<>();
        concurrentMap.put("two", 2);
        assertEquals(concurrentMap, deserialize(concurrentMap));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLegitimateQueueElementDeserializes() throws Exception {
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "product-1");
        doc.addField("name", "Test Product");

        List<SolrInputDocument> docs = new ArrayList<>();
        docs.add(doc);
        List<String> deleteQueries = new ArrayList<>();
        deleteQueries.add("category:obsolete");

        IncrementalUpdateCommand command = new IncrementalUpdateCommand(docs, deleteQueries);

        Object result = deserialize(command);
        assertTrue(result instanceof IncrementalUpdateCommand);
        IncrementalUpdateCommand deserialized = (IncrementalUpdateCommand) result;
        assertEquals(1, deserialized.getSolrInputDocuments().size());
        assertEquals("product-1", deserialized.getSolrInputDocuments().get(0).getFieldValue("id"));
        assertEquals(deleteQueries, deserialized.getDeleteQueries());
    }

    @Test
    public void testDisallowedClassIsRejected() throws Exception {
        // java.io.File is serializable but lives in java.io, which is NOT covered by the allowlist.
        byte[] payload = serialize(new File("/etc/passwd"));
        try {
            ZookeeperDistributedQueue.deserialize(payload, filter);
            fail("Expected deserialization of a non-allowlisted class to be rejected.");
        } catch (DistributedBlockingQueue.DistributedQueueException e) {
            assertTrue("Expected the rejection to be caused by an InvalidClassException, but was: " + e.getCause(),
                    e.getCause() instanceof InvalidClassException);
        }
    }

    @Test
    public void testDisallowedClassNestedInAllowedCollectionIsRejected() throws Exception {
        // A disallowed class nested inside an otherwise-allowed collection must still be rejected.
        Map<String, Object> map = new HashMap<>();
        map.put("evil", new File("/etc/passwd"));
        byte[] payload = serialize((Serializable) map);
        try {
            ZookeeperDistributedQueue.deserialize(payload, filter);
            fail("Expected deserialization of a non-allowlisted nested class to be rejected.");
        } catch (DistributedBlockingQueue.DistributedQueueException e) {
            assertTrue("Expected the rejection to be caused by an InvalidClassException, but was: " + e.getCause(),
                    e.getCause() instanceof InvalidClassException);
        }
    }
}
