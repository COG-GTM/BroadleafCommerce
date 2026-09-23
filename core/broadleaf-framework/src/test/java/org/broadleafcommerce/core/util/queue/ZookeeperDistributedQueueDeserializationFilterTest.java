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

import java.io.ObjectInputFilter;
import java.io.ObjectInputFilter.Status;
import java.util.ArrayList;
import java.util.HashMap;

import javax.management.BadAttributeValueExpException;

public class ZookeeperDistributedQueueDeserializationFilterTest extends TestCase {

    private final ObjectInputFilter filter =
            ObjectInputFilter.Config.createFilter(ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN);

    public void testAllowsQueuePayloadTypes() {
        assertEquals(Status.ALLOWED, check(String.class));
        assertEquals(Status.ALLOWED, check(Integer.class));
        assertEquals(Status.ALLOWED, check(ArrayList.class));
        assertEquals(Status.ALLOWED, check(HashMap.class));
        assertEquals(Status.ALLOWED, check(String[].class));
    }

    public void testRejectsTypesOutsideTheAllowList() {
        assertEquals(Status.REJECTED, check(BadAttributeValueExpException.class));
        assertEquals(Status.REJECTED, check(javax.naming.Reference.class));
        assertEquals(Status.REJECTED, check(javax.sql.rowset.BaseRowSet.class));
    }

    private Status check(final Class<?> clazz) {
        return filter.checkInput(new ObjectInputFilter.FilterInfo() {

            @Override
            public Class<?> serialClass() {
                return clazz;
            }

            @Override
            public long arrayLength() {
                return -1;
            }

            @Override
            public long depth() {
                return 1;
            }

            @Override
            public long references() {
                return 1;
            }

            @Override
            public long streamBytes() {
                return 0;
            }
        });
    }
}
