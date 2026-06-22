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

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An {@link ObjectInputStream} that mitigates insecure deserialization (CWE-502) by restricting the set of classes
 * that may be resolved during {@link #readObject()} to an explicit allowlist. Any attempt to deserialize a class that
 * is not on the allowlist - or any dynamic proxy - results in an {@link InvalidClassException}, preventing the
 * instantiation of gadget-chain classes that could otherwise lead to remote code execution.
 *
 * <p>The allowlist is expressed as a list of patterns. Each pattern is either:
 * <ul>
 *     <li>an exact fully-qualified class name (e.g. {@code java.lang.Integer}), or</li>
 *     <li>a package prefix ending in {@code .*} (e.g. {@code java.util.*}) that matches the package and all of its
 *     sub-packages.</li>
 * </ul>
 *
 * <p>Array types are unwrapped to their (non-primitive) component type before being matched, and primitive component
 * types are always permitted.
 *
 * @author Broadleaf Commerce
 */
public class SecureObjectInputStream extends ObjectInputStream {

    /**
     * A conservative default allowlist that covers the common, safe types stored on a Broadleaf distributed queue
     * (boxed primitives, strings, standard collections, dates/times, big numbers) as well as Broadleaf's own classes.
     * Applications that place additional custom types on the queue should supply an extended allowlist rather than
     * widening this default.
     */
    public static final List<String> DEFAULT_ALLOWED_CLASS_PATTERNS = Collections.unmodifiableList(Arrays.asList(
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
            "java.util.*",
            "java.time.*",
            "java.math.*",
            "org.broadleafcommerce.*"
    ));

    private final List<String> allowedClassPatterns;

    public SecureObjectInputStream(InputStream in) throws IOException {
        this(in, DEFAULT_ALLOWED_CLASS_PATTERNS);
    }

    public SecureObjectInputStream(InputStream in, List<String> allowedClassPatterns) throws IOException {
        super(in);
        if (allowedClassPatterns == null || allowedClassPatterns.isEmpty()) {
            this.allowedClassPatterns = DEFAULT_ALLOWED_CLASS_PATTERNS;
        } else {
            this.allowedClassPatterns = allowedClassPatterns;
        }
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        if (!isAllowed(desc.getName())) {
            throw new InvalidClassException(desc.getName(),
                    "Unauthorized deserialization attempt; class is not in the allowlist.");
        }
        return super.resolveClass(desc);
    }

    @Override
    protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
        throw new InvalidClassException("Unauthorized deserialization attempt; dynamic proxies are not permitted.");
    }

    protected boolean isAllowed(String className) {
        String name = className;
        // Unwrap array types (e.g. "[[Lorg.foo.Bar;" -> "org.foo.Bar").
        while (name.startsWith("[")) {
            name = name.substring(1);
        }
        if (name.startsWith("L") && name.endsWith(";")) {
            name = name.substring(1, name.length() - 1);
        }
        // Primitive array component types are encoded as a single character (e.g. 'I', 'J', 'Z') and are always safe.
        if (name.length() <= 1) {
            return true;
        }
        for (String pattern : allowedClassPatterns) {
            if (pattern.endsWith(".*")) {
                // Keep the trailing '.' so that "java.util.*" matches "java.util" and all sub-packages but not, say,
                // "java.utilx.Foo".
                final String prefix = pattern.substring(0, pattern.length() - 1);
                if (name.startsWith(prefix)) {
                    return true;
                }
            } else if (name.equals(pattern)) {
                return true;
            }
        }
        return false;
    }
}
