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

import org.broadleafcommerce.core.search.service.solr.indexer.FullReindexCommand;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ZookeeperDistributedQueueDeserializationFilterTest {

    private final ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
            ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN);

    @Test
    public void shouldRoundTripInteger() throws Exception {
        assertEquals(Integer.valueOf(42), roundTrip(Integer.valueOf(42)));
    }

    @Test
    public void shouldRoundTripCollection() throws Exception {
        assertEquals(new ArrayList<>(Arrays.asList("a", "b")), roundTrip(new ArrayList<>(Arrays.asList("a", "b"))));
    }

    @Test
    public void shouldRoundTripBroadleafCommand() throws Exception {
        FullReindexCommand command = new FullReindexCommand();

        assertEquals(command, roundTrip(command));
    }

    @Test
    public void shouldRejectClassesOutsideAllowedPackages() {
        assertThrows(InvalidClassException.class, () -> roundTrip(new URL("http://x")));
    }

    private Object roundTrip(Serializable object) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(object);
        }

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            ois.setObjectInputFilter(filter);
            return ois.readObject();
        }
    }
}
