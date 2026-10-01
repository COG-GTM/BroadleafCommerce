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
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ZookeeperDistributedQueueDeserializationTest {

    private ZookeeperDistributedQueue<SolrUpdateCommand> queue;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        queue = EasyMock.partialMockBuilder(ZookeeperDistributedQueue.class).createMock();
    }

    @Test
    public void testAllowsMaxCapacityConfig() {
        Assert.assertEquals(500, queue.deserialize(queue.serialize(500)));
    }

    @Test
    public void testAllowsSolrUpdateCommands() {
        Assert.assertEquals(FullReindexCommand.class, queue.deserialize(queue.serialize(new FullReindexCommand())).getClass());
        Assert.assertEquals(new CatalogReindexCommand(42L), queue.deserialize(queue.serialize(new CatalogReindexCommand(42L))));

        SolrInputDocument child = new SolrInputDocument();
        child.addField("id", "child-1");
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "product-1");
        doc.addField("price", 19.99d);
        doc.addField("created", new Date());
        doc.addField("tags", new ArrayList<>(List.of("a", "b")));
        doc.addChildDocument(child);

        IncrementalUpdateCommand command = new IncrementalUpdateCommand(
                Collections.singletonList(doc), new ArrayList<>(List.of("id:product-2")));
        IncrementalUpdateCommand result = (IncrementalUpdateCommand) queue.deserialize(queue.serialize(command));

        Assert.assertEquals(List.of("id:product-2"), result.getDeleteQueries());
        SolrInputDocument resultDoc = result.getSolrInputDocuments().get(0);
        Assert.assertEquals("product-1", resultDoc.getFieldValue("id"));
        Assert.assertEquals(19.99d, resultDoc.getFieldValue("price"));
        Assert.assertEquals("child-1", resultDoc.getChildDocuments().get(0).getFieldValue("id"));
    }

    @Test
    public void testRejectsDisallowedTopLevelClass() throws Exception {
        assertRejected(new URL("http://example.com"));
    }

    @Test
    public void testRejectsDisallowedNestedClass() throws Exception {
        ArrayList<Object> list = new ArrayList<>();
        list.add(new URL("http://example.com"));
        assertRejected(list);
    }

    private void assertRejected(Serializable payload) {
        byte[] bytes = queue.serialize(payload);
        try {
            queue.deserialize(bytes);
            Assert.fail("Expected deserialization of " + payload.getClass().getName() + " to be rejected");
        } catch (DistributedQueueException e) {
            Assert.assertTrue(e.getCause() instanceof InvalidClassException);
        }
    }
}
