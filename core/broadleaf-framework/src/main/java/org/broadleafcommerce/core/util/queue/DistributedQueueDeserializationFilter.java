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

import org.springframework.util.Assert;

import java.io.ObjectInputFilter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the allow-list {@link ObjectInputFilter} that is applied to data read from a distributed queue.  Data read from an
 * external store such as Zookeeper is untrusted, so any class that is not explicitly allowed is rejected before it is resolved
 * or instantiated.  Patterns follow the syntax of {@link ObjectInputFilter.Config#createFilter(String)}.
 */
public class DistributedQueueDeserializationFilter {

    /**
     * Class name patterns that are permitted by default.  Queues that carry types outside of these patterns should register them
     * via {@link #addAllowedClassPatterns(String...)}.
     */
    public static final List<String> DEFAULT_ALLOWED_CLASS_PATTERNS = Collections.unmodifiableList(Arrays.asList(
            //array patterns match on the component type, so this covers the Object[] backing collections
            "java.lang.Object",
            "java.lang.Boolean",
            "java.lang.Byte",
            "java.lang.Character",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Short",
            "java.lang.Number",
            "java.lang.String",
            "java.lang.Enum",
            "java.math.BigDecimal",
            "java.math.BigInteger",
            "java.util.Date",
            "java.sql.Date",
            "java.sql.Timestamp",
            "java.time.*",
            "java.util.ArrayList",
            "java.util.LinkedList",
            "java.util.HashMap",
            "java.util.LinkedHashMap",
            "java.util.TreeMap",
            "java.util.HashSet",
            "java.util.LinkedHashSet",
            "java.util.TreeSet",
            "java.util.Arrays$ArrayList",
            "java.util.Collections$*",
            "org.broadleafcommerce.**"));

    /**
     * Structural limits applied to the object graph.  The byte limit matches Zookeeper's 1MB transport limit.
     */
    public static final String LIMITS = "maxdepth=32;maxrefs=10000;maxbytes=1048576;maxarray=10000";

    private final Set<String> allowedClassPatterns = new LinkedHashSet<>(DEFAULT_ALLOWED_CLASS_PATTERNS);
    private volatile ObjectInputFilter filter;

    /**
     * Replaces the allowed class name patterns.
     *
     * @param patterns
     */
    public synchronized void setAllowedClassPatterns(Collection<String> patterns) {
        Assert.notEmpty(patterns, "At least one allowed class pattern must be provided.");
        allowedClassPatterns.clear();
        allowedClassPatterns.addAll(patterns);
        filter = null;
    }

    /**
     * Adds to the allowed class name patterns.
     *
     * @param patterns
     */
    public synchronized void addAllowedClassPatterns(String... patterns) {
        Assert.notEmpty(patterns, "At least one allowed class pattern must be provided.");
        allowedClassPatterns.addAll(Arrays.asList(patterns));
        filter = null;
    }

    public Set<String> getAllowedClassPatterns() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(allowedClassPatterns));
    }

    /**
     * @return a filter that allows only the configured patterns and rejects everything else
     */
    public ObjectInputFilter getFilter() {
        ObjectInputFilter localFilter = filter;
        if (localFilter == null) {
            synchronized (this) {
                localFilter = filter;
                if (localFilter == null) {
                    final StringBuilder pattern = new StringBuilder(LIMITS);
                    for (String allowed : allowedClassPatterns) {
                        pattern.append(';').append(allowed);
                    }
                    pattern.append(";!*");
                    localFilter = ObjectInputFilter.Config.createFilter(pattern.toString());
                    filter = localFilter;
                }
            }
        }
        return localFilter;
    }
}
