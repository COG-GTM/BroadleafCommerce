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
import org.broadleafcommerce.core.search.service.solr.indexer.CatalogReindexCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.DefaultSolrIndexQueueProvider;
import org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand;
import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZookeeperDistributedQueueDeserializationTest {

    private static final ObjectInputFilter DEFAULT_FILTER = ZookeeperDistributedQueue.createDeserializationFilter(null);

    private static final ObjectInputFilter SOLR_FILTER = ZookeeperDistributedQueue.createDeserializationFilter(
            DefaultSolrIndexQueueProvider.DISTRIBUTED_QUEUE_ALLOWED_CLASS_PATTERNS);

    @Test
    public void allowsDefaultJdkTypes() throws Exception {
        Assert.assertEquals(500, ZookeeperDistributedQueue.deserialize(serialize(500), DEFAULT_FILTER));

        List<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        Assert.assertEquals(list, ZookeeperDistributedQueue.deserialize(serialize((Serializable) list), DEFAULT_FILTER));

        HashMap<String, Long> map = new HashMap<>();
        map.put("k", 1L);
        Assert.assertEquals(map, ZookeeperDistributedQueue.deserialize(serialize(map), DEFAULT_FILTER));
    }

    @Test
    public void allowsSolrUpdateCommandsWithSolrPatterns() throws Exception {
        SolrInputDocument doc = new SolrInputDocument();
        doc.setField("id", "product_1");
        doc.addField("category", 10L);
        doc.addField("category", 11L);
        IncrementalUpdateCommand command = new IncrementalUpdateCommand(
                Collections.singletonList(doc), Collections.singletonList("id:product_2"));

        Object result = ZookeeperDistributedQueue.deserialize(serialize(command), SOLR_FILTER);

        Assert.assertTrue(result instanceof IncrementalUpdateCommand);
        IncrementalUpdateCommand read = (IncrementalUpdateCommand) result;
        Assert.assertEquals("product_1", read.getSolrInputDocuments().get(0).getFieldValue("id"));
        Assert.assertEquals(2, read.getSolrInputDocuments().get(0).getFieldValues("category").size());
        Assert.assertEquals("id:product_2", read.getDeleteQueries().get(0));

        Assert.assertTrue(ZookeeperDistributedQueue.deserialize(serialize(new CatalogReindexCommand(1L)), SOLR_FILTER)
                instanceof CatalogReindexCommand);
    }

    @Test
    public void rejectsSolrUpdateCommandsWithoutSolrPatterns() throws Exception {
        assertRejected(serialize(new CatalogReindexCommand(1L)), DEFAULT_FILTER);
    }

    @Test
    public void rejectsClassesOutsideAllowList() throws Exception {
        assertRejected(serialize(new NotAllowed()), SOLR_FILTER);

        Map<URL, String> urlDnsStyleGraph = new HashMap<>();
        urlDnsStyleGraph.put(new URL("http://example.invalid"), "x");
        assertRejected(serialize((Serializable) urlDnsStyleGraph), SOLR_FILTER);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativePatterns() {
        ZookeeperDistributedQueue.createDeserializationFilter(Collections.singletonList("!java.lang.String"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsLimitPatterns() {
        ZookeeperDistributedQueue.createDeserializationFilter(Collections.singletonList("maxdepth=1000"));
    }

    private static void assertRejected(byte[] bytes, ObjectInputFilter filter) {
        try {
            ZookeeperDistributedQueue.deserialize(bytes, filter);
            Assert.fail("Expected deserialization to be rejected");
        } catch (DistributedQueueException e) {
            Assert.assertTrue(e.getCause() instanceof InvalidClassException);
        }
    }

    private static byte[] serialize(Serializable obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    private static class NotAllowed implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
