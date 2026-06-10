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
import org.broadleafcommerce.core.search.service.solr.indexer.CatalogReindexCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand;
import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.InvalidClassException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Verifies that {@link ZookeeperDistributedQueue#deserialize(byte[])} is hardened against insecure deserialization
 * (CWE-502). Legitimate queue payloads must still round-trip, while classes outside the allowlist must be rejected
 * before they are instantiated.
 *
 * <p>The queue's constructors require a live Zookeeper connection, so the instance under test is created without
 * invoking a constructor (via EasyMock's partial mock builder). The {@code serialize}/{@code deserialize} methods and
 * the deserialization filter do not depend on any instance state, so this faithfully exercises the real code path.</p>
 *
 * @author Devin
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private ZookeeperDistributedQueue<Serializable> queue;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        queue = EasyMock.partialMockBuilder(ZookeeperDistributedQueue.class).createMock();
    }

    @Test
    public void testAllowedSimpleTypesRoundTrip() {
        // The queue serializes an Integer for its max-capacity config value.
        Object result = roundTrip(Integer.valueOf(42));
        assertEquals(Integer.valueOf(42), result);

        assertEquals("hello", roundTrip("hello"));
    }

    @Test
    public void testAllowedReindexCommandRoundTrips() {
        CatalogReindexCommand command = new CatalogReindexCommand(1234L);
        Object result = roundTrip(command);
        assertTrue(result instanceof CatalogReindexCommand);
        assertEquals(Long.valueOf(1234L), ((CatalogReindexCommand) result).getCatalogId());
    }

    @Test
    public void testAllowedIncrementalUpdateCommandWithSolrDocumentRoundTrips() {
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "product:1");
        doc.addField("name", "Test Product");

        IncrementalUpdateCommand command = new IncrementalUpdateCommand(
                new ArrayList<>(Arrays.asList(doc)),
                new ArrayList<>(Arrays.asList("category:deleted")));

        Object result = roundTrip(command);
        assertTrue(result instanceof IncrementalUpdateCommand);

        IncrementalUpdateCommand deserialized = (IncrementalUpdateCommand) result;
        assertEquals(1, deserialized.getSolrInputDocuments().size());
        assertEquals("product:1", deserialized.getSolrInputDocuments().get(0).getFieldValue("id"));
        assertEquals(Arrays.asList("category:deleted"), deserialized.getDeleteQueries());
    }

    @Test
    public void testDisallowedTopLevelClassIsRejected() {
        // java.io.File is Serializable but is NOT on the deserialization allowlist.
        byte[] payload = queue.serialize(new File("/etc/passwd"));
        assertRejected(payload);
    }

    @Test
    public void testDisallowedNestedClassIsRejected() {
        // A disallowed class hidden inside an otherwise-allowed container must still be rejected.
        ArrayList<Serializable> payload = new ArrayList<>();
        payload.add("safe");
        payload.add(new File("/etc/passwd"));
        byte[] bytes = queue.serialize(payload);
        assertRejected(bytes);
    }

    private Object roundTrip(Serializable obj) {
        byte[] bytes = queue.serialize(obj);
        return queue.deserialize(bytes);
    }

    private void assertRejected(byte[] payload) {
        try {
            queue.deserialize(payload);
            fail("Expected deserialization of a non-allowlisted class to be rejected.");
        } catch (DistributedQueueException e) {
            Throwable cause = e.getCause();
            assertTrue("Expected an InvalidClassException from the ObjectInputFilter but got: " + cause,
                    cause instanceof InvalidClassException);
        }
    }
}
