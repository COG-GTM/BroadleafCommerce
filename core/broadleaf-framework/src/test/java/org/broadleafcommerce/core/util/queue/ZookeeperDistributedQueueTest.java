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
import org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.SolrUpdateCommand;
import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ZookeeperDistributedQueueTest {

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

    @Before
    public void resetPayloadFlag() {
        EvilPayload.readObjectInvoked = false;
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

    @Test
    public void testSolrUpdateCommandRoundTrips() throws Exception {
        ZookeeperDistributedQueue<SolrUpdateCommand> queue = new ZookeeperDistributedQueue<>("/round-trip", zk);

        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "product-1");
        doc.addField("price", new BigDecimal("19.99"));
        doc.addField("quantity", 5L);
        doc.addField("created", new Date());
        queue.put(new IncrementalUpdateCommand(Collections.singletonList(doc), Collections.singletonList("id:old")));

        SolrUpdateCommand taken = queue.poll();
        assertNotNull(taken);
        IncrementalUpdateCommand command = (IncrementalUpdateCommand) taken;
        assertEquals("product-1", command.getSolrInputDocuments().get(0).getFieldValue("id"));
        assertEquals(new BigDecimal("19.99"), command.getSolrInputDocuments().get(0).getFieldValue("price"));
        assertEquals("id:old", command.getDeleteQueries().get(0));
    }

    @Test
    public void testDisallowedClassIsRejected() throws Exception {
        ZookeeperDistributedQueue<SolrUpdateCommand> queue = new ZookeeperDistributedQueue<>("/rejected", zk);
        writeRawEntry("/rejected", new EvilPayload());

        try {
            queue.poll();
            fail("Expected deserialization of a disallowed class to be rejected");
        } catch (DistributedQueueException e) {
            assertTrue("Expected InvalidClassException cause but was " + e.getCause(),
                    e.getCause() instanceof InvalidClassException);
        }
        assertFalse("readObject of a rejected class must never run", EvilPayload.readObjectInvoked);
    }

    @Test
    public void testCustomFilterCanAllowAdditionalClasses() throws Exception {
        ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
                EvilPayload.class.getName() + ';' + ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN);
        ZookeeperDistributedQueue<Serializable> queue = new ZookeeperDistributedQueue<>("/custom-filter", zk,
                ZookeeperDistributedQueue.DEFAULT_MAX_QUEUE_SIZE, true, null, filter);
        writeRawEntry("/custom-filter", new EvilPayload());

        assertTrue(queue.poll() instanceof EvilPayload);
        assertTrue(EvilPayload.readObjectInvoked);
    }

    private void writeRawEntry(String queuePath, Serializable payload) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(payload);
        }
        String entryFolder = ZookeeperDistributedQueue.DEFAULT_BASE_FOLDER + queuePath + ZookeeperDistributedQueue.QUEUE_ENTRY_FOLDER;
        zk.create(entryFolder + "/dz-queue-entry", baos.toByteArray(), ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT_SEQUENTIAL);
    }

    public static class EvilPayload implements Serializable {

        private static final long serialVersionUID = 1L;

        static volatile boolean readObjectInvoked;

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            readObjectInvoked = true;
        }
    }
}
