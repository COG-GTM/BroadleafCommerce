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
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.broadleafcommerce.core.search.service.solr.indexer.CatalogReindexCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.DefaultSolrIndexQueueProvider;
import org.broadleafcommerce.core.search.service.solr.indexer.FullReindexCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.IncrementalUpdateCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.SiteReindexCommand;
import org.broadleafcommerce.core.search.service.solr.indexer.SolrUpdateCommand;
import org.broadleafcommerce.core.util.ZookeeperUtil;
import org.broadleafcommerce.core.util.queue.DistributedBlockingQueue.DistributedQueueException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Runs {@link ZookeeperDistributedQueue} and {@link DefaultSolrIndexQueueProvider} against an embedded Zookeeper server and
 * checks that entries planted in Zookeeper are only deserialized when their classes are expected queue payloads.
 */
public class ZookeeperDistributedQueueDeserializationTest {

    private static File dataDir;
    private static ZooKeeperServer server;
    private static ServerCnxnFactory connectionFactory;
    private static String connectString;

    private final List<ZooKeeper> clients = new ArrayList<>();
    private ZooKeeper zk;

    @BeforeClass
    public static void startZookeeper() throws Exception {
        dataDir = Files.createTempDirectory("bl-zk-queue-test").toFile();
        server = new ZooKeeperServer(dataDir, dataDir, 2000);
        connectionFactory = ServerCnxnFactory.createFactory(new InetSocketAddress("127.0.0.1", 0), 100);
        connectionFactory.startup(server);
        connectString = "127.0.0.1:" + connectionFactory.getLocalPort();
    }

    @Before
    public void createSharedParentFolders() throws Exception {
        //In a real deployment every node shares the same ACL config; here the open and digest tests share one server.
        ZooKeeper admin = newClient();
        ZookeeperUtil.makePath(ZookeeperDistributedQueue.DEFAULT_BASE_FOLDER + DefaultSolrIndexQueueProvider.QUEUE_PATH, null,
                admin, CreateMode.PERSISTENT, ZooDefs.Ids.OPEN_ACL_UNSAFE);
    }

    @AfterClass
    public static void stopZookeeper() throws Exception {
        if (connectionFactory != null) {
            connectionFactory.shutdown();
        }
        if (server != null) {
            server.shutdown();
        }
        if (dataDir != null) {
            deleteDir(dataDir);
        }
    }

    @Before
    public void connect() throws Exception {
        zk = newClient();
        UnexpectedPayload.READ_OBJECT_CALLED.set(false);
    }

    @After
    public void disconnect() throws Exception {
        for (ZooKeeper client : clients) {
            client.close();
        }
    }

    @Test
    public void roundTripsSolrUpdateCommands() throws Exception {
        ZookeeperDistributedQueue<SolrUpdateCommand> queue = new ZookeeperDistributedQueue<>(uniquePath(), zk);

        SolrInputDocument child = new SolrInputDocument();
        child.addField("id", "sku-1");
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", "product-1");
        doc.addField("name_t", "Hot Sauce");
        doc.addField("price_d", new BigDecimal("12.99"));
        doc.addField("stock_i", 7);
        doc.addField("active_b", true);
        doc.addField("created_dt", new Date(0L));
        doc.addField("tags_ss", Arrays.asList("spicy", "sale"));
        Map<String, Object> atomicUpdate = new LinkedHashMap<>();
        atomicUpdate.put("set", "updated");
        doc.addField("desc_t", atomicUpdate);
        doc.addChildDocument(child);

        assertTrue(queue.offer(new IncrementalUpdateCommand(Collections.singletonList(doc), Arrays.asList("id:old-1", "id:old-2"))));
        assertTrue(queue.offer(FullReindexCommand.DEFAULT_INSTANCE));
        assertTrue(queue.offer(new CatalogReindexCommand(5L)));
        assertTrue(queue.offer(new SiteReindexCommand(3L)));

        IncrementalUpdateCommand incremental = (IncrementalUpdateCommand) queue.poll();
        assertNotNull(incremental);
        assertEquals(Arrays.asList("id:old-1", "id:old-2"), incremental.getDeleteQueries());
        SolrInputDocument read = incremental.getSolrInputDocuments().get(0);
        assertEquals("product-1", read.getFieldValue("id"));
        assertEquals(new BigDecimal("12.99"), read.getFieldValue("price_d"));
        assertEquals(7, read.getFieldValue("stock_i"));
        assertEquals(new Date(0L), read.getFieldValue("created_dt"));
        assertEquals(Arrays.asList("spicy", "sale"), new ArrayList<>(read.getFieldValues("tags_ss")));
        assertEquals(atomicUpdate, read.getFieldValue("desc_t"));
        assertEquals("sku-1", read.getChildDocuments().get(0).getFieldValue("id"));

        assertEquals(FullReindexCommand.DEFAULT_INSTANCE, queue.poll());
        assertEquals(new CatalogReindexCommand(5L), queue.poll());
        assertEquals(new SiteReindexCommand(3L), queue.poll());
        assertNull(queue.poll());
    }

