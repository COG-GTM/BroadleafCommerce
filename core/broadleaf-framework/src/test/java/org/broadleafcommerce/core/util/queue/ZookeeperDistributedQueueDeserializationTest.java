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
import org.broadleafcommerce.core.search.service.solr.indexer.FullReindexCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.SolrUpdateCommand;
import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.InvalidClassException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZookeeperDistributedQueueDeserializationTest {

    private ZookeeperDistributedQueue<SolrUpdateCommand> queue;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        queue = EasyMock.partialMockBuilder(ZookeeperDistributedQueue.class).createMock();
    }

    @Test
    public void allowsMaxCapacityConfigValue() {
        Assert.assertEquals(500, queue.deserialize(queue.serialize(500)));
    }

    @Test
    public void allowsSolrUpdateCommands() {
        Assert.assertSame(FullReindexCommand.class, queue.deserialize(queue.serialize(new FullReindexCommand())).getClass());
        CatalogReindexCommand catalog = (CatalogReindexCommand) queue.deserialize(queue.serialize(new CatalogReindexCommand(42L)));
        Assert.assertEquals(Long.valueOf(42L), catalog.getCatalogId());

        SolrInputDocument child = new SolrInputDocument();
        child.addField("id", "sku-1");
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "product-1");
        doc.addField("price", new BigDecimal("19.99"));
        doc.addField("created", new Date());
        doc.addField("tags", new ArrayList<>(List.of("a", "b")));
        doc.addChildDocument(child);

        IncrementalUpdateCommand result = (IncrementalUpdateCommand) queue.deserialize(queue.serialize(
                new IncrementalUpdateCommand(new ArrayList<>(List.of(doc)), new ArrayList<>(List.of("id:product-2")))));

        Assert.assertEquals(List.of("id:product-2"), result.getDeleteQueries());
        SolrInputDocument resultDoc = result.getSolrInputDocuments().get(0);
        Assert.assertEquals("product-1", resultDoc.getFieldValue("id"));
        Assert.assertEquals(new BigDecimal("19.99"), resultDoc.getFieldValue("price"));
        Assert.assertEquals("sku-1", resultDoc.getChildDocuments().get(0).getFieldValue("id"));
    }

    @Test
    public void rejectsDisallowedTopLevelClass() throws Exception {
        assertRejected(queue, new URL("http://example.com"));
    }

    @Test
    public void rejectsDisallowedClassNestedInAllowedCollection() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("gadget", new URL("http://example.com"));
        assertRejected(queue, (Serializable) map);
    }

    @Test
    public void rejectsUnlistedCustomType() {
        assertRejected(queue, new CustomMessage("hello"));
    }

    @Test
    public void allowsCustomTypeWhenSubclassAddsPattern() {
        ZookeeperDistributedQueue<CustomMessage> customQueue = EasyMock.partialMockBuilder(CustomTypeQueue.class).createMock();
        CustomMessage result = (CustomMessage) customQueue.deserialize(customQueue.serialize(new CustomMessage("hello")));
        Assert.assertEquals("hello", result.text);
    }

    @Test
    public void rejectsExcessiveNesting() {
        List<Object> nested = new ArrayList<>();
        List<Object> current = nested;
        for (int i = 0; i < 100; i++) {
            List<Object> next = new ArrayList<>();
            current.add(next);
            current = next;
        }
        assertRejected(queue, (Serializable) nested);
    }

    private static void assertRejected(ZookeeperDistributedQueue<?> queue, Serializable payload) {
        byte[] bytes = queue.serialize(payload);
        try {
            queue.deserialize(bytes);
            Assert.fail("Expected deserialization of " + payload.getClass().getName() + " to be rejected");
        } catch (DistributedQueueException e) {
            Assert.assertTrue(e.getCause() instanceof InvalidClassException);
        }
    }

    public static class CustomMessage implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String text;

        public CustomMessage(String text) {
            this.text = text;
        }
    }

    public static class CustomTypeQueue extends ZookeeperDistributedQueue<CustomMessage> {
        public CustomTypeQueue() {
            super(null, null);
        }

        @Override
        protected List<String> getAdditionalAllowedClassPatterns() {
            return List.of(CustomMessage.class.getName());
        }
    }
}
