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

import org.broadleafcommerce.common.currency.domain.BroadleafCurrency;
import org.broadleafcommerce.common.money.Money;
import org.broadleafcommerce.core.catalog.domain.Sku;
import org.broadleafcommerce.core.order.domain.DiscreteOrderItem;
import org.broadleafcommerce.core.order.domain.FulfillmentGroup;
import org.broadleafcommerce.core.order.domain.FulfillmentGroupItem;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.domain.OrderItemAttribute;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class GoogleUniversalAnalyticsProcessorTest {

    private static final String BREAKOUT = "</script><script>alert(1)</script>";
    private static final String QUOTE_BREAKOUT = "';alert(2);//";

    private Order order;

    @Before
    public void setUp() {
        Map<String, OrderItemAttribute> attributes = new LinkedHashMap<>();
        attributes.put("note", attribute("note", BREAKOUT));
        attributes.put("x", attribute("x", QUOTE_BREAKOUT));

        Sku sku = EasyMock.createNiceMock(Sku.class);
        EasyMock.expect(sku.getId()).andReturn(42L).anyTimes();
        EasyMock.expect(sku.getName()).andReturn("Hot Sauce\\'</script>").anyTimes();

        DiscreteOrderItem item = EasyMock.createNiceMock(DiscreteOrderItem.class);
        EasyMock.expect(item.getSku()).andReturn(sku).anyTimes();
        EasyMock.expect(item.getOrderItemAttributes()).andReturn(attributes).anyTimes();
        EasyMock.expect(item.getAveragePrice()).andReturn(new Money(new BigDecimal("9.99"))).anyTimes();
        EasyMock.expect(item.getQuantity()).andReturn(2).anyTimes();

        FulfillmentGroupItem fgItem = EasyMock.createNiceMock(FulfillmentGroupItem.class);
        EasyMock.expect(fgItem.getOrderItem()).andReturn(item).anyTimes();

        FulfillmentGroup fg = EasyMock.createNiceMock(FulfillmentGroup.class);
        EasyMock.expect(fg.getFulfillmentGroupItems()).andReturn(Collections.singletonList(fgItem)).anyTimes();

        BroadleafCurrency currency = EasyMock.createNiceMock(BroadleafCurrency.class);
        EasyMock.expect(currency.getCurrencyCode()).andReturn("USD'</script>").anyTimes();

        order = EasyMock.createNiceMock(Order.class);
        EasyMock.expect(order.getOrderNumber()).andReturn("ORD-1'</script>").anyTimes();
        EasyMock.expect(order.getTotal()).andReturn(new Money(new BigDecimal("19.98"))).anyTimes();
        EasyMock.expect(order.getTotalShipping()).andReturn(new Money(new BigDecimal("5.00"))).anyTimes();
        EasyMock.expect(order.getTotalTax()).andReturn(new Money(new BigDecimal("1.25"))).anyTimes();
        EasyMock.expect(order.getCurrency()).andReturn(currency).anyTimes();
        EasyMock.expect(order.getFulfillmentGroups()).andReturn(Collections.singletonList(fg)).anyTimes();

        EasyMock.replay(sku, item, fgItem, fg, currency, order);
    }

    private static OrderItemAttribute attribute(String name, String value) {
        OrderItemAttribute attribute = EasyMock.createNiceMock(OrderItemAttribute.class);
        EasyMock.expect(attribute.getName()).andReturn(name).anyTimes();
        EasyMock.expect(attribute.getValue()).andReturn(value).anyTimes();
        EasyMock.replay(attribute);
        return attribute;
    }

    private static void assertNoBreakout(String js) {
        Assert.assertFalse(js, js.contains("<"));
        Assert.assertFalse(js, js.contains(">"));
        Assert.assertFalse(js, js.contains(QUOTE_BREAKOUT));
        Assert.assertFalse(js, js.contains("ORD-1'"));
        Assert.assertFalse(js, js.contains("USD'"));
    }

    private GoogleUniversalAnalyticsProcessor processor(final String affiliation) {
        return new GoogleUniversalAnalyticsProcessor() {
            @Override
            public String getAffiliation() {
                return affiliation;
            }
        };
    }

    @Test
    public void testTransactionJsEscapesOrderAndItemValues() {
        String js = processor("Acme'</script>").getTransactionJs(order, "master.");

        assertNoBreakout(js);
        Assert.assertTrue(js, js.contains("'id': 'ORD-1\\'\\u003C\\/script\\u003E'"));
        Assert.assertTrue(js, js.contains(",'affiliation': 'Acme\\'\\u003C\\/script\\u003E'"));
        Assert.assertTrue(js, js.contains(",'currency': 'USD\\'\\u003C\\/script\\u003E'"));
        Assert.assertTrue(js, js.contains(",'name': 'Hot Sauce\\\\\\'\\u003C\\/script\\u003E'"));
        Assert.assertTrue(js, js.contains(",'sku': '42'"));
        Assert.assertTrue(js, js.contains(",'category': '\\u003C\\/script\\u003E\\u003Cscript\\u003Ealert(1)"
                + "\\u003C\\/script\\u003E \\';alert(2);\\/\\/'"));
        Assert.assertTrue(js, js.contains(",'revenue': '19.98'"));
        Assert.assertTrue(js, js.contains(",'price': '9.99'"));
        Assert.assertTrue(js, js.contains(",'quantity': '2'"));
    }

    @Test
    public void testSetValuesJsEscapesKeysAndValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("dimension1", QUOTE_BREAKOUT);
        values.put("x');alert(3);//", BREAKOUT);
        values.put(null, "ignored");

        String js = processor(null).getSetValuesJs(values, "");

        assertNoBreakout(js);
        Assert.assertFalse(js, js.contains("ignored"));
        Assert.assertEquals("ga('set', 'dimension1', '\\';alert(2);\\/\\/');"
                + "ga('set', 'x\\');alert(3);\\/\\/', '\\u003C\\/script\\u003E\\u003Cscript\\u003Ealert(1)"
                + "\\u003C\\/script\\u003E');", js);
        Assert.assertEquals("", processor(null).getSetValuesJs(null, ""));
    }

    @Test
    public void testVariationStillReturnsRawValues() {
        DiscreteOrderItem item = (DiscreteOrderItem) order.getFulfillmentGroups().get(0)
                .getFulfillmentGroupItems().get(0).getOrderItem();
        Assert.assertEquals(BREAKOUT + " " + QUOTE_BREAKOUT, processor(null).getVariation(item));
    }

}
