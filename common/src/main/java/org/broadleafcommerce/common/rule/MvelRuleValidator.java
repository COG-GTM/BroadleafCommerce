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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Restricts MVEL rules to side-effect free predicates over the variables passed to the rule.
 * <p>
 * Offer, page, structured content and similar rules are authored in the admin and evaluated on the storefront. MVEL
 * resolves fully qualified class names and {@code java.lang} types (Runtime, System, Class, Thread, ...) on its own,
 * so an unrestricted rule can run arbitrary Java. Rules are therefore checked before they are stored and before they
 * are compiled:
 * <ul>
 *     <li>a type may only be referenced if it is an allowed type or a context import, and only allowed static
 *     members of {@link MvelHelper} may be called;</li>
 *     <li>reflective, class loading, process, mutating and setter members are denied;</li>
 *     <li>statements, blocks, assignments, object creation, imports and function definitions are denied.</li>
 * </ul>
 * String literal contents are not inspected, so rule data that merely mentions a denied name is unaffected.
 */
public class MvelRuleValidator {

    /**
     * Context imports every rule is compiled with by {@link MvelHelper}.
     */
    public static final Set<String> DEFAULT_ALLOWED_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "MvelHelper", "CollectionUtils"
    )));

    /**
     * Fully qualified types emitted by the admin rule builder.
     */
    public static final Set<String> ALLOWED_QUALIFIED_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "org.apache.commons.lang3.StringUtils"
    )));

    /**
     * Static members of {@link MvelHelper} that rules may call.
     */
    public static final Set<String> ALLOWED_MVEL_HELPER_MEMBERS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("convertField", "toUpperCase", "currentTime", "subtractFromCurrentTime", "parseLegacyDate")
    ));

    protected static final Set<String> DENIED_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "new", "import", "import_static", "def", "function", "class", "proto", "typeof", "with", "foreach",
            "for", "while", "until", "do", "if", "else", "return", "assert", "switch", "var", "this"
    )));

    protected static final Set<String> DENIED_MEMBERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "class", "getClass", "forName", "newInstance", "getRuntime", "exec", "exit", "halt", "load",
            "loadLibrary", "loadClass", "defineClass", "getClassLoader", "getSystemClassLoader",
            "getContextClassLoader", "currentThread", "invoke", "invokeExact", "invokeWithArguments", "lookup",
            "getMethod", "getMethods", "getDeclaredMethod", "getDeclaredMethods", "getField", "getFields",
            "getDeclaredField", "getDeclaredFields", "getConstructor", "getConstructors", "getDeclaredConstructor",
            "getDeclaredConstructors", "setAccessible", "getSuperclass", "getDeclaringClass", "getEnclosingClass",
            "getProtectionDomain", "getModule", "getResource", "getResourceAsStream", "getenv", "doPrivileged", "wait", "notify", "notifyAll", "readObject", "writeObject",
            "compileExpression", "executeExpression", "eval", "evaluateRule", "getBean", "getApplicationContext",
            "getServletContext", "getSession", "add", "addAll", "remove", "removeAll", "removeIf", "retainAll",
            "clear", "put", "putAll", "putIfAbsent", "replaceAll", "compute", "computeIfAbsent", "computeIfPresent",
            "merge", "set", "sort", "delete", "save", "persist", "update", "flush", "close"
    )));

    /**
     * Roots of fully qualified names. A dotted chain starting with one of these is a class reference, not a variable.
     */
    protected static final Set<String> PACKAGE_ROOTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "java", "javax", "jakarta", "jdk", "sun", "com", "org", "net", "io", "groovy", "bsh", "ognl"
    )));

    protected static final Set<String> OPERATOR_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "in", "contains", "instanceof", "is", "soundslike", "strsim", "convertable_to", "and", "or", "return"
    )));

        protected static final Pattern SETTER = Pattern.compile("set[A-Z_].*");

    protected static final String DENIED_CHARACTERS = ";{}@#$`\\";

    protected static final Pattern LEGACY_TO_UPPER_CASE =
            Pattern.compile("MVEL\\s*\\.\\s*eval\\s*\\(\\s*([\"'])toUpperCase\\(\\)\\1\\s*,");

    protected static final Pattern LEGACY_DATE_PARSE = Pattern.compile(
            "java\\s*\\.\\s*text\\s*\\.\\s*DateFormat\\s*\\.\\s*getDateTimeInstance\\s*\\(\\s*3\\s*,\\s*3\\s*\\)"
                    + "\\s*\\.\\s*parse\\s*\\(");

    protected MvelRuleValidator() {
    }

    /**
     * Validates the rule against {@link #DEFAULT_ALLOWED_TYPES} plus the given additional context import names.
     *
     * @throws UnsafeMvelRuleException when the rule leaves the restricted rule language
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
            if (token.type == TokenType.STRING) {
                validateIndexedMember(tokens, i);
                continue;
            }
            if (token.type != TokenType.IDENTIFIER) {
                continue;
            }
            if (DENIED_KEYWORDS.contains(token.text)) {
                throw new UnsafeMvelRuleException("Rules may not use the '" + token.text + "' construct");
            }
            if (isMemberAccess(tokens, i)) {
                validateMember(token.text);
            } else {
                validateRootReference(tokens, i, allowedTypes);
            }
        }
    }

    public static boolean isValid(String rule, Collection<String> additionalAllowedTypes) {
        try {
            validate(rule, additionalAllowedTypes);
            return true;
        } catch (UnsafeMvelRuleException e) {
            return false;
        }
    }

    /**
     * Rewrites the calls emitted by older versions of the rule builder, {@code MVEL.eval("toUpperCase()", value)} and
     * {@code java.text.DateFormat.getDateTimeInstance(3,3).parse(value)}, to {@link MvelHelper#toUpperCase(String)}
     * and {@link MvelHelper#parseLegacyDate(String)} so that legacy rules keep working without access to
     * {@code MVEL} or {@code java.text}. String literals are left untouched.
     */
    public static String rewriteLegacyExpressions(String rule) {
        if (rule == null || (!rule.contains("MVEL") && !rule.contains("DateFormat"))) {
            return rule;
        }
        StringBuilder sb = new StringBuilder(rule.length());
        Matcher toUpperCase = LEGACY_TO_UPPER_CASE.matcher(rule);
        Matcher dateParse = LEGACY_DATE_PARSE.matcher(rule);
        int length = rule.length();
        int i = 0;
        while (i < length) {
            char c = rule.charAt(i);
            if (c == '"' || c == '\'') {
                int end = skipStringLiteral(rule, i);
                sb.append(rule, i, end);
                i = end;
            } else if (toUpperCase.region(i, length).lookingAt()) {
                sb.append("MvelHelper.toUpperCase(");
                i = toUpperCase.end();
            } else if (dateParse.region(i, length).lookingAt()) {
                sb.append("MvelHelper.parseLegacyDate(");
                i = dateParse.end();
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * Returns the code of the rule with the contents of every string literal removed, so that two rules can be
     * compared for structural equality regardless of the data they contain.
     *
     * @throws UnsafeMvelRuleException when the rule cannot be tokenized
     */
    public static String codeSkeleton(String rule) {
        StringBuilder sb = new StringBuilder();
        for (Token token : tokenize(rule)) {
            sb.append(token.type == TokenType.STRING ? "\"\"" : token.text).append(' ');
        }
        return sb.toString();
    }

    protected static void validateMember(String member) {
        if (DENIED_MEMBERS.contains(member) || SETTER.matcher(member).matches()) {
            throw new UnsafeMvelRuleException("Rules may not access '" + member + "'");
        }
    }

    /**
     * {@code value['getClass']} style property access is treated like {@code value.getClass}.
     */
    protected static void validateIndexedMember(List<Token> tokens, int index) {
        if (index > 1 && tokens.get(index - 1).is("[") && isIndexTarget(tokens.get(index - 2))) {
            String text = tokens.get(index).text;
            String name = text.substring(1, text.length() - 1).trim();
            if (DENIED_MEMBERS.contains(name) || SETTER.matcher(name).matches()) {
                throw new UnsafeMvelRuleException("Rules may not access '" + name + "'");
            }
        }
    }

    /**
     * A root identifier is either a variable or a type reference. Identifiers starting with an upper case letter, and
     * dotted chains rooted in a package name or reaching an upper case segment before any call or index, are type
     * references and must be allowed.
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
        String root = segments.get(0);
        if (Character.isUpperCase(root.charAt(0))) {
            if (!allowedTypes.contains(root)) {
                throw new UnsafeMvelRuleException("Rules may not reference the type '" + root + "'");
            }
            if ("MvelHelper".equals(root) && (segments.size() < 2
                    || !ALLOWED_MVEL_HELPER_MEMBERS.contains(segments.get(1)))) {
                throw new UnsafeMvelRuleException("Rules may not access '" + String.join(".", segments) + "'");
            }
            return;
        }
        StringBuilder typeName = new StringBuilder(root);
        for (int s = 1; s < segments.size(); s++) {
            typeName.append('.').append(segments.get(s));
            if (Character.isUpperCase(segments.get(s).charAt(0))) {
                if (!ALLOWED_QUALIFIED_TYPES.contains(typeName.toString())) {
                    throw new UnsafeMvelRuleException("Rules may not reference the type '" + typeName + "'");
                }
                return;
            }
        }
        if (segments.size() > 1 && PACKAGE_ROOTS.contains(root)) {
            throw new UnsafeMvelRuleException("Rules may not reference '" + typeName + "'");
        }
    }

    /**
     * Whether a {@code [} following the token indexes into a value ({@code a['x']}, {@code a()['x']},
     * {@code a[0]['x']}) rather than opening a list literal ({@code ['x', 'y']}).
     */
    protected static boolean isIndexTarget(Token token) {
        return token.type == TokenType.IDENTIFIER && !OPERATOR_KEYWORDS.contains(token.text)
                || token.type == TokenType.STRING || token.is(")") || token.is("]");
    }

    protected static boolean isMemberAccess(List<Token> tokens, int index) {
        int i = index - 1;
        if (i >= 0 && tokens.get(i).is("?")) {
            i--;
        }
        return i >= 0 && tokens.get(i).is(".");
    }

    protected static int skipStringLiteral(String rule, int start) {
        char quote = rule.charAt(start);
        int i = start + 1;
        while (i < rule.length() && rule.charAt(i) != quote) {
            i += rule.charAt(i) == '\\' ? 2 : 1;
        }
        if (i >= rule.length()) {
            throw new UnsafeMvelRuleException("Rule contains an unterminated string literal");
        }
        return i + 1;
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
                int end = skipStringLiteral(rule, i);
                tokens.add(new Token(TokenType.STRING, rule.substring(i, end)));
                i = end;
            } else if (Character.isDigit(c)) {
                int start = i;
                while (i < length && (Character.isLetterOrDigit(rule.charAt(i)) || rule.charAt(i) == '.')) {
                    if (rule.charAt(i) == '.' && (i + 1 >= length || !Character.isDigit(rule.charAt(i + 1)))) {
                        break;
                    }
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER, rule.substring(start, i)));
            } else if (Character.isJavaIdentifierStart(c) && c != '$') {
                int start = i;
                while (i < length && Character.isJavaIdentifierPart(rule.charAt(i)) && rule.charAt(i) != '$') {
                    i++;
                }
                tokens.add(new Token(TokenType.IDENTIFIER, rule.substring(start, i)));
            } else if (DENIED_CHARACTERS.indexOf(c) >= 0) {
                throw new UnsafeMvelRuleException("Rules may not contain the character '" + c + "'");
            } else if ("=!<>".indexOf(c) >= 0 && i + 1 < length && rule.charAt(i + 1) == '=') {
                tokens.add(new Token(TokenType.PUNCTUATION, rule.substring(i, i + 2)));
                i += 2;
            } else if ("+-*/%&|^".indexOf(c) >= 0 && i + 1 < length && rule.charAt(i + 1) == '='
                    || (c == '+' || c == '-') && i + 1 < length && rule.charAt(i + 1) == c) {
                throw new UnsafeMvelRuleException("Rules may not contain assignments");
            } else if (c == '=') {
                throw new UnsafeMvelRuleException("Rules may not contain assignments");
            } else if (Character.isISOControl(c) || c > 127) {
                throw new UnsafeMvelRuleException("Rules may not contain the character U+"
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
