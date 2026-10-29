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

import org.broadleafcommerce.common.rule.MvelHelper;
import org.broadleafcommerce.common.rule.MvelRuleValidator;
import org.broadleafcommerce.common.rule.UnsafeMvelRuleException;

import java.io.File;
import java.nio.file.Files;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

public class MvelRuleValidatorTest extends TestCase {

    private static final List<String> OFFER_IMPORTS = Arrays.asList("OfferType", "FulfillmentType");

    public void testRuleBuilderOutputIsAllowed() {
        String[] rules = {
                "MvelHelper.toUpperCase(?discreteOrderItem.?category.?name)==MvelHelper.toUpperCase(\"merchandise\")",
                "CollectionUtils.intersection(?discreteOrderItem.?category.?name,[\"test category\", \"test category 2\"]).size()>0",
                "(MvelHelper.convertField(\"DATE\",?customer.?getCustomerAttributes()[\"invoice_date\"])"
                        + ">MvelHelper.convertField(\"DATE\",MvelHelper.subtractFromCurrentTime(12))"
                        + "&&(MvelHelper.convertField(\"DATE\",?customer.?getCustomerAttributes()[\"invoice_date\"])"
                        + "<MvelHelper.convertField(\"DATE\",MvelHelper.currentTime())))",
                "customer.emailAddress!=customer.username&&customer.deactivated==true",
                "order.subTotal.getAmount()>=100.5&&!(order.currency.defaultFlag==false)",
                "discreteOrderItem.?sku.?retailPrice.getAmount()<MvelHelper.convertField(\"DECIMAL\",\"10\")",
                "org.apache.commons.lang3.StringUtils.isNotBlank(customer.firstName)",
                "offer.type==OfferType.ORDER_ITEM&&fulfillmentGroup.type==FulfillmentType.PHYSICAL_SHIP",
                "customer.firstName==\"java.lang.Runtime.getRuntime().exec(\\\"id\\\"); new Thread()\"",
                "x == null ? false : x.name contains 'a'",
                "MVEL.eval(\"toUpperCase()\",customer.firstName)==\"BOB\"",
                "java.text.DateFormat.getDateTimeInstance(3,3).parse(\"1/1/20 1:00 PM\")!=null",
                "CollectionUtils.intersection(discreteOrderItem.category.name,[\"set\", \"getClass\"]).size()>0",
                "discreteOrderItem.category.name in ['add','remove']"
        };
        for (String rule : rules) {
            MvelHelper.validateRule(rule, OFFER_IMPORTS);
        }
    }

    public void testCodeExecutionIsRejected() {
        String[] rules = {
                "Runtime.getRuntime().exec(\"id\")!=null",
                "java.lang.Runtime.getRuntime().exec(\"id\")!=null",
                "a==\"x\" || java . lang . Runtime . getRuntime() != null",
                "new java.lang.ProcessBuilder(\"id\").start()!=null",
                "ProcessBuilder(\"id\")!=null",
                "System.exit(0)",
                "Thread.currentThread().getContextClassLoader()!=null",
                "customer.getClass().forName(\"java.lang.Runtime\")!=null",
                "customer.class.classLoader!=null",
                "customer.?getClass()!=null",
                "customer['class']!=null",
                "customer.attributes()['getClass']!=null",
                "customer.list[0]['getClass']!=null",
                "Class.forName(\"java.lang.Runtime\")!=null",
                "MVEL.eval(\"java.lang.Runtime.getRuntime()\")!=null",
                "MvelHelper.evaluateRule(\"true\", null)",
                "org.mvel2.MVEL.eval(\"1\")!=null",
                "javax.script.ScriptEngineManager!=null",
                "customer.firstName=\"x\"",
                "customer.setFirstName(\"x\")!=null",
                "customer.firstName++",
                "order.orderItems.clear()==null",
                "def f() { true }; f()",
                "import java.lang.Runtime; true",
                "foreach (x : order.orderItems) { x }",
                "@{1}",
                "customer.firstName==\"unterminated",
                "OfferType.ORDER==null"
        };
        for (String rule : rules) {
            assertFalse(rule, MvelRuleValidator.isValid(MvelRuleValidator.rewriteLegacyExpressions(rule),
                    Collections.emptyList()));
        }
    }

