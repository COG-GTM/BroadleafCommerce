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

/**
 * Verifies the default deserialization filter pattern used by {@link ZookeeperDistributedQueue} rejects
 * known Java deserialization gadget classes while still allowing typical Serializable payload types.
 * This guards against regression of the CWE-502 mitigation added to
 * {@link ZookeeperDistributedQueue#deserialize(byte[])}.
 */
public class ZookeeperDistributedQueueDeserializationFilterTest extends TestCase {

    private final ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
            ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN);

    public void testCommonTypesAreAllowed() {
        assertAllowed(String.class);
        assertAllowed(Integer.class);
        assertAllowed(Long.class);
        assertAllowed(java.util.ArrayList.class);
        assertAllowed(java.util.HashMap.class);
        assertAllowed(java.util.Date.class);
    }

    public void testKnownGadgetClassesAreRejected() throws ClassNotFoundException {
        // Use classes that are guaranteed to be available on the test classpath
        // (JDK built-ins and Spring, which is a transitive dependency).
        assertRejected(Class.forName("javax.management.BadAttributeValueExpException"));
        assertRejected(Class.forName("com.sun.rowset.JdbcRowSetImpl"));
        assertRejected(Class.forName("com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl"));
        assertRejected(Class.forName("org.springframework.beans.factory.ObjectFactory"));
        // The outer class and its inner gadget classes must both be rejected. Inner classes use `$`
        // in their binary name, which `ObjectInputFilter` only matches via a trailing prefix `*`.
        assertRejected(Class.forName("org.springframework.core.SerializableTypeWrapper"));
        assertRejected(Class.forName("org.springframework.core.SerializableTypeWrapper$MethodInvokeTypeProvider"));
    }

    public void testPatternMentionsWellKnownGadgetPackages() {
        // Sanity check that the pattern literally denies every gadget package we care about.
        String pattern = ZookeeperDistributedQueue.DEFAULT_DESERIALIZATION_FILTER_PATTERN;
        String[] denied = new String[] {
                "!org.apache.commons.collections.functors.**",
                "!org.apache.commons.collections4.functors.**",
                "!org.codehaus.groovy.runtime.**",
                "!org.springframework.beans.factory.**",
                "!com.sun.org.apache.xalan.internal.xsltc.trax.**",
                "!com.sun.rowset.**",
                "!javax.management.BadAttributeValueExpException",
                "!com.mchange.v2.c3p0.**",
                "!sun.rmi.server.**",
                "!java.rmi.server.**",
                "!bsh.**",
                "!clojure.**",
                "!org.python.core.**",
                "!javassist.util.proxy.**",
                "!com.sun.syndication.feed.impl.**",
                "!com.rometools.rome.feed.impl.**",
        };
        for (String token : denied) {
            assertTrue("Filter pattern is missing required denylist entry: " + token,
                    pattern.contains(token));
        }
    }

    private void assertAllowed(Class<?> clazz) {
        ObjectInputFilter.FilterInfo info = filterInfo(clazz);
        ObjectInputFilter.Status status = filter.checkInput(info);
        assertFalse("Expected class " + clazz.getName() + " to be allowed but filter returned REJECTED",
                status == ObjectInputFilter.Status.REJECTED);
    }

    private void assertRejected(Class<?> clazz) {
        ObjectInputFilter.FilterInfo info = filterInfo(clazz);
        ObjectInputFilter.Status status = filter.checkInput(info);
        assertEquals("Expected class " + clazz.getName() + " to be rejected",
                ObjectInputFilter.Status.REJECTED, status);
    }

    private ObjectInputFilter.FilterInfo filterInfo(final Class<?> clazz) {
        return new ObjectInputFilter.FilterInfo() {
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
        };
    }
}
