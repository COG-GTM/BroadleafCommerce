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
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.broadleafcommerce.core.search.service.solr.indexer.CatalogReindexCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.FullReindexCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.SiteReindexCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.SolrUpdateCommand;
import org.broadleafcommerce.core.util.lock.DistributedLock;
import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;
import org.broadleafcommerce.core.util.queue.ZookeeperDistributedQueue.RejectedQueueEntryException;
import org.easymock.EasyMock;
import org.example.gadget.Gadget;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ZookeeperDistributedQueueDeserializationTest {

    private static final String ENTRY_FOLDER = "/test-queue/elements";

    private ZookeeperDistributedQueue<Serializable> queue;
    private ZooKeeper zk;
    private DistributedLock lock;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        Gadget.triggered = false;
        zk = EasyMock.createMock(ZooKeeper.class);
        lock = EasyMock.createNiceMock(DistributedLock.class);
        EasyMock.expect(lock.tryLock()).andReturn(true).anyTimes();
        EasyMock.expect(lock.tryLock(EasyMock.anyLong(), EasyMock.anyObject(TimeUnit.class))).andReturn(true).anyTimes();
        EasyMock.replay(lock);

        // Skips the constructor so no ZooKeeper connection or folder setup is needed.
        queue = EasyMock.partialMockBuilder(ZookeeperDistributedQueue.class)
                .addMockedMethods("getZookeeperClient", "getQueueAccessLock", "getQueueEntryFolder", "getQueueFolderPath")
                .createMock();
        EasyMock.expect(queue.getZookeeperClient()).andReturn(zk).anyTimes();
        EasyMock.expect(queue.getQueueAccessLock()).andReturn(lock).anyTimes();
        EasyMock.expect(queue.getQueueEntryFolder()).andReturn(ENTRY_FOLDER).anyTimes();
        EasyMock.expect(queue.getQueueFolderPath()).andReturn("/test-queue").anyTimes();
        EasyMock.replay(queue);

        Field monitor = ZookeeperDistributedQueue.class.getDeclaredField("QUEUE_MONITOR");
        monitor.setAccessible(true);
        monitor.set(queue, new Object());
    }

    @Test
    public void roundTripsJdkTypes() {
        assertEquals(42, queue.deserialize(serialize(42)));
        assertEquals("hello", queue.deserialize(serialize("hello")));
        assertEquals(new BigDecimal("12.50"), queue.deserialize(serialize(new BigDecimal("12.50"))));
        HashMap<String, Object> map = new HashMap<>();
        map.put("list", new ArrayList<>(Arrays.asList(1L, "two", new Date(0L))));
        assertEquals(map, queue.deserialize(serialize(map)));
    }

    @Test
    public void roundTripsSolrUpdateCommands() {
        assertTrue(queue.deserialize(serialize(new FullReindexCommand())) instanceof FullReindexCommand);
        assertEquals(new CatalogReindexCommand(7L), queue.deserialize(serialize(new CatalogReindexCommand(7L))));
        assertEquals(new SiteReindexCommand(3L), queue.deserialize(serialize(new SiteReindexCommand(3L))));

        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "product-1");
        doc.addField("price", new BigDecimal("9.99"));
        doc.addField("tags", Arrays.asList("a", "b"));
        doc.addField("created", new Date(1000L));
        SolrInputDocument child = new SolrInputDocument();
        child.addField("id", "sku-1");
        doc.addChildDocument(child);
        IncrementalUpdateCommand cmd = new IncrementalUpdateCommand(
                new ArrayList<>(Collections.singletonList(doc)), new ArrayList<>(Collections.singletonList("id:old")));

        IncrementalUpdateCommand read = (IncrementalUpdateCommand) queue.deserialize(serialize(cmd));
        SolrInputDocument readDoc = read.getSolrInputDocuments().get(0);
        assertEquals("product-1", readDoc.getFieldValue("id"));
        assertEquals(new BigDecimal("9.99"), readDoc.getFieldValue("price"));
        assertEquals("sku-1", readDoc.getChildDocuments().get(0).getFieldValue("id"));
        assertEquals(Collections.singletonList("id:old"), read.getDeleteQueries());
    }

    @Test
    public void acceptsImmutableCollectionFactoriesIncludingIndexUpdates() {
        assertEquals(List.of(), queue.deserialize(serialize((Serializable) List.of())));
        assertEquals(List.of(1, "two", 3L), queue.deserialize(serialize((Serializable) List.of(1, "two", 3L))));
        assertEquals(Set.of("a"), queue.deserialize(serialize((Serializable) Set.of("a"))));
        assertEquals(Set.of("a", "b", "c"), queue.deserialize(serialize((Serializable) Set.of("a", "b", "c"))));
        assertEquals(Map.of("k", 1), queue.deserialize(serialize((Serializable) Map.of("k", 1))));
        assertEquals(Map.of("k", 1, "j", 2), queue.deserialize(serialize((Serializable) Map.of("k", 1, "j", 2))));

        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "product-1");
        IncrementalUpdateCommand cmd = new IncrementalUpdateCommand(List.of(doc), List.of("id:old"));
        byte[] data = queue.serializeForQueue(cmd);
        IncrementalUpdateCommand read = (IncrementalUpdateCommand) queue.deserialize(data);
        assertEquals("product-1", read.getSolrInputDocuments().get(0).getFieldValue("id"));
        assertEquals(List.of("id:old"), read.getDeleteQueries());
    }

    @Test
    public void rejectsGadgetInsideImmutableCollection() {
        assertRejected(serialize((Serializable) List.of(new Gadget())), Gadget.class.getName());
        assertRejected(serialize((Serializable) Map.of("k", new Gadget())), Gadget.class.getName());
    }

    @Test
    public void rejectsGadgetWithoutRunningIt() {
        assertRejected(serialize(new Gadget()), Gadget.class.getName());
    }

    @Test
    public void rejectsGadgetNestedInAllowedCollection() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("payload", new Gadget());
        assertRejected(serialize(map), Gadget.class.getName());
    }

    @Test
    public void rejectsNonAllowlistedApplicationClass() {
        assertRejected(serialize(new NotAllowed()), NotAllowed.class.getName());
    }

    @Test
    public void roundTripsAllowlistedCollectionWrappersAndValueTypes() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("unmodifiable", Collections.unmodifiableList(new ArrayList<>(Arrays.asList("a", "b"))));
        map.put("asList", Arrays.asList(1, 2.5d, 3.5f, (short) 4, (byte) 5, 'c', true));
        map.put("empty", Collections.emptyList());
        map.put("singleton", Collections.singletonMap("k", "v"));
        map.put("sorted", new TreeSet<>(Arrays.asList("z", "y")));
        map.put("uuid", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        map.put("locale", Locale.CANADA_FRENCH);
        map.put("localDate", LocalDate.of(2026, 1, 2));
        map.put("timestamp", new Timestamp(0L));
        assertEquals(map, queue.deserialize(serialize(map)));
    }

    @Test
    public void rejectsJdkClassesOutsideTheAllowlist() {
        assertRejected(serialize(new PriorityQueue<>(Arrays.asList(1, 2))), PriorityQueue.class.getName());
        assertRejected(serialize(new StringBuilder("x")), StringBuilder.class.getName());
        assertRejected(serialize(java.util.concurrent.TimeUnit.SECONDS), java.util.concurrent.TimeUnit.class.getName());
    }

    @Test
    public void rejectsPayloadsLargerThanByteLimit() {
        int half = (int) (ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_MAX_BYTES / 2) + 1024;
        // The filter checks the running byte count at each callback, so the third array trips the limit.
        ArrayList<byte[]> chunks = new ArrayList<>(Arrays.asList(new byte[half], new byte[half], new byte[1]));
        assertRejected(serialize(chunks), "bytes=");
    }

    @Test
    public void rejectsGraphsWithTooManyReferences() {
        ZookeeperDistributedQueue<Serializable> limited = EasyMock.partialMockBuilder(ZookeeperDistributedQueue.class)
                .addMockedMethods("getQueueFolderPath", "getDeserializationMaxReferences")
                .createMock();
        EasyMock.expect(limited.getQueueFolderPath()).andReturn("/test-queue").anyTimes();
        EasyMock.expect(limited.getDeserializationMaxReferences()).andReturn(1000L).anyTimes();
        EasyMock.replay(limited);

        ArrayList<Integer> refs = new ArrayList<>(Collections.nCopies(2000, 7));
        try {
            limited.deserialize(serialize(refs));
            fail("Expected RejectedQueueEntryException");
        } catch (RejectedQueueEntryException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("references=1001"));
        }
        assertEquals(refs, queue.deserialize(serialize(refs)));
    }

    @Test
    public void rejectsGraphsDeeperThanLimit() {
        List<Object> root = new ArrayList<>();
        List<Object> current = root;
        for (int i = 0; i < 200; i++) {
            List<Object> next = new ArrayList<>();
            current.add(next);
            current = next;
        }
        assertRejected(serialize((Serializable) root), "depth=" + (ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_MAX_DEPTH + 1));
    }

    @Test
    public void rejectsOversizedArrays() {
        assertRejected(serialize(new byte[ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_MAX_ARRAY_LENGTH + 1]),
                "arrayLength=" + (ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_MAX_ARRAY_LENGTH + 1));
        assertEquals(1024, ((byte[]) queue.deserialize(serialize(new byte[1024]))).length);
    }

    @Test
    public void offerRejectsEntriesConsumersWouldDiscardBeforeTouchingZookeeper() throws Exception {
        EasyMock.replay(zk);
        HashMap<String, Object> map = new HashMap<>();
        map.put("uri", new java.net.URI("https://example.org"));
        for (Serializable entry : Arrays.<Serializable>asList(new Gadget(), map, new NotAllowed())) {
            try {
                queue.offer(entry);
                fail("Expected IllegalArgumentException for " + entry);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getCause() instanceof RejectedQueueEntryException);
            }
        }
        assertFalse(Gadget.triggered);
        EasyMock.verify(zk);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void writeRevalidatesTheSnapshotItWritesInsteadOfTrustingEarlierValidation() throws Exception {
        ZookeeperDistributedQueue<Serializable> q = EasyMock.partialMockBuilder(ZookeeperDistributedQueue.class)
                .addMockedMethods("getZookeeperClient", "getQueueAccessLock", "getQueueEntryFolder", "getQueueFolderPath",
                        "remainingCapacity", "serializeForQueue")
                .createMock();
        CatalogReindexCommand entry = new CatalogReindexCommand(1L);
        EasyMock.expect(q.getZookeeperClient()).andReturn(zk).anyTimes();
        EasyMock.expect(q.getQueueAccessLock()).andReturn(lock).anyTimes();
        EasyMock.expect(q.getQueueEntryFolder()).andReturn(ENTRY_FOLDER).anyTimes();
        EasyMock.expect(q.getQueueFolderPath()).andReturn("/test-queue").anyTimes();
        EasyMock.expect(q.remainingCapacity()).andReturn(1).anyTimes();
        // Valid when first offered, rejected by the time it is written (e.g. mutated while waiting for capacity).
        EasyMock.expect(q.serializeForQueue(entry)).andReturn(serialize(entry));
        EasyMock.expect(q.serializeForQueue(entry)).andThrow(new IllegalArgumentException("changed"));
        EasyMock.replay(q, zk);
        Field monitor = ZookeeperDistributedQueue.class.getDeclaredField("QUEUE_MONITOR");
        monitor.setAccessible(true);
        monitor.set(q, new Object());

        try {
            q.offer(entry);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("changed", e.getMessage());
        }
        EasyMock.verify(q, zk);
    }

    @Test
    public void configValueUsesFixedFilterIndependentOfSubclassAllowlist() {
        ZookeeperDistributedQueue<Serializable> q = EasyMock.partialMockBuilder(ZookeeperDistributedQueue.class)
                .addMockedMethods("getDeserializationAllowlist", "getQueueFolderPath")
                .createMock();
        EasyMock.expect(q.getDeserializationAllowlist()).andThrow(new NullPointerException("subclass field not initialized")).anyTimes();
        EasyMock.expect(q.getQueueFolderPath()).andReturn("/test-queue").anyTimes();
        EasyMock.replay(q);

        assertEquals(42, q.deserializeConfigValue(serialize(42)));
        try {
            q.deserializeConfigValue(serialize(new Gadget()));
            fail("Expected RejectedQueueEntryException");
        } catch (RejectedQueueEntryException e) {
            assertFalse(Gadget.triggered);
        }
        try {
            q.deserializeConfigValue(serialize(new ArrayList<>(Collections.singletonList(42))));
            fail("Expected RejectedQueueEntryException");
        } catch (RejectedQueueEntryException expected) {
        }
    }

    @Test
    public void configValueEncodingIsIndependentOfSerializeOverride() {
        ZookeeperDistributedQueue<Serializable> q = EasyMock.partialMockBuilder(ZookeeperDistributedQueue.class)
                .addMockedMethods("serialize", "deserialize", "getQueueFolderPath")
                .createMock();
        EasyMock.expect(q.getQueueFolderPath()).andReturn("/test-queue").anyTimes();
        // A subclass with a custom wire format; config must not go through it.
        EasyMock.expect(q.serialize(EasyMock.anyObject(Serializable.class))).andReturn(new byte[] {1, 2, 3}).anyTimes();
        EasyMock.replay(q);

        assertEquals(Integer.valueOf(500), q.readConfiguredCapacity(q.serializeConfigValue(500)));
        EasyMock.verify(q);
    }

    @Test
    public void legacyCustomFormatConfigFallsBackToQueueDeserializer() {
        byte[] custom = new byte[] {1, 2, 3};
        ZookeeperDistributedQueue<Serializable> q = EasyMock.partialMockBuilder(ZookeeperDistributedQueue.class)
                .addMockedMethods("deserialize", "getQueueFolderPath")
                .createMock();
        EasyMock.expect(q.getQueueFolderPath()).andReturn("/test-queue").anyTimes();
        EasyMock.expect(q.deserialize(custom)).andReturn(500);
        EasyMock.replay(q);

        assertEquals(Integer.valueOf(500), q.readConfiguredCapacity(custom));
        EasyMock.verify(q);
    }

    @Test
    public void malformedJavaStreamConfigIsNeverPassedToQueueDeserializer() {
        byte[] full = serialize(500);
        byte[] truncated = Arrays.copyOf(full, full.length - 2);
        ZookeeperDistributedQueue<Serializable> q = EasyMock.partialMockBuilder(ZookeeperDistributedQueue.class)
                .addMockedMethods("deserialize", "getQueueFolderPath")
                .createMock();
        EasyMock.expect(q.getQueueFolderPath()).andReturn("/test-queue").anyTimes();
        // No deserialize expectation: an (unfiltered) override must not be handed a Java object stream.
        EasyMock.replay(q);

        try {
            q.readConfiguredCapacity(truncated);
            fail("Expected DistributedQueueException");
        } catch (RejectedQueueEntryException e) {
            fail("Truncated data must not be treated as a filter rejection");
        } catch (DistributedQueueException expected) {
        }
        EasyMock.verify(q);
    }

    @Test
    public void corruptDataIsNotReportedAsRejection() {
        try {
            queue.deserialize(new byte[] {1, 2, 3, 4});
            fail("Expected DistributedQueueException");
        } catch (RejectedQueueEntryException e) {
            fail("Corrupt data must not be treated as a filter rejection");
        } catch (DistributedQueueException e) {
            // expected
        }
    }

    @Test
    public void readSkipsAndDeletesRejectedEntryAndReturnsNextValidOne() throws Exception {
        EasyMock.expect(zk.getChildren(EasyMock.eq(ENTRY_FOLDER), EasyMock.anyObject(Watcher.class)))
                .andReturn(new ArrayList<>(Arrays.asList("entry-2", "entry-1")));
        expectGetData("entry-1", serialize(new Gadget()));
        zk.delete(ENTRY_FOLDER + "/entry-1", -1);
        expectGetData("entry-2", serialize(new CatalogReindexCommand(5L)));
        zk.delete(ENTRY_FOLDER + "/entry-2", 0);
        EasyMock.replay(zk);

        Map<String, Serializable> result = queue.readQueueInternal(1, true, 0L);

        assertEquals(Collections.singletonMap("entry-2", new CatalogReindexCommand(5L)), result);
        assertFalse(Gadget.triggered);
        EasyMock.verify(zk);
    }

    @Test
    public void pollWaitsWhenOnlyRejectedEntriesArePresent() throws Exception {
        EasyMock.expect(zk.getChildren(EasyMock.eq(ENTRY_FOLDER), EasyMock.anyObject(Watcher.class)))
                .andReturn(new ArrayList<>(Collections.singletonList("entry-1")))
                .andReturn(new ArrayList<>()).anyTimes();
        expectGetData("entry-1", serialize(new Gadget()));
        zk.delete(ENTRY_FOLDER + "/entry-1", -1);
        EasyMock.replay(zk);

        long start = System.currentTimeMillis();
        Serializable polled = queue.poll(300L, TimeUnit.MILLISECONDS);
        long elapsed = System.currentTimeMillis() - start;

        assertNull(polled);
        assertTrue("poll should block for its timeout instead of returning early, took " + elapsed + "ms", elapsed >= 250L);
        assertFalse(Gadget.triggered);
        EasyMock.verify(zk);
    }

    private void expectGetData(String entryName, byte[] data) throws Exception {
        EasyMock.expect(zk.getData(EasyMock.eq(ENTRY_FOLDER + '/' + entryName), EasyMock.<Watcher>isNull(), EasyMock.<Stat>isNull()))
                .andReturn(data);
    }

    private void assertRejected(byte[] data, String expectedReason) {
        try {
            queue.deserialize(data);
            fail("Expected RejectedQueueEntryException");
        } catch (RejectedQueueEntryException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(expectedReason));
        }
        assertFalse("Gadget readObject must never run", Gadget.triggered);
    }

    private static byte[] serialize(Serializable obj) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static class NotAllowed extends SolrUpdateCommand {
        private static final long serialVersionUID = 1L;
    }
}
