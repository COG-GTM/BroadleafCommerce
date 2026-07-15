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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Performs hardened deserialization of distributed-queue payloads as a defense against insecure deserialization
 * (CWE-502).
 * <p>
 * Java's default {@link ObjectInputStream} will happily instantiate <em>any</em> {@link Serializable} class present in a
 * stream. When the serialized bytes originate from an untrusted or externally-influenced source (e.g. data stored in
 * Zookeeper), this allows an attacker to trigger "gadget chains" that can lead to remote code execution. This class
 * mitigates that risk with two layers of defense:
 * <ol>
 *   <li>An <strong>allow-list</strong>: {@link #resolveClass(ObjectStreamClass) class resolution} rejects any class
 *       whose fully-qualified name does not begin with one of the configured {@link #getAllowedClassNamePrefixes()
 *       prefixes}.</li>
 *   <li>An {@link ObjectInputFilter} that additionally caps the object-graph depth, reference count, and stream size to
 *       limit resource-exhaustion attacks and re-checks the class allow-list.</li>
 * </ol>
 * The defaults permit the JDK value/collection types and Broadleaf's own classes. When a queue stores custom payload
 * types, register their prefixes via {@link #addAllowedClassNamePrefix(String)}.
 *
 * @author Broadleaf Commerce
 */
public class SecureQueueDeserializer {

    /**
     * Default class-name prefixes permitted during deserialization. Deliberately narrow: none of the well-known
     * deserialization gadget libraries (e.g. Apache Commons Collections, {@code com.sun.*}, {@code javax.management.*})
     * fall under these prefixes.
     */
    public static final Set<String> DEFAULT_ALLOWED_CLASS_NAME_PREFIXES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "java.lang.",
                    "java.util.",
                    "java.time.",
                    "java.math.",
                    "org.broadleafcommerce.")));

    private final Set<String> allowedClassNamePrefixes;
    private final int maxDepth;
    private final long maxReferences;
    private final long maxStreamBytes;

    /**
     * Creates a deserializer with the {@link #DEFAULT_ALLOWED_CLASS_NAME_PREFIXES default allow-list} and default
     * resource limits (depth 64, 10,000 references, 1MB).
     */
    public SecureQueueDeserializer() {
        this(DEFAULT_ALLOWED_CLASS_NAME_PREFIXES, 64, 10000L, 1024L * 1024L);
    }

    /**
     * @param allowedClassNamePrefixes the initial set of allowed class-name prefixes (copied)
     * @param maxDepth maximum object-graph depth permitted
     * @param maxReferences maximum number of object references permitted
     * @param maxStreamBytes maximum number of stream bytes permitted
     */
    public SecureQueueDeserializer(Set<String> allowedClassNamePrefixes, int maxDepth, long maxReferences, long maxStreamBytes) {
        Assert.notNull(allowedClassNamePrefixes, "allowedClassNamePrefixes must not be null.");
        this.allowedClassNamePrefixes = Collections.synchronizedSet(new LinkedHashSet<>(allowedClassNamePrefixes));
        this.maxDepth = maxDepth;
        this.maxReferences = maxReferences;
        this.maxStreamBytes = maxStreamBytes;
    }

    /**
     * Deserializes the given bytes into an object, enforcing the configured class allow-list and resource limits.
     *
     * @param bytes the serialized bytes; may be null (returns null)
     * @return the deserialized object, or null if {@code bytes} is null
     * @throws IOException if the stream cannot be read, a resource limit is exceeded, or the payload contains a class
     *         that is not on the allow-list (an {@link InvalidClassException} in that case)
     * @throws ClassNotFoundException if a class in the stream cannot be located
     */
    public Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        if (bytes == null) {
            return null;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ValidatingObjectInputStream(bais)) {
            ois.setObjectInputFilter(buildFilter());
            return ois.readObject();
        }
    }

    /**
     * @return the live, mutable, thread-safe set of allowed class-name prefixes. Never null.
     */
    public Set<String> getAllowedClassNamePrefixes() {
        return allowedClassNamePrefixes;
    }

    /**
     * Registers an additional allowed class-name prefix (a fully-qualified class name or package prefix).
     *
     * @param classNamePrefix e.g. {@code com.mycompany.queue.}
     */
    public void addAllowedClassNamePrefix(String classNamePrefix) {
        Assert.hasText(classNamePrefix, "classNamePrefix must not be null or empty.");
        allowedClassNamePrefixes.add(classNamePrefix.trim());
    }

    /**
     * @param className the fully-qualified (component) class name being resolved
     * @return true if {@code className} begins with one of the allowed prefixes
     */
    public boolean isClassAllowed(String className) {
        if (className == null) {
            return false;
        }
        synchronized (allowedClassNamePrefixes) {
            for (final String allowedPrefix : allowedClassNamePrefixes) {
                if (className.startsWith(allowedPrefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Builds a stream-scoped {@link ObjectInputFilter} enforcing resource limits and the class allow-list.
     */
    protected ObjectInputFilter buildFilter() {
        return filterInfo -> {
            if (filterInfo.depth() > maxDepth
                    || filterInfo.references() > maxReferences
                    || filterInfo.streamBytes() > maxStreamBytes) {
                return ObjectInputFilter.Status.REJECTED;
            }

            Class<?> clazz = filterInfo.serialClass();
            if (clazz == null) {
                return ObjectInputFilter.Status.UNDECIDED;
            }

            while (clazz.isArray()) {
                clazz = clazz.getComponentType();
            }
            if (clazz.isPrimitive()) {
                return ObjectInputFilter.Status.ALLOWED;
            }
            if (isClassAllowed(clazz.getName())) {
                return ObjectInputFilter.Status.ALLOWED;
            }
            return ObjectInputFilter.Status.REJECTED;
        };
    }

    /**
     * An {@link ObjectInputStream} that enforces the {@link #isClassAllowed(String) class allow-list} when resolving
     * classes, refusing to instantiate any class not on the allow-list.
     */
    protected class ValidatingObjectInputStream extends ObjectInputStream {

        protected ValidatingObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            final String name = desc.getName();

            // Unwrap array type descriptors (e.g. "[[Lcom.Foo;") down to the base component type name.
            String componentName = name;
            while (componentName.startsWith("[")) {
                componentName = componentName.substring(1);
            }
            if (componentName.startsWith("L") && componentName.endsWith(";")) {
                componentName = componentName.substring(1, componentName.length() - 1);
            }

            // Single-character codes (e.g. "I", "J") denote primitive arrays and carry no class name to validate.
            if (componentName.length() > 1 && !isClassAllowed(componentName)) {
                throw new InvalidClassException(name,
                        "Deserialization of this class is not permitted by the distributed-queue security policy.");
            }

            return super.resolveClass(desc);
        }
    }
}
