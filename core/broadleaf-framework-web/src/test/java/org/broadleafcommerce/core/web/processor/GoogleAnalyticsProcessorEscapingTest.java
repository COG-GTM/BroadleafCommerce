/*-
 * #%L
 * BroadleafCommerce Framework Web
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
package org.broadleafcommerce.core.web.processor;

import org.broadleafcommerce.core.catalog.domain.Sku;
import org.broadleafcommerce.core.order.domain.DiscreteOrderItem;
import org.broadleafcommerce.core.order.domain.FulfillmentGroup;
import org.broadleafcommerce.core.order.domain.FulfillmentGroupItem;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.domain.OrderItemAttribute;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GoogleAnalyticsProcessorEscapingTest {

    private static final String PAYLOAD = "';alert(1);//</script><script>alert(2)</script>\\";
    private static final String ESCAPED_PAYLOAD =
            "\\';alert(1);\\/\\/\\u003C\\/script\\u003E\\u003Cscript\\u003Ealert(2)\\u003C\\/script\\u003E\\\\";

    @Test
    public void ga4TransactionJsEscapesCustomerControlledValues() {
        GoogleAnalytics4Processor processor = new GoogleAnalytics4Processor() {
            @Override
            public String getAffiliation() {
                return "Acme' + alert(3) + '";
            }
        };

        String js = processor.getTransactionJs(buildOrder());

        Assert.assertTrue(js, js.startsWith("gtag('event', 'purchase',{transaction_id: '1001\\';alert(4);\\/\\/'"));
        Assert.assertTrue(js, js.contains(",affiliation: 'Acme\\' + alert(3) + \\''"));
        Assert.assertTrue(js, js.contains(",item_name: 'Shirt\\' + alert(5) + \\''"));
        Assert.assertTrue(js, js.contains(",item_category: '" + ESCAPED_PAYLOAD + "'"));
        assertNoScriptBreakout(js);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void universalAnalyticsTransactionJsEscapesCustomerControlledValues() {
        GoogleUniversalAnalyticsProcessor processor = new GoogleUniversalAnalyticsProcessor() {
            @Override
            public String getAffiliation() {
                return "Acme' + alert(3) + '";
            }
        };

        String js = processor.getTransactionJs(buildOrder(), "");

        Assert.assertTrue(js, js.contains("'id': '1001\\';alert(4);\\/\\/'"));
        Assert.assertTrue(js, js.contains(",'affiliation': 'Acme\\' + alert(3) + \\''"));
        Assert.assertTrue(js, js.contains(",'name': 'Shirt\\' + alert(5) + \\''"));
        Assert.assertTrue(js, js.contains(",'category': '" + ESCAPED_PAYLOAD + "'"));
        assertNoScriptBreakout(js);
    }

    @Test
    public void escapeJsHandlesNullAndPlainValues() {
        GoogleAnalytics4Processor processor = new GoogleAnalytics4Processor();
        Assert.assertEquals("", processor.escapeJs(null));
        Assert.assertEquals("Blue Large", processor.escapeJs("Blue Large"));
        Assert.assertEquals("42", processor.escapeJs(42L));
    }

    private static void assertNoScriptBreakout(String js) {
        Assert.assertFalse(js, js.contains("<"));
        Assert.assertFalse(js, codeOutsideStringLiterals(js).contains("alert"));
    }

    private static String codeOutsideStringLiterals(String js) {
        StringBuilder code = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < js.length(); i++) {
            char c = js.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '\'') {
                    inString = false;
                }
            } else if (c == '\'') {
                inString = true;
            } else {
                code.append(c);
            }
        }
        Assert.assertFalse("Unterminated string literal: " + js, inString);
        return code.toString();
    }

    private static Order buildOrder() {
        Sku sku = EasyMock.createNiceMock(Sku.class);
        EasyMock.expect(sku.getId()).andReturn(42L).anyTimes();
        EasyMock.expect(sku.getName()).andReturn("Shirt' + alert(5) + '").anyTimes();

        OrderItemAttribute attribute = EasyMock.createNiceMock(OrderItemAttribute.class);
        EasyMock.expect(attribute.getName()).andReturn("note").anyTimes();
        EasyMock.expect(attribute.getValue()).andReturn(PAYLOAD).anyTimes();
        Map<String, OrderItemAttribute> attributes = new HashMap<>();
        attributes.put("note", attribute);

        DiscreteOrderItem orderItem = EasyMock.createNiceMock(DiscreteOrderItem.class);
        EasyMock.expect(orderItem.getSku()).andReturn(sku).anyTimes();
        EasyMock.expect(orderItem.getOrderItemAttributes()).andReturn(attributes).anyTimes();
        EasyMock.expect(orderItem.getQuantity()).andReturn(1).anyTimes();

        FulfillmentGroupItem fulfillmentGroupItem = EasyMock.createNiceMock(FulfillmentGroupItem.class);
        EasyMock.expect(fulfillmentGroupItem.getOrderItem()).andReturn(orderItem).anyTimes();

        FulfillmentGroup fulfillmentGroup = EasyMock.createNiceMock(FulfillmentGroup.class);
        EasyMock.expect(fulfillmentGroup.getFulfillmentGroupItems())
                .andReturn(Collections.singletonList(fulfillmentGroupItem)).anyTimes();

        Order order = EasyMock.createNiceMock(Order.class);
        EasyMock.expect(order.getOrderNumber()).andReturn("1001';alert(4);//").anyTimes();
        EasyMock.expect(order.getFulfillmentGroups())
                .andReturn(Collections.singletonList(fulfillmentGroup)).anyTimes();

        EasyMock.replay(sku, attribute, orderItem, fulfillmentGroupItem, fulfillmentGroup, order);
        return order;
    }

}
