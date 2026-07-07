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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mvel2.MVEL;
import org.mvel2.compiler.AbstractParser;

import java.io.FilePermission;
import java.net.SocketPermission;
import java.security.Permission;
import java.util.Map;
import java.util.PropertyPermission;

/**
 * Runtime hardening for the untrusted MVEL rule strings evaluated by {@link MvelHelper}.
 *
 * <p>Broadleaf rule strings are authored through the admin rule-builder (offer/customer/CMS targeting,
 * SKU fee expressions, etc.) and later evaluated with MVEL. MVEL is Turing-complete: a rule string can
 * reference arbitrary Java classes (directly, via {@code new}, or reflectively through
 * {@code someValue.getClass()}) and therefore reach {@code java.lang.Runtime}/{@code ProcessBuilder} for
 * remote code execution. Escaping the rule-builder input alone cannot close this because reflective
 * access ({@code obj.getClass().forName(...)}, dynamic property names, nested {@code MVEL.eval(...)})
 * bypasses any string/AST allow-list.</p>
 *
 * <p>This class therefore contains the <em>effects</em> of rule evaluation rather than trying to
 * sanitize the input:</p>
 * <ul>
 *   <li>A thread-scoped {@link SecurityManager} denies the dangerous operations (process execution,
 *       JVM exit, native linking, file writes, network access, thread manipulation, security-manager
 *       replacement and reflective access to non-public members) <strong>only</strong> while the
 *       current thread is inside {@link MvelHelper} rule evaluation. Outside of rule evaluation every
 *       permission is delegated unchanged, so the rest of the application is unaffected. No matter how
 *       an attacker reaches {@code Runtime.exec}/{@code ProcessBuilder.start}, the terminal operation is
 *       blocked.</li>
 *   <li>The dangerous class literals MVEL exposes by default ({@code Runtime}, {@code System},
 *       {@code Class}, {@code ClassLoader}, {@code Thread}, {@code Array}) are removed from MVEL's global
 *       literal table so the most direct payloads fail to compile rather than depending solely on the
 *       (deprecated) SecurityManager.</li>
 * </ul>
 *
 * <p>The sandbox can be disabled with {@code -Dbroadleaf.mvel.sandbox.enabled=false}, but doing so
 * re-opens the remote-code-execution path and should only be done in environments that never persist
 * admin/rule-builder input.</p>
 */
final class MvelSandbox {

    private static final Log LOG = LogFactory.getLog(MvelSandbox.class);

    /**
     * System property used to disable the runtime sandbox. Defaults to enabled.
     */
    public static final String SANDBOX_ENABLED_PROPERTY = "broadleaf.mvel.sandbox.enabled";

    /**
     * Per-thread re-entrancy counter. A value greater than zero means the current thread is actively
     * evaluating an MVEL rule and the restrictive policy must be enforced. A mutable {@code int[]} holder
     * is used so nested evaluations do not incur repeated {@link ThreadLocal#set(Object)} calls, and so
     * the counter cannot be neutralized via public API (the field is private and reaching it reflectively
     * requires {@code accessDeclaredMembers}, which the policy denies during evaluation).
     */
    private static final ThreadLocal<int[]> ACTIVE_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private MvelSandbox() {
    }

    /**
     * Installs the sandbox. Safe to call multiple times; only the first call has an effect.
     */
    static void initialize() {
        neutralizeDangerousClassLiterals();
        installSecurityManager();
    }

    /**
     * Marks the beginning of a rule evaluation on the current thread. Must be paired with {@link #exit()}
     * in a {@code finally} block.
     */
    static void enter() {
        ACTIVE_DEPTH.get()[0]++;
    }

    /**
     * Marks the end of a rule evaluation on the current thread.
     */
    static void exit() {
        int[] depth = ACTIVE_DEPTH.get();
        if (depth[0] > 0) {
            depth[0]--;
        }
    }

    private static boolean isRuleEvaluationActive() {
        return ACTIVE_DEPTH.get()[0] > 0;
    }

