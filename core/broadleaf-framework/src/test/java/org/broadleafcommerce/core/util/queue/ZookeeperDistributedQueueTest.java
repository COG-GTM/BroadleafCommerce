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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.solr.common.SolrInputDocument;
import org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates the serialization filter that guards {@link ZookeeperDistributedQueue#deserialize(byte[])} against
 * untrusted data read from Zookeeper.
 */
public class ZookeeperDistributedQueueTest {

    private static final ObjectInputFilter FILTER =
            ObjectInputFilter.Config.createFilter(ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN);

    private static byte[] serialize(Serializable obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    private static Object deserialize(Serializable obj) throws IOException {
        return ZookeeperDistributedQueue.deserialize(serialize(obj), FILTER);
    }

    @Test
    public void testMaxCapacityConfigIsDeserializable() throws IOException {
        assertEquals(Integer.valueOf(500), deserialize(500));
    }

    @Test
    public void testCommonQueueEntryTypesAreDeserializable() throws IOException {
        assertEquals("entry", deserialize("entry"));

        List<String> list = new ArrayList<>(Arrays.asList("one", "two"));
        assertEquals(list, deserialize((Serializable) list));

        Map<String, Integer> map = new HashMap<>();
        map.put("key", 1);
        assertEquals(map, deserialize((Serializable) map));
    }

    @Test
    public void testSolrUpdateCommandIsDeserializable() throws IOException {
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "product-1");

        IncrementalUpdateCommand command =
                new IncrementalUpdateCommand(Arrays.asList(doc), Arrays.asList("id:product-2"));
        Object deserialized = deserialize(command);

        assertTrue(deserialized instanceof IncrementalUpdateCommand);
        assertEquals("product-1",
                ((IncrementalUpdateCommand) deserialized).getSolrInputDocuments().get(0).getFieldValue("id"));
        assertEquals(Arrays.asList("id:product-2"), ((IncrementalUpdateCommand) deserialized).getDeleteQueries());
    }

    @Test
    public void testUnlistedClassesAreRejected() throws IOException {
        try {
            deserialize(new File("/tmp/should-not-deserialize"));
            fail("Expected the serialization filter to reject java.io.File.");
        } catch (DistributedBlockingQueue.DistributedQueueException e) {
            assertTrue(e.getCause() instanceof InvalidClassException);
        }
    }
}