    public void testLegacyExpressionsAreRewritten() {
        assertEquals("MvelHelper.toUpperCase(customer.firstName)==\"MVEL.eval(\\\"toUpperCase()\\\",\"",
                MvelRuleValidator.rewriteLegacyExpressions(
                        "MVEL.eval(\"toUpperCase()\",customer.firstName)==\"MVEL.eval(\\\"toUpperCase()\\\",\""));
        assertEquals("MvelHelper.parseLegacyDate(\"1/1/20 1:00 PM\")",
                MvelRuleValidator.rewriteLegacyExpressions("java.text.DateFormat.getDateTimeInstance(3,3).parse(\"1/1/20 1:00 PM\")"));
    }

    public void testValidateThrowsDescriptiveException() {
        try {
            MvelRuleValidator.validate("Runtime.getRuntime()", Collections.emptyList());
            fail();
        } catch (UnsafeMvelRuleException e) {
            assertTrue(e.getMessage().contains("Runtime"));
        }
    }

    public void testUnsafeRuleIsNotExecuted() throws Exception {
        File marker = new File(Files.createTempDirectory("mvel-rule").toFile(), "pwned");
        String[] rules = {
                "Runtime.getRuntime().exec(\"touch " + marker.getAbsolutePath() + "\")!=null",
                "java.lang.Runtime.getRuntime().exec(\"touch " + marker.getAbsolutePath() + "\")!=null",
                "MVEL.eval(\"java.lang.Runtime.getRuntime().exec('touch " + marker.getAbsolutePath() + "')\")!=null"
        };
        for (String rule : rules) {
            assertFalse(rule, MvelHelper.evaluateRule(rule, new HashMap<>()));
            // a cached rejection must also evaluate to false
            Map<String, java.io.Serializable> cache = new HashMap<>();
            assertFalse(MvelHelper.evaluateRule(rule, new HashMap<>(), cache));
            assertFalse(MvelHelper.evaluateRule(rule, new HashMap<>(), cache));
        }
        Thread.sleep(200);
        assertFalse(marker.exists());
    }

    public void testUnvalidatedCacheEntriesAreIgnored() throws Exception {
        File marker = new File(Files.createTempDirectory("mvel-rule").toFile(), "pwned");
        String rule = "true";
        Map<String, java.io.Serializable> cache = new HashMap<>();
        cache.put(rule, org.mvel2.MVEL.compileExpression(
                "java.lang.Runtime.getRuntime().exec(\"touch " + marker.getAbsolutePath() + "\")==null"));
        assertTrue(MvelHelper.evaluateRule(rule, new HashMap<>(), cache));
        Thread.sleep(200);
        assertFalse(marker.exists());
    }

    public void testAllowedRulesStillEvaluate() {
        Map<String, Object> vars = new HashMap<>();
        Map<String, String> customer = new HashMap<>();
        customer.put("firstName", "bob");
        vars.put("customer", customer);
        assertTrue(MvelHelper.evaluateRule("MvelHelper.toUpperCase(customer.firstName)==\"BOB\"", vars));
        assertTrue(MvelHelper.evaluateRule("MVEL.eval(\"toUpperCase()\",customer.firstName)==\"BOB\"", vars));
        assertTrue(MvelHelper.evaluateRule(
                "CollectionUtils.intersection([customer.firstName],[\"bob\", \"alice\"]).size()>0", vars));
        String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date());
        assertTrue(MvelHelper.evaluateRule(
                "java.text.DateFormat.getDateTimeInstance(3,3).parse(\"" + date + "\")!=null", vars));
    }

}
