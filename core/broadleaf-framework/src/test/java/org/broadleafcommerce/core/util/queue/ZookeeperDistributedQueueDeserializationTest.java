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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.broadleafcommerce.core.util.queue.ZookeeperDistributedQueue.AllowListObjectInputFilter;
import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates that data read from Zookeeper can only be deserialized into allowed types.
 */
public class ZookeeperDistributedQueueDeserializationTest {

    @After
    public void tearDown() {
        System.clearProperty(ZookeeperDistributedQueue.ALLOWED_DESERIALIZATION_PACKAGES_PROPERTY);
    }

    @Test
    public void testAllowedTypesAreDeserialized() throws Exception {
        assertEquals(Integer.valueOf(500), deserialize(serialize(Integer.valueOf(500))));
        assertEquals("some-queue-entry", deserialize(serialize("some-queue-entry")));

        final ArrayList<String> list = new ArrayList<>();
        list.add("first");
        list.add("second");
        assertEquals(list, deserialize(serialize(list)));

        final HashMap<String, Long> map = new HashMap<>();
        map.put("id", Long.valueOf(1L));
        assertEquals(map, deserialize(serialize(map)));
    }

    @Test
    public void testDisallowedTypeIsRejected() throws Exception {
        final byte[] bytes = serialize(new File("/tmp/attacker-controlled"));
        try {
            deserialize(bytes);
            fail("Expected " + File.class.getName() + " to be rejected by the deserialization filter.");
        } catch (InvalidClassException e) {
            assertTrue(e.getMessage().contains("filter"));
        }
    }

    @Test
    public void testDisallowedTypeNestedInAllowedCollectionIsRejected() throws Exception {
        final List<Serializable> list = new ArrayList<>();
        list.add("harmless");
        list.add(new File("/tmp/attacker-controlled"));
        final byte[] bytes = serialize((Serializable) list);
        try {
            deserialize(bytes);
            fail("Expected a nested disallowed type to be rejected by the deserialization filter.");
        } catch (InvalidClassException e) {
            assertTrue(e.getMessage().contains("filter"));
        }
    }

    /**
     * Dynamic proxies are the entry point of several well known deserialization gadget chains, and are rejected even when the
     * proxied interfaces and the {@link InvocationHandler} would be allowed on their own.
     */
    @Test
    public void testDynamicProxyIsRejected() throws Exception {
        final Object proxy = Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{Map.class}, new AllowedInvocationHandler());
        final byte[] bytes = serialize((Serializable) proxy);
        try {
            deserialize(bytes);
            fail("Expected a dynamic proxy to be rejected by the deserialization filter.");
        } catch (InvalidClassException e) {
            assertTrue(e.getMessage().contains("filter"));
        }
    }

    @Test
    public void testFrameworkTypesAndPrimitiveArraysAreAllowed() {
        final AllowListObjectInputFilter filter = new AllowListObjectInputFilter(
                ZookeeperDistributedQueue.DEFAULT_ALLOWED_DESERIALIZATION_CLASSES,
                ZookeeperDistributedQueue.getDefaultAllowedDeserializationPackages());

        assertTrue(filter.isAllowed("org.broadleafcommerce.core.search.service.solr.indexer.CatalogReindexCommand"));
        assertTrue(filter.isAllowed("java.util.HashMap"));
        assertFalse(filter.isAllowed("java.io.File"));
        assertFalse(filter.isAllowed("org.apache.commons.collections.functors.InvokerTransformer"));
        assertFalse(filter.isAllowed("com.mycompany.commands.CustomCommand"));
    }

    @Test
    public void testAdditionalPackagesCanBeConfigured() {
        System.setProperty(ZookeeperDistributedQueue.ALLOWED_DESERIALIZATION_PACKAGES_PROPERTY,
                "com.mycompany.commands, com.mycompany.other.");
        final Set<String> packages = ZookeeperDistributedQueue.getDefaultAllowedDeserializationPackages();
        assertTrue(packages.contains(ZookeeperDistributedQueue.DEFAULT_ALLOWED_DESERIALIZATION_PACKAGE));
        assertTrue(packages.contains("com.mycompany.commands."));
        assertTrue(packages.contains("com.mycompany.other."));

        final AllowListObjectInputFilter filter = new AllowListObjectInputFilter(
                ZookeeperDistributedQueue.DEFAULT_ALLOWED_DESERIALIZATION_CLASSES, packages);
        assertTrue(filter.isAllowed("com.mycompany.commands.CustomCommand"));
        assertFalse(filter.isAllowed("java.io.File"));
    }

    protected byte[] serialize(Serializable obj) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    /**
     * Mirrors {@link ZookeeperDistributedQueue#deserialize(byte[])}, which cannot be invoked directly here since constructing the
     * queue requires a live Zookeeper connection.
     */
    protected Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            ois.setObjectInputFilter(new AllowListObjectInputFilter(
                    ZookeeperDistributedQueue.DEFAULT_ALLOWED_DESERIALIZATION_CLASSES,
                    ZookeeperDistributedQueue.getDefaultAllowedDeserializationPackages()));
            return ois.readObject();
        }
    }

    public static class AllowedInvocationHandler implements InvocationHandler, Serializable {

        private static final long serialVersionUID = 1L;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return null;
        }
    }
}
