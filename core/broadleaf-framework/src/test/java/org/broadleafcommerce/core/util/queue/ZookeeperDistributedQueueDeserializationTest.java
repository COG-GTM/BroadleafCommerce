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
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InvalidClassException;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;

public class ZookeeperDistributedQueueDeserializationTest {

    @Test
    public void shouldDeserializeAllowedJdkTypes() throws Exception {
        assertDeserializesEquals(Integer.valueOf(42));
        assertDeserializesEquals("queue entry");

        ArrayList<String> list = new ArrayList<>();
        list.add("first");
        list.add("second");
        assertDeserializesEquals(list);

        LinkedHashMap<String, Long> map = new LinkedHashMap<>();
        map.put("one", 1L);
        map.put("two", 2L);
        assertDeserializesEquals(map);
    }

    @Test
    public void shouldDeserializeAllowedBroadleafType() throws Exception {
        assertDeserializesEquals(new TestQueueEntry("queue entry"));
    }

    @Test
    public void shouldDeserializeAllowedSolrType() throws Exception {
        SolrInputDocument document = new SolrInputDocument();
        document.addField("id", "queue-entry");
        SolrInputDocument deserialized = (SolrInputDocument) deserialize(serialize(document));
        assertEquals(document.toString(), deserialized.toString());
        assertEquals(document.getFieldValue("id"), deserialized.getFieldValue("id"));
    }

    @Test(expected = InvalidClassException.class)
    public void shouldRejectDisallowedType() throws Exception {
        deserialize(serialize(new File("queue-entry")));
    }

    private void assertDeserializesEquals(Serializable value) throws Exception {
        assertEquals(value, deserialize(serialize(value)));
    }

    private Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            input.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
                    ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN));
            return input.readObject();
        }
    }

    private byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static class TestQueueEntry implements Serializable {

        private final String value;

        private TestQueueEntry(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TestQueueEntry)) {
                return false;
            }
            TestQueueEntry that = (TestQueueEntry) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
