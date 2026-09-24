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

import java.io.ObjectInputFilter;
import java.io.ObjectInputFilter.FilterInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import junit.framework.TestCase;

public class ZookeeperDistributedQueueDeserializationFilterTest extends TestCase {

    public void testAllowedClassesArePermitted() {
        ObjectInputFilter filter = ZookeeperDistributedQueue.createDeserializationFilter(
                ZookeeperDistributedQueue.DEFAULT_ALLOWED_CLASS_NAMES);

        assertEquals(ObjectInputFilter.Status.ALLOWED, filter.checkInput(new TestFilterInfo(String.class)));
        assertEquals(ObjectInputFilter.Status.ALLOWED, filter.checkInput(new TestFilterInfo(Integer.class)));
    }

    public void testUnregisteredClassesAreRejected() {
        ObjectInputFilter filter = ZookeeperDistributedQueue.createDeserializationFilter(
                ZookeeperDistributedQueue.DEFAULT_ALLOWED_CLASS_NAMES);

        assertEquals(ObjectInputFilter.Status.REJECTED, filter.checkInput(new TestFilterInfo(HashMap.class)));
        assertEquals(ObjectInputFilter.Status.REJECTED, filter.checkInput(new TestFilterInfo(Thread.class)));
    }

    public void testRegisteredClassesArePermitted() {
        List<String> classNames = new ArrayList<>(ZookeeperDistributedQueue.DEFAULT_ALLOWED_CLASS_NAMES);
        classNames.add(HashMap.class.getName());
        ObjectInputFilter filter = ZookeeperDistributedQueue.createDeserializationFilter(classNames);

        assertEquals(ObjectInputFilter.Status.ALLOWED, filter.checkInput(new TestFilterInfo(HashMap.class)));
        assertEquals(ObjectInputFilter.Status.REJECTED, filter.checkInput(new TestFilterInfo(Thread.class)));
    }

    public void testExcessiveDepthIsRejected() {
        ObjectInputFilter filter = ZookeeperDistributedQueue.createDeserializationFilter(
                Arrays.asList(String.class.getName()));

        FilterInfo tooDeep = new TestFilterInfo(String.class) {
            @Override
            public long depth() {
                return 1000L;
            }
        };

        assertEquals(ObjectInputFilter.Status.REJECTED, filter.checkInput(tooDeep));
    }

    private static class TestFilterInfo implements FilterInfo {

        private final Class<?> serialClass;

        TestFilterInfo(Class<?> serialClass) {
            this.serialClass = serialClass;
        }

        @Override
        public Class<?> serialClass() {
            return serialClass;
        }

        @Override
        public long arrayLength() {
            return -1L;
        }

        @Override
        public long depth() {
            return 1L;
        }

        @Override
        public long references() {
            return 1L;
        }

        @Override
        public long streamBytes() {
            return 1L;
        }
    }
}
