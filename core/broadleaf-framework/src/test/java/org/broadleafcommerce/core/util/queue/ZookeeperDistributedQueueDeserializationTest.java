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

import org.broadleafcommerce.core.search.service.solr.indexer.SiteReindexCommand;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ZookeeperDistributedQueueDeserializationTest {

    @Test
    public void shouldRoundTripAllowedJdkPayloads() throws IOException {
        assertEquals(Integer.valueOf(500), deserialize(Integer.valueOf(500)));

        List<Long> values = new ArrayList<>(Arrays.asList(1L, 2L, 3L));
        assertEquals(values, deserialize(values));

        assertEquals("queue element", deserialize("queue element"));
    }

    @Test
    public void shouldRejectDisallowedClass() throws IOException {
        try {
            deserialize(new File("/tmp/x"));
            fail("Expected a DistributedQueueException");
        } catch (DistributedBlockingQueue.DistributedQueueException e) {
            assertEquals(java.io.InvalidClassException.class, e.getCause().getClass());
        }

        try {
            deserialize(new ArrayList<>(Arrays.asList(new File("/tmp/x"))));
            fail("Expected a DistributedQueueException");
        } catch (DistributedBlockingQueue.DistributedQueueException e) {
            assertEquals(java.io.InvalidClassException.class, e.getCause().getClass());
        }
    }

    @Test
    public void shouldAcceptBroadleafSerializablePayload() throws IOException {
        SiteReindexCommand command = new SiteReindexCommand(42L);

        assertEquals(command, deserialize(command));
    }

    private static Object deserialize(Object value) throws IOException {
        return ZookeeperDistributedQueue.deserialize(serialize(value),
                ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER);
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(output)) {
            stream.writeObject(value);
        }
        return output.toByteArray();
    }
}
