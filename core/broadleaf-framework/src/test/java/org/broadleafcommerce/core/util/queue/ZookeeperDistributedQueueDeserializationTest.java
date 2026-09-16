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
import static org.junit.Assert.fail;

import org.apache.solr.common.SolrInputDocument;
import org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand;
import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;

public class ZookeeperDistributedQueueDeserializationTest {

    private static final ObjectInputFilter FILTER =
            ObjectInputFilter.Config.createFilter(ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER);

    @Test
    public void testAllowedTypesAreDeserialized() {
        assertEquals(Integer.valueOf(500), ZookeeperDistributedQueue.deserialize(serialize(500), FILTER));
        assertEquals("queue entry", ZookeeperDistributedQueue.deserialize(serialize("queue entry"), FILTER));
    }

    @Test
    public void testSolrUpdateCommandIsDeserialized() {
        final SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "product:1");
        final IncrementalUpdateCommand command =
                new IncrementalUpdateCommand(Collections.singletonList(doc), Collections.singletonList("id:2"));

        final Object deserialized = ZookeeperDistributedQueue.deserialize(serialize(command), FILTER);

        assertEquals(IncrementalUpdateCommand.class, deserialized.getClass());
        assertEquals(Collections.singletonList("id:2"), ((IncrementalUpdateCommand) deserialized).getDeleteQueries());
    }

    @Test
    public void testDisallowedTypeIsRejected() {
        try {
            ZookeeperDistributedQueue.deserialize(serialize(new File("/tmp/not-a-queue-entry")), FILTER);
            fail("Expected the deserialization filter to reject " + File.class.getName());
        } catch (DistributedQueueException e) {
            assertEquals(InvalidClassException.class, e.getCause().getClass());
        }
    }

    private byte[] serialize(Serializable obj) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
