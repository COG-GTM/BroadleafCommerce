/*-
 * #%L
 * BroadleafCommerce Common Libraries
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
package org.broadleafcommerce.common.rule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Restricts MVEL rules to side-effect free predicates over the variables handed to the rule.
 * <p>
 * Rule text is authored by admin users (offers, pages, structured content, fulfillment fees, ...) and is executed on
 * the storefront, so it is treated as untrusted. MVEL resolves fully qualified class names and a set of
 * {@code java.lang} types (Runtime, System, Thread, Class, ...) on its own, which would let a rule run arbitrary code.
 * Every rule is therefore validated before it is compiled:
 * <ul>
 *     <li>a type may only be referenced if it is an explicitly allowed type or a context import;</li>
 *     <li>reflective, class loading, process, and mutating members (getClass, forName, exec, setters, collection
 *     mutators, ...) are denied;</li>
 *     <li>statements, blocks, assignments, object creation, imports and function definitions are denied.</li>
 * </ul>
 * String literals are not inspected, so rule data that merely mentions a blocked name is unaffected.
 */
public class MvelRuleSandbox {

    /**
     * Types (simple or fully qualified names) that every rule may reference, in addition to the context imports.
     */
    public static final Set<String> DEFAULT_ALLOWED_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "MvelHelper", "CollectionUtils", "StringUtils", "org.apache.commons.lang3.StringUtils",
            "Math", "String", "Integer", "Long", "Double", "Float", "Short", "Byte", "Boolean", "Character",
            "BigDecimal", "BigInteger"
    )));

    protected static final Set<String> BLOCKED_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "new", "import", "import_static", "def", "function", "class", "proto", "typeof", "with", "foreach",
            "for", "while", "until", "do", "if", "else", "return", "assert", "switch", "convertable_to"
    )));

    protected static final Set<String> BLOCKED_MEMBERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "getClass", "forName", "newInstance", "getRuntime", "exec", "exit", "halt", "load", "loadLibrary",
            "loadClass", "defineClass", "getClassLoader", "getSystemClassLoader", "getContextClassLoader",
            "currentThread", "invoke", "invokeExact", "lookup", "getMethod", "getMethods", "getDeclaredMethod",
            "getDeclaredMethods", "getField", "getFields", "getDeclaredField", "getDeclaredFields", "getConstructor",
            "getConstructors", "getDeclaredConstructor", "getDeclaredConstructors", "getSuperclass",
            "getDeclaringClass", "getEnclosingClass", "getComponentType", "getProtectionDomain", "getModule",
            "getResource", "getResourceAsStream", "getProperty", "getProperties", "getenv", "getInteger", "getLong",
            "getBoolean", "doPrivileged", "wait", "notify", "notifyAll", "readObject", "writeObject",
            "compileExpression", "executeExpression", "eval", "getBean", "getApplicationContext",
            "getServletContext", "getSession",
            "add", "addAll", "addFirst", "addLast", "remove", "removeAll", "removeIf", "removeFirst", "removeLast",
            "retainAll", "clear", "put", "putAll", "putIfAbsent", "replace", "replaceAll", "compute",
            "computeIfAbsent", "computeIfPresent", "merge", "set", "sort", "push", "pop", "poll", "pollFirst",
            "pollLast", "offer", "offerFirst", "offerLast", "iterator", "listIterator", "delete", "save", "persist",
            "update", "flush", "close"
    )));

    /**
     * Roots of fully qualified names. A dotted chain starting with one of these is a class reference, not a variable.
     */
    protected static final Set<String> PACKAGE_ROOTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "java", "javax", "jakarta", "jdk", "sun", "com", "org", "net", "io", "groovy", "bsh", "ognl", "freemarker"
    )));

    protected static final Pattern SETTER = Pattern.compile("^set[A-Z_].*");

    protected static final String FORBIDDEN_CHARACTERS = ";{}@#`\\";

    protected MvelRuleSandbox() {
    }

    /**
     * Validates the rule against {@link #DEFAULT_ALLOWED_TYPES} plus the given additional type names.
     *
     * @throws MvelRuleViolationException when the rule leaves the restricted rule language
     */
    public static void validate(String rule, Collection<String> additionalAllowedTypes) {
        if (rule == null || rule.trim().isEmpty()) {
            return;
        }
        Set<String> allowedTypes = new HashSet<>(DEFAULT_ALLOWED_TYPES);
        if (additionalAllowedTypes != null) {
            allowedTypes.addAll(additionalAllowedTypes);
        }
        List<Token> tokens = tokenize(rule);
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.type != TokenType.IDENTIFIER) {
                continue;
            }
            if (BLOCKED_KEYWORDS.contains(token.text)) {
                throw new MvelRuleViolationException("Rules may not use the '" + token.text + "' construct");
            }
            if (isMemberAccess(tokens, i)) {
                if (BLOCKED_MEMBERS.contains(token.text) || SETTER.matcher(token.text).matches()) {
                    throw new MvelRuleViolationException("Rules may not call '" + token.text + "'");
                }
            } else {
                validateRootReference(tokens, i, allowedTypes);
            }
        }
    }

    public static boolean isValid(String rule, Collection<String> additionalAllowedTypes) {
        try {
            validate(rule, additionalAllowedTypes);
            return true;
        } catch (MvelRuleViolationException e) {
            return false;
        }
    }

    /**
     * A root identifier is either a variable (lower case chain) or a type reference. A type reference is the chain up
     * to and including the first segment starting with an upper case letter, and it must be an allowed type.
     */
    protected static void validateRootReference(List<Token> tokens, int index, Set<String> allowedTypes) {
        List<String> segments = new ArrayList<>();
        segments.add(tokens.get(index).text);
        int i = index;
        while (true) {
            int next = i + 1;
            if (next < tokens.size() && tokens.get(next).is(".")) {
                next++;
                if (next < tokens.size() && tokens.get(next).is("?")) {
                    next++;
                }
                if (next < tokens.size() && tokens.get(next).type == TokenType.IDENTIFIER) {
                    segments.add(tokens.get(next).text);
                    i = next;
                    continue;
                }
            }
            break;
        }
        StringBuilder typeName = new StringBuilder();
        for (String segment : segments) {
            if (typeName.length() > 0) {
                typeName.append('.');
            }
            typeName.append(segment);
            if (Character.isUpperCase(segment.charAt(0))) {
                if (!allowedTypes.contains(typeName.toString())) {
                    throw new MvelRuleViolationException("Rules may not reference the type '" + typeName + "'");
                }
                return;
            }
        }
        if (segments.size() > 1 && PACKAGE_ROOTS.contains(segments.get(0))) {
            throw new MvelRuleViolationException("Rules may not reference '" + typeName + "'");
        }
    }

    protected static boolean isMemberAccess(List<Token> tokens, int index) {
        int i = index - 1;
        if (i >= 0 && tokens.get(i).is("?")) {
            i--;
        }
        return i >= 0 && tokens.get(i).is(".");
    }

    protected static List<Token> tokenize(String rule) {
        List<Token> tokens = new ArrayList<>();
        int length = rule.length();
        int i = 0;
        while (i < length) {
            char c = rule.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '"' || c == '\'') {
                int start = i++;
                while (i < length && rule.charAt(i) != c) {
                    i += rule.charAt(i) == '\\' ? 2 : 1;
                }
                if (i >= length) {
                    throw new MvelRuleViolationException("Rule contains an unterminated string literal");
                }
                i++;
                tokens.add(new Token(TokenType.STRING, rule.substring(start, i)));
            } else if (Character.isDigit(c)) {
                int start = i;
                while (i < length && (Character.isLetterOrDigit(rule.charAt(i)) || rule.charAt(i) == '.')) {
                    if (rule.charAt(i) == '.' && (i + 1 >= length || !Character.isDigit(rule.charAt(i + 1)))) {
                        break;
                    }
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER, rule.substring(start, i)));
            } else if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < length && Character.isJavaIdentifierPart(rule.charAt(i))) {
                    i++;
                }
                tokens.add(new Token(TokenType.IDENTIFIER, rule.substring(start, i)));
            } else if (FORBIDDEN_CHARACTERS.indexOf(c) >= 0) {
                throw new MvelRuleViolationException("Rules may not contain the character '" + c + "'");
            } else if ("=!<>".indexOf(c) >= 0 && i + 1 < length && rule.charAt(i + 1) == '=') {
                tokens.add(new Token(TokenType.PUNCTUATION, rule.substring(i, i + 2)));
                i += 2;
            } else if ((c == '+' || c == '-') && i + 1 < length && rule.charAt(i + 1) == c) {
                throw new MvelRuleViolationException("Rules may not use increment or decrement operators");
            } else if (c == '=') {
                throw new MvelRuleViolationException("Rules may not contain assignments");
            } else if (Character.isISOControl(c) || c > 127) {
                throw new MvelRuleViolationException("Rules may not contain the character U+"
                        + String.format("%04X", (int) c) + " outside of a string literal");
            } else {
                tokens.add(new Token(TokenType.PUNCTUATION, String.valueOf(c)));
                i++;
            }
        }
        return tokens;
    }

    protected enum TokenType {
        IDENTIFIER, STRING, NUMBER, PUNCTUATION
    }

    protected static class Token {

        protected final TokenType type;
        protected final String text;

        protected Token(TokenType type, String text) {
            this.type = type;
            this.text = text;
        }

        protected boolean is(String punctuation) {
            return type == TokenType.PUNCTUATION && text.equals(punctuation);
        }
    }

}