    @Test
    public void rejectsPlantedEntryOfUnexpectedClassWithoutRunningItsReadObject() throws Exception {
        ZookeeperDistributedQueue<SolrUpdateCommand> queue = new ZookeeperDistributedQueue<>(uniquePath(), zk);

        plant(newClient(), queue, new UnexpectedPayload("touch /tmp/pwned"));

        assertRejected(queue);
        assertFalse("readObject of a non-allowlisted class must not run", UnexpectedPayload.READ_OBJECT_CALLED.get());
    }

    @Test
    public void rejectsUnexpectedClassNestedInsideAnAllowedContainer() throws Exception {
        ZookeeperDistributedQueue<SolrUpdateCommand> queue = new ZookeeperDistributedQueue<>(uniquePath(), zk);
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id", new UnexpectedPayload("nested"));

        plant(newClient(), queue, new IncrementalUpdateCommand(Collections.singletonList(doc), null));

        assertRejected(queue);
        assertFalse(UnexpectedPayload.READ_OBJECT_CALLED.get());
    }

    @Test
    public void rejectsCommonGadgetEntryPoints() throws Exception {
        assertRejectedByFilter(new java.util.PriorityQueue<>(Collections.singletonList("x")));
        assertRejectedByFilter(new java.util.TreeMap<>(Collections.singletonMap("k", "v")));
        assertRejectedByFilter(new java.util.concurrent.ConcurrentHashMap<>(Collections.singletonMap("k", "v")));
        assertRejectedByFilter(new java.util.Hashtable<>(Collections.singletonMap("k", "v")));
        assertRejectedByFilter(new File("/etc/passwd"));
    }

    @Test
    public void rejectsGraphsDeeperThanTheConfiguredLimit() throws Exception {
        ZookeeperDistributedQueue<SolrUpdateCommand> queue = new ZookeeperDistributedQueue<>(uniquePath(), zk);
        List<Object> root = new ArrayList<>();
        List<Object> current = root;
        for (int i = 0; i < 100; i++) {
            List<Object> next = new ArrayList<>();
            current.add(next);
            current = next;
        }

        plant(newClient(), queue, (Serializable) root);

        assertRejected(queue);
    }

