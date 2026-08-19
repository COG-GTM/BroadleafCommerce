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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Restricts what an MVEL rule is allowed to reach at compile and execution time.
 *
 * <p>
 * MVEL is a full expression language: it can instantiate types and invoke any method reachable from an expression
 * (for example {@code Runtime.getRuntime().exec(...)}), which turns every rule string into code executing with the
 * privileges of the storefront or admin JVM. Broadleaf rules are only meant to be boolean predicates over the domain
 * objects handed to the expression, so a rule that reaches a type, member or language construct outside of that
 * contract is rejected before it is ever compiled. Type resolution performed during compilation is additionally
 * routed through {@link SandboxClassLoader} so that dangerous types cannot be resolved by any other route.
 *
 * @see MvelHelper#evaluateRule(String, java.util.Map, java.util.Map, java.util.Map)
 */
public class MvelSandbox {

    /**
     * Types that must never be reachable from a rule. Matched as whole tokens, so a property such as
     * <tt>systemName</tt> or a domain method such as <tt>getFileName()</tt> is unaffected.
     */
    protected static final Set<String> BLOCKED_IDENTIFIERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // process and runtime control
            "Runtime", "ProcessBuilder", "Process", "ProcessHandle", "Thread", "ThreadGroup", "System",
            // class loading and reflection
            "Class", "ClassLoader", "URLClassLoader", "Method", "Field", "Constructor", "Proxy", "AccessController",
            "MethodHandle", "MethodHandles", "VarHandle", "Unsafe", "Instrumentation", "Reflection", "SecurityManager",
            // scripting engines
            "ScriptEngine", "ScriptEngineManager", "ScriptContext", "GroovyShell", "Compiler",
            // io, network and naming
            "File", "FileWriter", "FileReader", "FileInputStream", "FileOutputStream", "RandomAccessFile", "Files",
            "Paths", "Socket", "ServerSocket", "URL", "URI", "URLConnection", "ObjectInputStream", "ObjectOutputStream",
            "InitialContext", "Naming"
    )));

    /**
     * Members that must never be invoked from a rule, regardless of the receiver. These are the members that make
     * reflective escapes, class loading and process execution possible.
     */
    protected static final Set<String> BLOCKED_MEMBERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "getClass", "forName", "newInstance", "getRuntime", "exec", "exit", "halt", "loadLibrary", "load",
            "loadClass", "defineClass", "getClassLoader", "getContextClassLoader", "setContextClassLoader",
            "setAccessible", "getMethod", "getMethods", "getDeclaredMethod", "getDeclaredMethods", "getField",
            "getFields", "getDeclaredField", "getDeclaredFields", "getConstructor", "getConstructors",
            "getDeclaredConstructor", "getDeclaredConstructors", "getSuperclass", "getDeclaringClass",
            "getProtectionDomain", "getPermissions", "getModule", "getResource", "getResourceAsStream", "invoke",
            "doPrivileged", "currentThread", "readObject", "writeObject", "readResolve", "wait", "notify",
            "notifyAll", "getProperty", "setProperty", "getenv", "lookup", "compileExpression", "executeExpression"
    )));

    /**
     * Language constructs a rule has no business using. {@code new} covers arbitrary instantiation, the remainder
     * cover MVEL's ability to declare imports, functions and class literals from inside of an expression.
     */
    protected static final Set<String> BLOCKED_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "new", "import", "def", "function", "class", "typeof", "proto", "protoimport"
    )));

    /**
     * Package roots that indicate the rule is trying to reach a type by its fully qualified name.
     */
    protected static final Set<String> BLOCKED_PACKAGE_ROOTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "java", "javax", "jakarta", "sun", "jdk", "com", "org", "net", "groovy", "kotlin", "scala"
    )));

    /**
     * The only members permitted to take an expression as an argument. Rules generated by the rule builder use
     * {@code MVEL.eval("toUpperCase()", someValue)}, so the nested expression is validated as if it were a rule of
     * its own and it must be supplied as a literal rather than built at runtime.
     */
    protected static final Set<String> NESTED_EXPRESSION_MEMBERS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList("eval", "evalToBoolean", "evalToString", "evalToInteger")));

    /**
     * The {@link org.mvel2.MVEL} facade is imported into every rule context, but only its evaluation helpers are
     * meaningful to a rule. Everything else it exposes (compilation, property mutation, class resolution) is denied.
     */
    protected static final Set<String> ALLOWED_MVEL_MEMBERS = NESTED_EXPRESSION_MEMBERS;

    protected static final String MVEL_IMPORT = "MVEL";

    protected static final int MAX_NESTING_DEPTH = 3;

    protected MvelSandbox() {
        // utility class
    }

    /**
     * Validates that the given rule stays within the rule DSL that Broadleaf supports.
     *
     * @param rule the MVEL expression that is about to be compiled
     * @throws MvelSandboxException when the rule reaches a blocked type, member or language construct
     */
    public static void validate(String rule) {
        validate(rule, 0);
    }

    /**
     * Returns true when the rule is safe to compile and execute.
     */
    public static boolean isValid(String rule) {
        try {
            validate(rule);
            return true;
        } catch (MvelSandboxException e) {
            return false;
        }
    }

    /**
     * Returns a {@link ClassLoader} that refuses to resolve the types a rule must not reach. MVEL resolves the types
     * named in an expression through the {@link org.mvel2.ParserConfiguration} class loader, so installing this on
     * the parser context keeps such references from resolving during compilation and execution.
     */
    public static ClassLoader getSandboxClassLoader(ClassLoader parent) {
        return new SandboxClassLoader(parent == null ? MvelSandbox.class.getClassLoader() : parent);
    }

    protected static void validate(String rule, int depth) {
        if (rule == null || rule.trim().isEmpty()) {
            return;
        }
        if (depth > MAX_NESTING_DEPTH) {
            throw new MvelSandboxException("The rule nests expressions more deeply than is supported");
        }

        List<Token> tokens = tokenize(rule);
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.type != TokenType.IDENTIFIER) {
                continue;
            }
            String text = token.text;
            boolean memberAccess = isMemberAccess(tokens, i);

            if (BLOCKED_MEMBERS.contains(text)) {
                throw new MvelSandboxException("The rule uses the disallowed member '" + text + "'");
            }
            if (BLOCKED_KEYWORDS.contains(text)) {
                throw new MvelSandboxException("The rule uses the disallowed construct '" + text + "'");
            }
            if (!memberAccess && BLOCKED_IDENTIFIERS.contains(text)) {
                throw new MvelSandboxException("The rule references the disallowed type '" + text + "'");
            }
            if (!memberAccess && BLOCKED_PACKAGE_ROOTS.contains(text) && isFollowedBy(tokens, i, ".")) {
                throw new MvelSandboxException("The rule references the fully qualified type starting with '"
                        + text + "'");
            }
            if (!memberAccess && MVEL_IMPORT.equals(text) && isFollowedBy(tokens, i, ".")) {
                validateMvelFacadeMember(tokens, i);
            }
            if (NESTED_EXPRESSION_MEMBERS.contains(text) && isFollowedBy(tokens, i, "(")) {
                validateNestedExpression(tokens, i, depth);
            }
        }
    }

    /**
     * The argument of a nested expression call must be a string literal, and that literal is itself a rule that has
     * to pass validation. Anything built at runtime is rejected since its contents cannot be inspected.
     */
    protected static void validateNestedExpression(List<Token> tokens, int index, int depth) {
        Token argument = index + 2 < tokens.size() ? tokens.get(index + 2) : null;
        if (argument == null || argument.type != TokenType.STRING) {
            throw new MvelSandboxException("A nested expression may only be supplied as a literal");
        }
        validate(argument.text, depth + 1);
    }

    /**
     * Only the evaluation helpers of the imported {@link org.mvel2.MVEL} facade may be called from a rule.
     */
    protected static void validateMvelFacadeMember(List<Token> tokens, int index) {
        Token member = index + 2 < tokens.size() ? tokens.get(index + 2) : null;
        if (member == null || member.type != TokenType.IDENTIFIER || !ALLOWED_MVEL_MEMBERS.contains(member.text)) {
            throw new MvelSandboxException("The rule uses an unsupported member of the MVEL facade");
        }
    }

    protected static boolean isMemberAccess(List<Token> tokens, int index) {
        int i = index - 1;
        if (i >= 0 && tokens.get(i).is("?")) {
            i--;
        }
        return i >= 0 && tokens.get(i).is(".");
    }

    protected static boolean isFollowedBy(List<Token> tokens, int index, String punctuation) {
        return index + 1 < tokens.size() && tokens.get(index + 1).is(punctuation);
    }

    /**
     * Splits the expression into identifiers, string literals and single character punctuation. String literals are
     * captured rather than scanned as code so that rule data (a product name containing the word "System", for
     * instance) is never mistaken for an attempt to reach a blocked type.
     */
    protected static List<Token> tokenize(String expression) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < expression.length()) {
            char c = expression.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '\'' || c == '"') {
                StringBuilder literal = new StringBuilder();
                i++;
                while (i < expression.length() && expression.charAt(i) != c) {
                    if (expression.charAt(i) == '\\' && i + 1 < expression.length()) {
                        literal.append(expression.charAt(i)).append(expression.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    literal.append(expression.charAt(i));
                    i++;
                }
                i++;
                tokens.add(new Token(TokenType.STRING, unescape(literal.toString())));
            } else if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < expression.length() && Character.isJavaIdentifierPart(expression.charAt(i))) {
                    i++;
                }
                tokens.add(new Token(TokenType.IDENTIFIER, expression.substring(start, i)));
            } else {
                tokens.add(new Token(TokenType.PUNCTUATION, String.valueOf(c)));
                i++;
            }
        }
        return tokens;
    }

    /**
     * Resolves the escapes that MVEL itself would resolve, so that a nested expression cannot hide a blocked token
     * behind an escape sequence.
     */
    protected static String unescape(String literal) {
        StringBuilder unescaped = new StringBuilder(literal.length());
        int i = 0;
        while (i < literal.length()) {
            char c = literal.charAt(i);
            if (c == '\\' && i + 1 < literal.length()) {
                char next = literal.charAt(i + 1);
                if ((next == 'u' || next == 'U') && i + 5 < literal.length()) {
                    try {
                        unescaped.append((char) Integer.parseInt(literal.substring(i + 2, i + 6), 16));
                        i += 6;
                        continue;
                    } catch (NumberFormatException e) {
                        // fall through and treat the sequence literally
                    }
                }
                unescaped.append(next);
                i += 2;
                continue;
            }
            unescaped.append(c);
            i++;
        }
        return unescaped.toString();
    }

    protected enum TokenType {
        IDENTIFIER, STRING, PUNCTUATION
    }

    protected static class Token {

        protected final TokenType type;
        protected final String text;

        public Token(TokenType type, String text) {
            this.type = type;
            this.text = text;
        }

        public boolean is(String punctuation) {
            return type == TokenType.PUNCTUATION && text.equals(punctuation);
        }
    }

    /**
     * Blocks resolution of the packages and types that would allow a rule to escape the rule DSL.
     */
    protected static class SandboxClassLoader extends ClassLoader {

        protected static final String[] BLOCKED_PACKAGES = {
                "java.lang.reflect.", "java.lang.invoke.", "java.nio.file.", "java.net.",
                "javax.script.", "javax.naming.", "sun.", "jdk.internal.", "groovy.", "org.codehaus.groovy."
        };

        protected static final String[] BLOCKED_CLASSES = {
                "java.lang.Runtime", "java.lang.ProcessBuilder", "java.lang.Process", "java.lang.ProcessHandle",
                "java.lang.Thread", "java.lang.ThreadGroup", "java.lang.System", "java.lang.Class",
                "java.lang.ClassLoader", "java.lang.SecurityManager", "java.io.File", "java.io.FileWriter",
                "java.io.FileReader", "java.io.FileInputStream", "java.io.FileOutputStream",
                "java.io.RandomAccessFile", "java.io.ObjectInputStream", "java.io.ObjectOutputStream"
        };

        public SandboxClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (isBlocked(name)) {
                throw new ClassNotFoundException("Type " + name + " may not be used from an MVEL rule");
            }
            return super.loadClass(name);
        }

        protected boolean isBlocked(String name) {
            if (name == null) {
                return false;
            }
            for (String blocked : BLOCKED_CLASSES) {
                if (name.equals(blocked)) {
                    return true;
                }
            }
            for (String blockedPackage : BLOCKED_PACKAGES) {
                if (name.startsWith(blockedPackage)) {
                    return true;
                }
            }
            return false;
        }
    }

}
