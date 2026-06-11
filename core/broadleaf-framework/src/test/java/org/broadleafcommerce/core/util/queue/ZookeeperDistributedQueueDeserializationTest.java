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

import org.broadleafcommerce.core.search.service.solr.indexer.SiteReindexCommand;
import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputFilter;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that {@link ZookeeperDistributedQueue#deserialize(byte[], ObjectInputFilter)} only deserializes classes that
 * are part of the allowlist, mitigating the insecure deserialization vulnerability (CWE-502).
 *
 * @author Devin
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private static final ObjectInputFilter FILTER =
            ObjectInputFilter.Config.createFilter(ZookeeperDistributedQueue.DESERIALIZATION_ALLOWLIST_PATTERN);

    private static byte[] serialize(Serializable obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    @Test
    public void testAllowedJdkValueTypesAreDeserialized() throws Exception {
        Object capacity = ZookeeperDistributedQueue.deserialize(serialize(Integer.valueOf(500)), FILTER);
        assertEquals(Integer.valueOf(500), capacity);

        Object text = ZookeeperDistributedQueue.deserialize(serialize("queue-entry"), FILTER);
        assertEquals("queue-entry", text);
    }

    @Test
    public void testAllowedCollectionIsDeserialized() throws Exception {
        ArrayList<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        Object result = ZookeeperDistributedQueue.deserialize(serialize(list), FILTER);
        assertEquals(list, result);
    }

    @Test
    public void testAllowedBroadleafCommandIsDeserialized() throws Exception {
        SiteReindexCommand command = new SiteReindexCommand(1L);
        Object result = ZookeeperDistributedQueue.deserialize(serialize(command), FILTER);
        assertEquals(SiteReindexCommand.class, result.getClass());
        assertEquals(Long.valueOf(1L), ((SiteReindexCommand) result).getSiteId());
    }

    @Test
    public void testDisallowedClassIsRejected() throws Exception {
        byte[] payload = serialize(new File("/etc/passwd"));
        try {
            ZookeeperDistributedQueue.deserialize(payload, FILTER);
            fail("Expected deserialization of a non-allowlisted class to be rejected.");
        } catch (DistributedQueueException e) {
            // expected: the filter rejects java.io.File, which is not on the allowlist
        }
    }

    @Test
    public void testDisallowedClassNestedInAllowedCollectionIsRejected() throws Exception {
        List<Object> list = new ArrayList<>();
        list.add(new File("/etc/passwd"));
        byte[] payload = serialize((Serializable) list);
        try {
            ZookeeperDistributedQueue.deserialize(payload, FILTER);
            fail("Expected deserialization of a disallowed nested class to be rejected.");
        } catch (DistributedQueueException e) {
            // expected: even though the outer collection is allowed, the nested File is rejected
        }
    }
}
