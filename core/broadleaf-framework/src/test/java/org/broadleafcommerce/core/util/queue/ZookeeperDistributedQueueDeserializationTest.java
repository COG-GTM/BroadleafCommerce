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

import org.apache.solr.common.SolrInputDocument;
import org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand;
import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

public class ZookeeperDistributedQueueDeserializationTest extends TestCase {

    private static final ObjectInputFilter FILTER =
            ObjectInputFilter.Config.createFilter(ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN);

    private static byte[] serialize(Serializable obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    public void testAllowsQueueCapacityAndCommandPayloads() throws Exception {
        assertEquals(Integer.valueOf(500), ZookeeperDistributedQueue.deserialize(serialize(500), FILTER));

        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "product-1");
        doc.addField("price", 12.5d);
        List<SolrInputDocument> docs = new ArrayList<>();
        docs.add(doc);
        IncrementalUpdateCommand command = new IncrementalUpdateCommand(docs, Arrays.asList("id:product-2"));

        Object deserialized = ZookeeperDistributedQueue.deserialize(serialize(command), FILTER);
        assertTrue(deserialized instanceof IncrementalUpdateCommand);
        assertEquals("product-1",
                ((IncrementalUpdateCommand) deserialized).getSolrInputDocuments().get(0).getFieldValue("id"));
        assertEquals(Arrays.asList("id:product-2"), ((IncrementalUpdateCommand) deserialized).getDeleteQueries());
    }

    public void testAllowsPrimitiveArrays() throws Exception {
        byte[] deserialized = (byte[]) ZookeeperDistributedQueue.deserialize(serialize(new byte[] {1, 2, 3}), FILTER);
        assertTrue(Arrays.equals(new byte[] {1, 2, 3}, deserialized));
    }

    public void testRejectsClassesOutsideOfTheAllowList() throws Exception {
        byte[] bytes = serialize(new File("/tmp/unexpected"));
        try {
            ZookeeperDistributedQueue.deserialize(bytes, FILTER);
            fail("Expected a DistributedQueueException for a class that is not on the allow list.");
        } catch (DistributedQueueException e) {
            assertTrue(e.getCause() instanceof InvalidClassException);
        }
    }

    public void testRejectsNestedClassesOutsideOfTheAllowList() throws Exception {
        List<Object> payload = new ArrayList<>();
        payload.add(new File("/tmp/unexpected"));
        byte[] bytes = serialize((Serializable) payload);
        try {
            ZookeeperDistributedQueue.deserialize(bytes, FILTER);
            fail("Expected a DistributedQueueException for a nested class that is not on the allow list.");
        } catch (DistributedQueueException e) {
            assertTrue(e.getCause() instanceof InvalidClassException);
        }
    }

}
