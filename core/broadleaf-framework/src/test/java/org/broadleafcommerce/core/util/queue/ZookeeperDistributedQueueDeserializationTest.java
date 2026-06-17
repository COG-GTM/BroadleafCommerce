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

import org.broadleafcommerce.core.search.service.solr.indexer.CatalogReindexCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.FullReindexCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.SiteReindexCommand;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that {@link ZookeeperDistributedQueue#deserialize(byte[], java.io.ObjectInputFilter)} only deserializes
 * the allowlisted classes that the queue is expected to carry, and rejects everything else. This guards against the
 * insecure-deserialization (CWE-502) vector where arbitrary/gadget classes embedded in Zookeeper data could be
 * instantiated during {@link java.io.ObjectInputStream#readObject()}.
 *
 * @author Broadleaf Commerce
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private static byte[] serialize(Serializable obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    private static Object roundTrip(Serializable obj) throws Exception {
        return ZookeeperDistributedQueue.deserialize(serialize(obj),
                ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER);
    }

    @Test
    public void testAllowsQueueConfigType() throws Exception {
        assertEquals(Integer.valueOf(500), roundTrip(Integer.valueOf(500)));
    }

    @Test
    public void testAllowsSolrUpdateCommandTypes() throws Exception {
        assertEquals(new CatalogReindexCommand(42L), roundTrip(new CatalogReindexCommand(42L)));
        assertEquals(new SiteReindexCommand(7L), roundTrip(new SiteReindexCommand(7L)));
        assertEquals(FullReindexCommand.DEFAULT_INSTANCE, roundTrip(new FullReindexCommand()));
    }

    @Test
    public void testAllowsCommonCollectionTypes() throws Exception {
        List<String> payload = new ArrayList<>();
        payload.add("a");
        payload.add("b");
        assertEquals(payload, roundTrip(new ArrayList<>(payload)));
    }

    @Test
    public void testRejectsNonAllowlistedClass() throws Exception {
        // java.io.File is Serializable but is not on the allowlist; it stands in for an arbitrary/gadget class.
        try {
            ZookeeperDistributedQueue.deserialize(serialize(new File("/tmp/evil")),
                    ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER);
            fail("Expected deserialization of a non-allowlisted class to be rejected.");
        } catch (DistributedBlockingQueue.DistributedQueueException e) {
            assertTrue("Expected the rejection to originate from the input filter (InvalidClassException), but was: "
                    + e.getCause(), e.getCause() instanceof InvalidClassException);
        }
    }
}
