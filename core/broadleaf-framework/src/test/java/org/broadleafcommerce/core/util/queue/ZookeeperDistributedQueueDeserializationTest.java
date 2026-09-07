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

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ZookeeperDistributedQueueDeserializationTest {

    private static final Set<String> ALLOWED_PACKAGES = Set.of(
            "java.lang.",
            "java.util.",
            "java.math.",
            "java.time.",
            "org.broadleafcommerce.",
            "org.apache.solr.common."
    );

    @Test
    public void shouldDeserializeAllowedValues() throws Exception {
        assertEquals(Integer.valueOf(42), deserialize(Integer.valueOf(42)));

        ArrayList<String> values = new ArrayList<>(Arrays.asList("one", "two"));
        assertEquals(values, deserialize(values));
    }

    @Test(expected = InvalidClassException.class)
    public void shouldRejectNonAllowlistedClasses() throws Exception {
        deserialize(new Timestamp(0L));
    }

    private Object deserialize(Serializable value) throws Exception {
        byte[] bytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(value);
            oos.flush();
            bytes = baos.toByteArray();
        }

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            ObjectInputFilter filter = ZookeeperDistributedQueue.createDeserializationFilter(ALLOWED_PACKAGES);
            ois.setObjectInputFilter(filter);
            return ois.readObject();
        }
    }
}
