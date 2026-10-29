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
package org.broadleafcommerce.core.order.service;

import org.broadleafcommerce.common.money.Money;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.service.call.OrderItemRequestDTO;

import junit.framework.TestCase;

public class OrderServiceImplPriceOverrideTest extends TestCase {

    public void testAddItemClearsOverridePricesOnNestedChildRequests() throws Exception {
        OrderItemRequestDTO parent = request(1L, "10.00");
        OrderItemRequestDTO child = request(2L, "0.01");
        OrderItemRequestDTO grandChild = request(3L, "0.02");
        child.getChildOrderItems().add(grandChild);
        parent.getChildOrderItems().add(child);

        CapturingOrderService service = new CapturingOrderService();
        service.addItem(100L, parent, false);

        assertSame(parent, service.captured);
        assertNoOverrides(parent);
        assertNoOverrides(child);
        assertNoOverrides(grandChild);
        assertEquals(Long.valueOf(2L), child.getProductId());
        assertEquals(Integer.valueOf(1), child.getQuantity());
        assertEquals(Long.valueOf(3L), grandChild.getProductId());
    }

    public void testAddItemToleratesNullChildList() throws Exception {
        OrderItemRequestDTO parent = request(1L, "10.00");
        parent.setChildOrderItems(null);

        new CapturingOrderService().addItem(100L, parent, false);

        assertNoOverrides(parent);
    }

    public void testAddItemWithPriceOverridesKeepsChildOverrides() throws Exception {
        OrderItemRequestDTO parent = request(1L, "10.00");
        OrderItemRequestDTO child = request(2L, "0.01");
        parent.getChildOrderItems().add(child);

        CapturingOrderService service = new CapturingOrderService();
        service.addItemWithPriceOverrides(100L, parent, false);

        assertEquals(new Money("10.00"), parent.getOverrideSalePrice());
        assertEquals(new Money("0.01"), child.getOverrideSalePrice());
        assertEquals(new Money("0.01"), child.getOverrideRetailPrice());
    }

    protected OrderItemRequestDTO request(Long productId, String price) {
        OrderItemRequestDTO dto = new OrderItemRequestDTO(productId, 1);
        dto.setOverrideSalePrice(new Money(price));
        dto.setOverrideRetailPrice(new Money(price));
        return dto;
    }

    protected void assertNoOverrides(OrderItemRequestDTO dto) {
        assertNull(dto.getOverrideSalePrice());
        assertNull(dto.getOverrideRetailPrice());
    }

    protected static class CapturingOrderService extends OrderServiceImpl {

        protected OrderItemRequestDTO captured;

        @Override
        public Order addItemWithPriceOverrides(Long orderId, OrderItemRequestDTO orderItemRequestDTO, boolean priceOrder) {
            captured = orderItemRequestDTO;
            return null;
        }
    }
}
