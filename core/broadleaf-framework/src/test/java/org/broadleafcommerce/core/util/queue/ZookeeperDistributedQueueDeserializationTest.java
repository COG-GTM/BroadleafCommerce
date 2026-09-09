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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

public class ZookeeperDistributedQueueDeserializationTest extends TestCase {

    private static Object roundTrip(Serializable obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            ois.setObjectInputFilter(ObjectInputFilter.Config.createFilter(ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN));
            return ois.readObject();
        }
    }

    public void testAllowsQueueSizeInteger() throws Exception {
        assertEquals(500, roundTrip(500));
    }

    public void testAllowsSolrUpdateCommand() throws Exception {
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "1");
        List<SolrInputDocument> docs = new ArrayList<>(Arrays.asList(doc));
        IncrementalUpdateCommand cmd = new IncrementalUpdateCommand(docs, new ArrayList<>(Arrays.asList("id:2")));

        IncrementalUpdateCommand result = (IncrementalUpdateCommand) roundTrip(cmd);

        assertEquals(1, result.getSolrInputDocuments().size());
        assertEquals("1", result.getSolrInputDocuments().get(0).getFieldValue("id"));
        assertEquals(Arrays.asList("id:2"), result.getDeleteQueries());
    }

    public void testRejectsNonAllowlistedClass() throws Exception {
        try {
            roundTrip(new java.util.concurrent.atomic.AtomicReference<>("x"));
            fail("Expected deserialization of a non-allowlisted class to be rejected");
        } catch (InvalidClassException expected) {
            assertTrue(expected.getMessage().contains("filter status: REJECTED"));
        }
    }

    public void testRejectsNonAllowlistedClassNestedInAllowlistedCollection() throws Exception {
        ArrayList<Object> list = new ArrayList<>();
        list.add(new java.util.concurrent.atomic.AtomicReference<>("x"));
        try {
            roundTrip(list);
            fail("Expected deserialization of a non-allowlisted nested class to be rejected");
        } catch (InvalidClassException expected) {
            assertTrue(expected.getMessage().contains("filter status: REJECTED"));
        }
    }

}
