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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.apache.solr.common.SolrInputDocument;
import org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand;
import org.broadleafcommerce.core.util.queue.ZookeeperDistributedQueue.AllowListObjectInputStream;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that {@link AllowListObjectInputStream} (used by
 * {@link ZookeeperDistributedQueue#deserialize(byte[])}) only deserializes allow-listed classes,
 * mitigating insecure deserialization (CWE-502) of untrusted data read from Zookeeper.
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private static byte[] serialize(Serializable obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    private static Object deserializeWithAllowList(byte[] bytes) throws Exception {
        try (ObjectInputStream ois = new AllowListObjectInputStream(
                new ByteArrayInputStream(bytes),
                ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_ALLOW_LIST)) {
            return ois.readObject();
        }
    }

    @Test
    public void testAllowedJdkTypesDeserialize() throws Exception {
        assertEquals(Integer.valueOf(42), deserializeWithAllowList(serialize(Integer.valueOf(42))));
        assertEquals("hello", deserializeWithAllowList(serialize("hello")));

        List<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        assertEquals(list, deserializeWithAllowList(serialize((Serializable) list)));
    }

    @Test
    public void testAllowedArrayTypeDeserializes() throws Exception {
        String[] array = {"x", "y", "z"};
        Object result = deserializeWithAllowList(serialize(array));
        assertArrayEquals(array, (String[]) result);
    }

    @Test
    public void testAllowedSolrCommandDeserializes() throws Exception {
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "1");
        IncrementalUpdateCommand command =
                new IncrementalUpdateCommand(List.of(doc), List.of("delete:1"));

        Object result = deserializeWithAllowList(serialize(command));
        assertEquals(IncrementalUpdateCommand.class, result.getClass());
        assertEquals(1, ((IncrementalUpdateCommand) result).getSolrInputDocuments().size());
    }

    @Test
    public void testNonAllowListedClassIsRejected() throws Exception {
        // java.io.File is Serializable but lives in a package that is NOT on the allow list.
        byte[] bytes = serialize(new File("/etc/passwd"));
        try {
            deserializeWithAllowList(bytes);
            fail("Expected InvalidClassException for non-allow-listed class java.io.File");
        } catch (InvalidClassException expected) {
            // success: deserialization of a non-allow-listed class was blocked
        }
    }
}
