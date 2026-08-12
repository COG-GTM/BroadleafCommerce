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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Verifies that the {@link ObjectInputFilter} used by {@link ZookeeperDistributedQueue} allows expected queue payloads and rejects
 * arbitrary types, which are the gadget entrypoint for deserialization based remote code execution.
 */
public class ZookeeperDistributedQueueDeserializationTest extends TestCase {

    private final ObjectInputFilter filter =
            ObjectInputFilter.Config.createFilter(ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN);

    public void testAllowsCommonQueuePayloads() throws Exception {
        assertEquals(Integer.valueOf(500), roundTrip(Integer.valueOf(500)));
        assertEquals("some-command", roundTrip("some-command"));
        assertEquals(new ArrayList<>(Arrays.asList("a", "b")), roundTrip(new ArrayList<>(Arrays.asList("a", "b"))));
    }

    public void testRejectsUnlistedTypes() throws Exception {
        try {
            roundTrip(new File("/tmp/unlisted"));
            fail("Expected the deserialization filter to reject " + File.class.getName());
        } catch (InvalidClassException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("REJECTED"));
        }
    }

    private Object roundTrip(Serializable obj) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            ois.setObjectInputFilter(filter);
            return ois.readObject();
        }
    }
}
