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
import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

public class ZookeeperDistributedQueueDeserializationTest extends TestCase {

    private static final ObjectInputFilter FILTER =
            ObjectInputFilter.Config.createFilter(ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN);

    public void testAllowsQueueConfigAndElementTypes() throws IOException {
        assertEquals(Integer.valueOf(500), ZookeeperDistributedQueue.deserialize(serialize(500), FILTER));

        final List<String> list = new ArrayList<>();
        list.add("element");
        assertEquals(list, ZookeeperDistributedQueue.deserialize(serialize((Serializable) list), FILTER));

        assertEquals(new FullReindexCommand(),
                ZookeeperDistributedQueue.deserialize(serialize(FullReindexCommand.DEFAULT_INSTANCE), FILTER));
    }

    public void testRejectsTypesOutsideOfTheAllowList() throws IOException {
        final byte[] bytes = serialize(new File("/tmp/some-file"));
        try {
            ZookeeperDistributedQueue.deserialize(bytes, FILTER);
            fail("Expected a DistributedQueueException for a type that is not on the allow list.");
        } catch (DistributedQueueException e) {
            assertTrue(e.getCause() instanceof InvalidClassException);
        }
    }

    private byte[] serialize(Serializable obj) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

}
