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

import org.broadleafcommerce.common.rule.MvelRuleSandbox;
import org.broadleafcommerce.common.rule.MvelRuleViolationException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import junit.framework.TestCase;

public class MvelRuleSandboxTest extends TestCase {

    private static final Collection<String> OFFER_IMPORTS = Arrays.asList("OfferType", "FulfillmentType");

    public void testAllowsRuleBuilderOutput() {
        String[] rules = {
                "locale.localeCode == 'US'",
                "request.properties['blcSearchTerm'] == 'hot'",
                "MvelHelper.toUpperCase(?discreteOrderItem.?category.?name)==MvelHelper.toUpperCase(\"merchandise\")",
                "?customer.?emailAddress!=customer.username",
                "CollectionUtils.intersection(product.?allParentCategoryIds,[\"2002\",\"2003\"]).size()>0",
                "(MvelHelper.convertField(\"DATE\",?customer.?getCustomerAttributes()[\"invoice_date\"])"
                        + ">MvelHelper.convertField(\"DATE\",MvelHelper.subtractFromCurrentTime(12)))",
                "order.subTotal.getAmount()>=100.00 && !(order.customer.registered)",
                "discreteOrderItem.?sku.?name contains \"Hot\" || discreteOrderItem.quantity > 2",
                "orderItem.isOnSale() ? orderItem.salePrice.amount > 10 : false",
                "fulfillmentGroup.type == FulfillmentType.PHYSICAL_SHIP",
                "offer.type == OfferType.ORDER_ITEM",
                "!StringUtils.isBlank(customer.firstName) && Math.abs(-1) == 1",
                "customer.firstName == 'java.lang.Runtime.getRuntime().exec(\"x\")'",
                "customer.firstName == 'new Foo(); System.exit(1)'"
        };
        for (String rule : rules) {
            assertTrue(rule, MvelRuleSandbox.isValid(rule, OFFER_IMPORTS));
        }
    }

    public void testRejectsCodeExecution() {
        String[] rules = {
                "java.lang.Runtime.getRuntime().exec(\"touch /tmp/pwned\")",
                "java . lang . Runtime . getRuntime()",
                "java.?lang.?Runtime.getRuntime()",
                "Runtime.getRuntime()",
                "ProcessBuilder",
                "javax.script.ScriptEngineManager",
                "org.mvel2.MVEL.eval('1')",
                "MVEL.eval(\"Runtime.getRuntime()\")",
                "MVEL.eval(\"toUpperCase()\", x)",
                "MvelHelper.class",
                "customer.getClass().forName('java.lang.Runtime')",
                "customer.class.classLoader",
                "x ? Runtime : null",
                "new java.io.File('/tmp/x').delete()",
                "customer.firstName = 'x'",
                "customer.setFirstName('x')",
                "customer.firstName == 'x'; System.exit(1)",
                "def x() { 1 }",
                "import java.lang.Runtime",
                "foreach (x : items) { x }",
                "@{1}",
                "Thread.sleep(1000)",
                "System.getProperty('user.home')",
                "customer.firstName == 'x",
                "OfferType.ORDER_ITEM"
        };
        for (String rule : rules) {
            try {
                MvelRuleSandbox.validate(rule, Collections.<String>emptySet());
                fail("Expected rule to be rejected: " + rule);
            } catch (MvelRuleViolationException e) {
                // expected
            }
        }
    }

    public void testBlankRuleIsValid() {
        assertTrue(MvelRuleSandbox.isValid(null, null));
        assertTrue(MvelRuleSandbox.isValid("  ", null));
    }

}
