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
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.broadleafcommerce.core.search.service.solr.indexer.FullReindexCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.SolrUpdateCommand;
import org.broadleafcommerce.core.util.queue.ZookeeperDistributedQueue.DisallowedQueueEntryException;
import org.example.gadget.EvilPayload;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.management.BadAttributeValueExpException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ZookeeperDistributedQueueDeserializationTest {

    @ClassRule
    public static TemporaryFolder tmp = new TemporaryFolder();

    private static ServerCnxnFactory factory;
    private static ZooKeeper zk;

    @BeforeClass
    public static void startZookeeper() throws Exception {
        File dir = tmp.newFolder("zk");
        ZooKeeperServer server = new ZooKeeperServer(dir, dir, 2000);
        factory = ServerCnxnFactory.createFactory(0, 10);
        factory.startup(server);

        CountDownLatch connected = new CountDownLatch(1);
        zk = new ZooKeeper("127.0.0.1:" + factory.getLocalPort(), 10000, event -> {
            if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                connected.countDown();
            }
        });
        assertTrue("Could not connect to embedded Zookeeper", connected.await(10, TimeUnit.SECONDS));
    }

    @AfterClass
    public static void stopZookeeper() throws Exception {
        if (zk != null) {
            zk.close();
        }
        if (factory != null) {
            factory.shutdown();
        }
    }

    @Before
    public void resetPayloadFlag() {
        EvilPayload.readObjectInvoked = false;
    }

    @Test
    public void solrUpdateCommandsRoundTrip() throws Exception {
        ZookeeperDistributedQueue<SolrUpdateCommand> queue = new ZookeeperDistributedQueue<>("/round-trip", zk);

        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "product-1");
        doc.addField("price", new BigDecimal("19.99"));
        doc.addField("quantity", 5L);
        doc.addField("created", new Date());
        queue.put(new IncrementalUpdateCommand(Collections.singletonList(doc), Collections.singletonList("id:old")));
        queue.put(new FullReindexCommand());

        IncrementalUpdateCommand incremental = (IncrementalUpdateCommand) queue.poll();
        assertNotNull(incremental);
        assertEquals("product-1", incremental.getSolrInputDocuments().get(0).getFieldValue("id"));
        assertEquals(new BigDecimal("19.99"), incremental.getSolrInputDocuments().get(0).getFieldValue("price"));
        assertEquals("id:old", incremental.getDeleteQueries().get(0));
        assertTrue(queue.poll() instanceof FullReindexCommand);
        assertNull(queue.poll());
    }

    @Test
    public void gadgetEntryIsRejectedBeforeReadObjectAndDiscarded() throws Exception {
        ZookeeperDistributedQueue<SolrUpdateCommand> queue = new ZookeeperDistributedQueue<>("/gadget", zk);
        writeRawEntry("/gadget", new EvilPayload());
        queue.put(new FullReindexCommand());

        assertTrue("Valid entry behind the malicious one must still be delivered", queue.poll() instanceof FullReindexCommand);
        assertFalse("readObject of a rejected class must never run", EvilPayload.readObjectInvoked);
        assertEquals("Malicious entry must be removed so it cannot block the queue", 0, queue.size());
    }

    @Test
    public void jdkGadgetEntryPointIsRejected() throws Exception {
        ZookeeperDistributedQueue<SolrUpdateCommand> queue = new ZookeeperDistributedQueue<>("/jdk-gadget", zk);
        writeRawEntry("/jdk-gadget", new BadAttributeValueExpException("x"));

        assertNull(queue.poll());
        assertEquals(0, queue.size());
    }

    @Test
    public void gadgetNestedInsideAllowedCollectionIsRejected() throws Exception {
        ZookeeperDistributedQueue<Serializable> queue = new ZookeeperDistributedQueue<>("/nested", zk);
        ArrayList<Object> wrapper = new ArrayList<>();
        wrapper.add("harmless");
        wrapper.add(new EvilPayload());

        assertRejected(queue, wrapper);
        assertFalse(EvilPayload.readObjectInvoked);
    }

    @Test
    public void excessiveNestingDepthIsRejected() throws Exception {
        ZookeeperDistributedQueue<Serializable> queue = new ZookeeperDistributedQueue<>("/depth", zk);
        List<Object> root = new ArrayList<>();
        List<Object> current = root;
        for (int i = 0; i < ZookeeperDistributedQueue.MAX_DESERIALIZATION_DEPTH + 10; i++) {
            List<Object> child = new ArrayList<>();
            current.add(child);
            current = child;
        }

        assertRejected(queue, (Serializable) root);
    }

    @Test
    public void subclassCanExtendAllowlist() throws Exception {
        ZookeeperDistributedQueue<Serializable> queue = new ZookeeperDistributedQueue<Serializable>("/extended", zk) {
            @Override
            protected List<String> getAllowedDeserializationPatterns() {
                List<String> patterns = new ArrayList<>(super.getAllowedDeserializationPatterns());
                patterns.add(EvilPayload.class.getName());
                return patterns;
            }
        };
        writeRawEntry("/extended", new EvilPayload());

        assertTrue(queue.poll() instanceof EvilPayload);
        assertTrue(EvilPayload.readObjectInvoked);
    }

    private static void assertRejected(ZookeeperDistributedQueue<Serializable> queue, Serializable payload) throws Exception {
        try {
            queue.deserialize(serialize(payload));
            fail("Expected deserialization to be rejected");
        } catch (DisallowedQueueEntryException e) {
            assertTrue(e.getCause() instanceof InvalidClassException);
        }
    }

    private static void writeRawEntry(String queuePath, Serializable payload) throws Exception {
        zk.create(ZookeeperDistributedQueue.DEFAULT_BASE_FOLDER + queuePath + ZookeeperDistributedQueue.QUEUE_ENTRY_FOLDER
                        + "/dz-queue-entry", serialize(payload), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
    }

    private static byte[] serialize(Serializable payload) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(payload);
        }
        return baos.toByteArray();
    }

}
