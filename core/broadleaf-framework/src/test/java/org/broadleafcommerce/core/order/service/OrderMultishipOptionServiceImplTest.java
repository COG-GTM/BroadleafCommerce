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

import org.broadleafcommerce.core.order.dao.OrderMultishipOptionDao;
import org.broadleafcommerce.core.order.domain.DiscreteOrderItemImpl;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.domain.OrderImpl;
import org.broadleafcommerce.core.order.domain.OrderItem;
import org.broadleafcommerce.core.order.domain.OrderMultishipOption;
import org.broadleafcommerce.core.order.domain.OrderMultishipOptionImpl;
import org.broadleafcommerce.core.order.service.call.OrderMultishipOptionDTO;
import org.broadleafcommerce.profile.core.domain.Address;
import org.broadleafcommerce.profile.core.domain.AddressImpl;
import org.broadleafcommerce.profile.core.domain.CustomerAddress;
import org.broadleafcommerce.profile.core.domain.CustomerAddressImpl;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.broadleafcommerce.profile.core.service.AddressService;
import org.broadleafcommerce.profile.core.service.CustomerAddressService;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OrderMultishipOptionServiceImplTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final Long OWN_ITEM_ID = 1000L;
    private static final Long FOREIGN_ITEM_ID = 2000L;
    private static final Long OWN_ADDRESS_ID = 10L;
    private static final Long FOREIGN_ADDRESS_ID = 20L;

    private OrderMultishipOptionServiceImpl service;
    private OrderMultishipOptionDao dao;
    private CustomerAddressService customerAddressService;
    private OrderItemService orderItemService;
    private AddressService addressService;
    private Order order;
    private Address ownAddress;
    private OrderItem ownItem;
    private List<OrderMultishipOption> saved;

    @Before
    public void setUp() {
        dao = EasyMock.createMock(OrderMultishipOptionDao.class);
        customerAddressService = EasyMock.createMock(CustomerAddressService.class);
        // Any primary-key lookup by client-supplied ID is a failure: these mocks have no expectations
        orderItemService = EasyMock.createStrictMock(OrderItemService.class);
        addressService = EasyMock.createStrictMock(AddressService.class);

        service = new OrderMultishipOptionServiceImpl();
        service.orderMultishipOptionDao = dao;
        service.customerAddressService = customerAddressService;
        service.orderItemService = orderItemService;
        service.addressService = addressService;

        CustomerImpl customer = new CustomerImpl();
        customer.setId(CUSTOMER_ID);

        ownItem = new DiscreteOrderItemImpl();
        ownItem.setId(OWN_ITEM_ID);

        order = new OrderImpl();
        order.setId(ORDER_ID);
        order.setCustomer(customer);
        List<OrderItem> items = new ArrayList<>();
        items.add(ownItem);
        order.setOrderItems(items);

        ownAddress = new AddressImpl();
        ownAddress.setId(OWN_ADDRESS_ID);
        CustomerAddress customerAddress = new CustomerAddressImpl();
        customerAddress.setCustomer(customer);
        customerAddress.setAddress(ownAddress);

        saved = new ArrayList<>();
        EasyMock.expect(dao.readOrderMultishipOptions(ORDER_ID))
                .andReturn(new ArrayList<OrderMultishipOption>()).anyTimes();
        EasyMock.expect(dao.create()).andAnswer(OrderMultishipOptionImpl::new).anyTimes();
        EasyMock.expect(dao.save(EasyMock.anyObject(OrderMultishipOption.class))).andAnswer(() -> {
            OrderMultishipOption option = (OrderMultishipOption) EasyMock.getCurrentArguments()[0];
            saved.add(option);
            return option;
        }).anyTimes();
        EasyMock.expect(customerAddressService.readActiveCustomerAddressesByCustomerId(CUSTOMER_ID))
                .andReturn(Collections.singletonList(customerAddress)).anyTimes();
        EasyMock.replay(dao, customerAddressService, orderItemService, addressService);
    }

    private OrderMultishipOptionDTO dto(Long id, Long orderItemId, Long addressId) {
        OrderMultishipOptionDTO dto = new OrderMultishipOptionDTO();
        dto.setId(id);
        dto.setOrderItemId(orderItemId);
        dto.setAddressId(addressId);
        return dto;
    }

    private void assertRejected(Runnable call) {
        try {
            call.run();
            Assert.fail("Expected the multiship request to be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        Assert.assertTrue("No option may be saved for a rejected request", saved.isEmpty());
        EasyMock.verify(orderItemService, addressService);
    }

    @Test
    public void saveAttachesOwnedAddressAndOrderItem() {
        service.saveOrderMultishipOptions(order, Collections.singletonList(dto(null, OWN_ITEM_ID, OWN_ADDRESS_ID)));

        Assert.assertEquals(1, saved.size());
        Assert.assertSame(order, saved.get(0).getOrder());
        Assert.assertSame(ownItem, saved.get(0).getOrderItem());
        Assert.assertSame(ownAddress, saved.get(0).getAddress());
        EasyMock.verify(orderItemService, addressService);
    }

    @Test
    public void saveAllowsNullAddress() {
        service.saveOrderMultishipOptions(order, Collections.singletonList(dto(null, OWN_ITEM_ID, null)));

        Assert.assertEquals(1, saved.size());
        Assert.assertNull(saved.get(0).getAddress());
    }

    @Test
    public void saveRejectsAnotherCustomersAddress() {
        assertRejected(() -> service.saveOrderMultishipOptions(
                order, Collections.singletonList(dto(null, OWN_ITEM_ID, FOREIGN_ADDRESS_ID))
        ));
    }

    @Test
    public void saveRejectsAnotherOrdersItem() {
        assertRejected(() -> service.saveOrderMultishipOptions(
                order, Collections.singletonList(dto(null, FOREIGN_ITEM_ID, OWN_ADDRESS_ID))
        ));
    }

    @Test
    public void saveRejectsMissingOrderItem() {
        assertRejected(() -> service.saveOrderMultishipOptions(
                order, Collections.singletonList(dto(null, null, OWN_ADDRESS_ID))
        ));
    }

    @Test
    public void saveRejectsOptionIdFromAnotherOrder() {
        assertRejected(() -> service.saveOrderMultishipOptions(
                order, Collections.singletonList(dto(999L, OWN_ITEM_ID, OWN_ADDRESS_ID))
        ));
    }

    @Test
    public void saveRejectsWholeRequestWhenAnyOptionIsForeign() {
        List<OrderMultishipOptionDTO> dtos = new ArrayList<>();
        dtos.add(dto(null, OWN_ITEM_ID, OWN_ADDRESS_ID));
        dtos.add(dto(null, OWN_ITEM_ID, FOREIGN_ADDRESS_ID));
        assertRejected(() -> service.saveOrderMultishipOptions(order, dtos));
    }

    @Test
    public void saveRejectsAddressWhenOrderHasNoCustomer() {
        order.setCustomer(null);
        assertRejected(() -> service.saveOrderMultishipOptions(
                order, Collections.singletonList(dto(null, OWN_ITEM_ID, OWN_ADDRESS_ID))
        ));
    }

    @Test
    public void fromDtosResolvesOwnedEntities() {
        List<OrderMultishipOption> options = service.getOrderMultishipOptionsFromDTOs(
                order, Collections.singletonList(dto(null, OWN_ITEM_ID, OWN_ADDRESS_ID))
        );

        Assert.assertEquals(1, options.size());
        Assert.assertSame(ownItem, options.get(0).getOrderItem());
        Assert.assertSame(ownAddress, options.get(0).getAddress());
        EasyMock.verify(orderItemService, addressService);
    }

    @Test
    public void fromDtosRejectsAnotherCustomersAddress() {
        assertRejected(() -> service.getOrderMultishipOptionsFromDTOs(
                order, Collections.singletonList(dto(null, OWN_ITEM_ID, FOREIGN_ADDRESS_ID))
        ));
    }

    @Test
    public void fromDtosRejectsAnotherOrdersItem() {
        assertRejected(() -> service.getOrderMultishipOptionsFromDTOs(
                order, Collections.singletonList(dto(null, FOREIGN_ITEM_ID, OWN_ADDRESS_ID))
        ));
    }

    @Test
    public void fromDtosRejectsOptionIdFromAnotherOrder() {
        assertRejected(() -> service.getOrderMultishipOptionsFromDTOs(
                order, Collections.singletonList(dto(999L, OWN_ITEM_ID, OWN_ADDRESS_ID))
        ));
    }

}
