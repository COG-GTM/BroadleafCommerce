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

import junit.framework.TestCase;

import org.broadleafcommerce.core.search.service.solr.indexer.SiteReindexCommand;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ZookeeperDistributedQueueDeserializationTest extends TestCase {

    private final ObjectInputFilter filter =
            ObjectInputFilter.Config.createFilter(ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN);

    public void testAllowsQueueEntryTypes() throws Exception {
        assertEquals(Integer.valueOf(500), deserialize(serialize(500)));
        assertEquals("some-entry", deserialize(serialize("some-entry")));

        List<String> queries = new ArrayList<>();
        queries.add("id:1");
        assertEquals(queries, deserialize(serialize((Serializable) queries)));

        assertEquals(Long.valueOf(1L), ((SiteReindexCommand) deserialize(serialize(new SiteReindexCommand(1L)))).getSiteId());
    }

    public void testRejectsUnlistedTypes() throws Exception {
        byte[] payload = serialize(new UnlistedPayload());
        try {
            deserialize(payload);
            fail("Expected the filter to reject a class that is not on the allow list.");
        } catch (java.io.InvalidClassException e) {
            //Expected
        }
    }

    private byte[] serialize(Serializable obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    private Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new java.io.ByteArrayInputStream(bytes))) {
            ois.setObjectInputFilter(filter);
            return ois.readObject();
        }
    }

    private static class UnlistedPayload implements Serializable {

        private static final long serialVersionUID = 1L;

    }

}