    private static boolean isSandboxEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(SANDBOX_ENABLED_PROPERTY, "true"));
    }

    /**
     * Removes the dangerous class literals that MVEL registers globally so that the most direct RCE
     * payloads (e.g. {@code Runtime.getRuntime().exec(...)}) fail to resolve at compile time. This is
     * defense-in-depth on top of the SecurityManager; the safe value literals (String, Integer, Math,
     * etc.) are intentionally preserved.
     */
    private static void neutralizeDangerousClassLiterals() {
        try {
            // Force MVEL to populate its static literal tables before we prune them.
            MVEL.compileExpression("true");
            String[] dangerous = {"System", "Runtime", "ClassLoader", "Class", "Thread", "Array"};
            removeAll(AbstractParser.LITERALS, dangerous);
            removeAll(AbstractParser.CLASS_LITERALS, dangerous);
        } catch (Throwable t) {
            LOG.warn("Unable to neutralize dangerous MVEL class literals; relying on the runtime sandbox instead", t);
        }
    }

    private static void removeAll(Map<String, Object> map, String[] keys) {
        if (map != null) {
            for (String key : keys) {
                map.remove(key);
            }
        }
    }

    @SuppressWarnings("removal")
    private static void installSecurityManager() {
        if (!isSandboxEnabled()) {
            LOG.warn("The MVEL rule evaluation sandbox is disabled via '" + SANDBOX_ENABLED_PROPERTY
                    + "=false'. Untrusted rule strings are able to execute arbitrary code; only disable this "
                    + "in environments that never persist admin/rule-builder input.");
            return;
        }
        try {
            SecurityManager current = System.getSecurityManager();
            if (current instanceof RuleEvaluationSecurityManager) {
                return;
            }
            System.setSecurityManager(new RuleEvaluationSecurityManager(current));
        } catch (Throwable t) {
            LOG.warn("Unable to install the MVEL rule evaluation SecurityManager sandbox. Rule evaluation "
                    + "will proceed with reduced isolation against remote code execution. If this JVM evaluates "
                    + "admin-authored rules, start it with '-Djava.security.manager=allow' (Java 17+) so the "
                    + "sandbox can be installed.", t);
        }
    }

    /**
     * A {@link SecurityManager} that enforces a deny-list of dangerous capabilities, but only while the
     * current thread is evaluating an MVEL rule (see {@link #isRuleEvaluationActive()}). All other checks
     * are delegated to the previously installed manager (if any), so behavior outside rule evaluation is
     * unchanged.
     */
    @SuppressWarnings("removal")
    private static final class RuleEvaluationSecurityManager extends SecurityManager {

        private final SecurityManager delegate;

        private RuleEvaluationSecurityManager(SecurityManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public void checkPermission(Permission perm) {
            if (isRuleEvaluationActive()) {
                enforce(perm);
            }
            if (delegate != null) {
                delegate.checkPermission(perm);
            }
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            if (isRuleEvaluationActive()) {
                enforce(perm);
            }
            if (delegate != null) {
                delegate.checkPermission(perm, context);
            }
        }

        private void enforce(Permission perm) {
            if (perm instanceof FilePermission) {
                String actions = perm.getActions();
                if (actions != null && (actions.contains("write") || actions.contains("delete")
                        || actions.contains("execute"))) {
                    throw deny("file access (" + actions + ")");
                }
                // Read access is required for legitimate class/resource loading during evaluation.
                return;
            }
            if (perm instanceof SocketPermission) {
                throw deny("network access");
            }
            if (perm instanceof PropertyPermission) {
                String actions = perm.getActions();
                if (actions != null && actions.contains("write")) {
                    throw deny("system property modification");
                }
                return;
            }
            if (perm instanceof RuntimePermission) {
                String name = perm.getName();
                if (name.equals("setSecurityManager")
                        || name.equals("createSecurityManager")
                        || name.equals("setIO")
                        || name.equals("shutdownHooks")
                        || name.startsWith("exitVM")
                        || name.equals("modifyThread")
                        || name.equals("modifyThreadGroup")
                        || name.equals("stopThread")
                        || name.startsWith("loadLibrary")
                        || name.equals("setContextClassLoader")
                        // Blocks reflective access to non-public members, e.g. tampering with the
                        // sandbox's own private state or reaching internal/JDK internals.
                        || name.equals("accessDeclaredMembers")) {
                    throw deny("restricted runtime capability (" + name + ")");
                }
            }
        }

        @Override
        public void checkExec(String cmd) {
            if (isRuleEvaluationActive()) {
                throw deny("process execution");
            }
            if (delegate != null) {
                delegate.checkExec(cmd);
            }
        }

        @Override
        public void checkExit(int status) {
            if (isRuleEvaluationActive()) {
                throw deny("JVM exit");
            }
            if (delegate != null) {
                delegate.checkExit(status);
            }
        }

        @Override
        public void checkLink(String lib) {
            if (isRuleEvaluationActive()) {
                throw deny("native library linking");
            }
            if (delegate != null) {
                delegate.checkLink(lib);
            }
        }

        @Override
        public void checkAccess(Thread t) {
            if (isRuleEvaluationActive()) {
                throw deny("thread access");
            }
            if (delegate != null) {
                delegate.checkAccess(t);
            }
        }

        @Override
        public void checkAccess(ThreadGroup g) {
            if (isRuleEvaluationActive()) {
                throw deny("thread group access");
            }
            if (delegate != null) {
                delegate.checkAccess(g);
            }
        }

        private static SecurityException deny(String capability) {
            return new SecurityException("Broadleaf MVEL rule sandbox blocked " + capability
                    + " during rule evaluation");
        }
    }
}
