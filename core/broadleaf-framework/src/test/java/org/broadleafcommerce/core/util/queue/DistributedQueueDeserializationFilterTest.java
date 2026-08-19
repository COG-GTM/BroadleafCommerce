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
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class DistributedQueueDeserializationFilterTest {

    private DistributedQueueDeserializationFilter filter;

    @Before
    public void setUp() {
        filter = new DistributedQueueDeserializationFilter();
    }

    @Test
    public void allowsCommonValueTypes() throws Exception {
        assertEquals(Integer.valueOf(500), roundTrip(Integer.valueOf(500)));
        assertEquals("queue-entry", roundTrip("queue-entry"));
        assertEquals(new ArrayList<>(Arrays.asList("a", "b")), roundTrip(new ArrayList<>(Arrays.asList("a", "b"))));
    }

    @Test
    public void rejectsUnregisteredTypes() throws Exception {
        try {
            roundTrip(new Random(1L));
            fail("Expected deserialization of an unregistered type to be rejected.");
        } catch (InvalidClassException e) {
            // expected
        }
    }

    @Test
    public void rejectsUnregisteredTypesNestedInAllowedContainers() throws Exception {
        final List<Serializable> entries = new ArrayList<>();
        entries.add(new Random(1L));
        try {
            roundTrip((Serializable) entries);
            fail("Expected deserialization of a nested unregistered type to be rejected.");
        } catch (InvalidClassException e) {
            // expected
        }
    }

    @Test
    public void allowsExplicitlyRegisteredTypes() throws Exception {
        filter.addAllowedClassPatterns(Random.class.getName());
        assertEquals(Random.class, roundTrip(new Random(1L)).getClass());
    }

    private Object roundTrip(Serializable obj) throws IOException, ClassNotFoundException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            ois.setObjectInputFilter(filter.getFilter());
            return ois.readObject();
        }
    }
}
