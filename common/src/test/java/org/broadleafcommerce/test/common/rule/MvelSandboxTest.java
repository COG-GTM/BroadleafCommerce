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
package org.broadleafcommerce.test.common.rule;

import org.broadleafcommerce.common.locale.domain.Locale;
import org.broadleafcommerce.common.locale.domain.LocaleImpl;
import org.broadleafcommerce.common.rule.MvelHelper;
import org.broadleafcommerce.common.rule.MvelSandbox;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

/**
 * Confirms that a rule can no longer reach outside of the rule DSL to execute arbitrary code.
 */
public class MvelSandboxTest extends TestCase {

    protected static final String[] MALICIOUS_RULES = {
            "Runtime.getRuntime().exec('touch /tmp/blc-mvel-sandbox')",
            "java.lang.Runtime.getRuntime().exec('touch /tmp/blc-mvel-sandbox')",
            "new java.lang.ProcessBuilder('sh', '-c', 'touch /tmp/blc-mvel-sandbox').start()",
            "new ProcessBuilder('sh').start()",
            "locale.getClass().forName('java.lang.Runtime').getRuntime().exec('id') != null",
            "locale.class.getClassLoader() != null",
            "System.exit(1)",
            "System.getProperty('user.home') != null",
            "MVEL.eval('Runtime.getRuntime().exec(\"id\")')",
            "MVEL.executeExpression(rule, this)",
            "MVEL.eval(locale.localeCode)",
            "org.springframework.util.StringUtils.hasText('a')",
            "def hack() { Runtime.getRuntime() }; hack()"
    };

    protected static final String[] LEGITIMATE_RULES = {
            "locale.localeCode == 'US'",
            "request.properties['blcSearchTerm'] == 'hot'",
            "customer.?emailAddress != null && customer.?registered == true",
            "product.?name == 'System Restore File'",
            "MvelHelper.convertField(\"INTEGER\",orderItem.?product.?getProductAttributes()[\"myinteger\"])>0",
            "([MVEL.eval(\"toUpperCase()\",\"test1\")] contains MVEL.eval(\"toUpperCase()\", "
                    + "discreteOrderItem.category.name))",
            "CollectionUtils.intersection(customer.?attributes, [\"gold\"]).size() > 0",
            "orderItem.?product.?getProductAttributes().?get('myattr').?value == 'yes'"
    };

    public void testMaliciousRulesAreRejectedBySandbox() {
        for (String rule : MALICIOUS_RULES) {
            assertFalse("Expected the sandbox to reject: " + rule, MvelSandbox.isValid(rule));
        }
    }

    public void testLegitimateRulesRemainValid() {
        for (String rule : LEGITIMATE_RULES) {
            assertTrue("Expected the sandbox to allow: " + rule, MvelSandbox.isValid(rule));
        }
    }

    /**
     * A rejected rule must never be compiled or executed and must not match.
     */
    public void testMaliciousRuleIsNotExecuted() throws Exception {
        File marker = new File(System.getProperty("java.io.tmpdir"), "blc-mvel-sandbox-" + System.nanoTime());
        String rule = "Runtime.getRuntime().exec(new String[]{'/bin/sh', '-c', 'touch " + marker.getAbsolutePath()
                + "'}) != null";

        MvelHelper.setTestMode(true);
        boolean result = MvelHelper.evaluateRule(rule, new HashMap<String, Object>());
        MvelHelper.setTestMode(false);

        assertFalse(result);
        // give a spawned process a moment to land on disk before asserting it never started
        Thread.sleep(500);
        assertFalse("The sandboxed rule executed a process", marker.exists());
    }

    /**
     * A blocked token appearing inside of rule data must not cause a legitimate rule to be rejected.
     */
    public void testBlockedTokenInsideStringLiteralIsData() {
        Locale testLocale = new LocaleImpl();
        testLocale.setLocaleCode("Runtime");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("locale", testLocale);

        assertTrue(MvelHelper.evaluateRule("locale.localeCode == 'Runtime'", parameters));
    }

    public void testSandboxClassLoaderRefusesDangerousTypes() {
        ClassLoader sandbox = MvelSandbox.getSandboxClassLoader(getClass().getClassLoader());
        try {
            sandbox.loadClass("java.lang.Runtime");
            fail("Expected java.lang.Runtime to be unresolvable from a rule");
        } catch (ClassNotFoundException expected) {
            // expected
        }
        try {
            assertNotNull(sandbox.loadClass("java.math.BigDecimal"));
        } catch (ClassNotFoundException e) {
            fail("Expected an innocuous type to still resolve: " + e.getMessage());
        }
    }

}