    @Test
    public void additionalAllowedClassPatternsExtendTheAllowlist() throws Exception {
        ZookeeperDistributedQueue<UnexpectedPayload> queue = new ZookeeperDistributedQueue<>(
                uniquePath(), zk, 10, true, null, Collections.singletonList(UnexpectedPayload.class.getName())
        );

        assertTrue(queue.offer(new UnexpectedPayload("custom")));
        assertEquals("custom", queue.poll().command);
        assertTrue(UnexpectedPayload.READ_OBJECT_CALLED.get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void additionalPatternsCannotSmuggleInFilterDirectives() {
        ZookeeperDistributedQueue.buildDeserializationFilter(Collections.singletonList("maxdepth=1000;*"));
    }

    @Test
    public void providerUsesWorldWritableAclWhenNotConfigured() throws Exception {
        DefaultSolrIndexQueueProvider provider = new DefaultSolrIndexQueueProvider(zk, new MockEnvironment());
        String queueName = "open-" + UUID.randomUUID();

        BlockingQueue<? super SolrUpdateCommand> queue = provider.createOrRetrieveCommandQueue(queueName);

        List<ACL> acls = zk.getACL(entryFolder(queue), null);
        assertEquals(ZooDefs.Ids.OPEN_ACL_UNSAFE, acls);
    }

    @Test
    public void providerLocksQueueNodesToDigestCredentials() throws Exception {
        MockEnvironment env = new MockEnvironment()
                .withProperty(DefaultSolrIndexQueueProvider.ZK_DIGEST_AUTH_PROPERTY, "broadleaf:s3cret");
        String queueName = "digest-" + UUID.randomUUID();
        DefaultSolrIndexQueueProvider provider = new DefaultSolrIndexQueueProvider(zk, env);

        @SuppressWarnings("unchecked")
        BlockingQueue<SolrUpdateCommand> queue = (BlockingQueue<SolrUpdateCommand>) provider.createOrRetrieveCommandQueue(queueName);
        assertTrue(queue.offer(new CatalogReindexCommand(1L)));
        String entryFolder = entryFolder(queue);

        List<ACL> acls = zk.getACL(entryFolder, null);
        assertEquals(1, acls.size());
        assertEquals("digest", acls.get(0).getId().getScheme());
        assertTrue(acls.get(0).getId().getId().startsWith("broadleaf:"));

        ZooKeeper anonymous = newClient();
        try {
            anonymous.create(entryFolder + "/dz-queue-entry", serialize(new UnexpectedPayload("x")),
                    ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
            fail("An unauthenticated client must not be able to add queue entries.");
        } catch (KeeperException.NoAuthException expected) {
            // expected
        }

        ZooKeeper wrongCredentials = newClient();
        wrongCredentials.addAuthInfo("digest", "broadleaf:guess".getBytes());
        try {
            wrongCredentials.getData(entryFolder, null, null);
            fail("A client with the wrong credentials must not be able to read the queue.");
        } catch (KeeperException.NoAuthException expected) {
            // expected
        }

        ZooKeeper otherNode = newClient();
        otherNode.addAuthInfo("digest", "broadleaf:s3cret".getBytes());
        ZookeeperDistributedQueue<SolrUpdateCommand> otherNodeQueue = new ZookeeperDistributedQueue<>(
                DefaultSolrIndexQueueProvider.QUEUE_PATH + '/' + queueName, otherNode, 500, true, ZooDefs.Ids.CREATOR_ALL_ACL
        );
        assertEquals(new CatalogReindexCommand(1L), otherNodeQueue.poll());
    }

    @Test(expected = IllegalArgumentException.class)
    public void providerRejectsMalformedDigestCredentials() {
        MockEnvironment env = new MockEnvironment()
                .withProperty(DefaultSolrIndexQueueProvider.ZK_DIGEST_AUTH_PROPERTY, "no-password");
        new DefaultSolrIndexQueueProvider(zk, env).createOrRetrieveCommandQueue("bad-" + UUID.randomUUID());
    }

    private void assertRejected(ZookeeperDistributedQueue<SolrUpdateCommand> queue) throws Exception {
        List<String> planted = zk.getChildren(queue.getQueueEntryFolder(), null);
        assertEquals(1, planted.size());
        byte[] bytes = zk.getData(queue.getQueueEntryFolder() + '/' + planted.get(0), null, null);
        try {
            Object read = queue.deserialize(bytes);
            fail("Expected the planted entry to be rejected, but read: " + read);
        } catch (DistributedQueueException e) {
            assertTrue("Expected an InvalidClassException cause but was " + e.getCause(), e.getCause() instanceof InvalidClassException);
            assertTrue(e.getCause().getMessage(), e.getCause().getMessage().contains("REJECTED"));
        }

        assertTrue(queue.offer(new SiteReindexCommand(42L)));
        assertEquals("The rejected entry is discarded and the next valid entry is delivered",
                new SiteReindexCommand(42L), queue.poll());
        assertTrue(zk.getChildren(queue.getQueueEntryFolder(), null).isEmpty());
        assertNull(queue.poll());
    }

    private void assertRejectedByFilter(Serializable payload) throws Exception {
        ZookeeperDistributedQueue<SolrUpdateCommand> queue = new ZookeeperDistributedQueue<>(uniquePath(), zk);
        plant(newClient(), queue, payload);
        assertRejected(queue);
    }

    private void plant(ZooKeeper attacker, ZookeeperDistributedQueue<?> queue, Serializable payload) throws Exception {
        attacker.create(queue.getQueueEntryFolder() + "/dz-queue-entry", serialize(payload),
                ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
    }

    private static String entryFolder(Object queue) {
        return ((ZookeeperDistributedQueue<?>) queue).getQueueEntryFolder();
    }

    private static byte[] serialize(Serializable payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(payload);
        }
        return bytes.toByteArray();
    }

    private static String uniquePath() {
        return "/test/" + UUID.randomUUID();
    }

    private ZooKeeper newClient() throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        ZooKeeper client = new ZooKeeper(connectString, 10000, event -> {
            if (event.getState() == org.apache.zookeeper.Watcher.Event.KeeperState.SyncConnected) {
                connected.countDown();
            }
        });
        clients.add(client);
        assertTrue("Could not connect to the embedded Zookeeper", connected.await(10, TimeUnit.SECONDS));
        return client;
    }

    private static void deleteDir(File dir) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteDir(child);
            }
        }
        dir.delete();
    }

    /**
     * Stands in for a deserialization gadget: a Serializable class with a readObject side effect that is not a queue payload.
     */
    public static class UnexpectedPayload implements Serializable {

        static final AtomicBoolean READ_OBJECT_CALLED = new AtomicBoolean();
        private static final long serialVersionUID = 1L;

        final String command;

        UnexpectedPayload(String command) {
            this.command = command;
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            READ_OBJECT_CALLED.set(true);
        }
    }
}
