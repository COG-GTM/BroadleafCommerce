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
package org.broadleafcommerce.core.spec.order.service

import org.broadleafcommerce.core.order.dao.OrderMultishipOptionDao
import org.broadleafcommerce.core.order.domain.DiscreteOrderItem
import org.broadleafcommerce.core.order.domain.Order
import org.broadleafcommerce.core.order.domain.OrderMultishipOption
import org.broadleafcommerce.core.order.domain.OrderMultishipOptionImpl
import org.broadleafcommerce.core.order.service.FulfillmentOptionService
import org.broadleafcommerce.core.order.service.OrderItemService
import org.broadleafcommerce.core.order.service.OrderMultishipOptionServiceImpl
import org.broadleafcommerce.core.order.service.call.OrderMultishipOptionDTO
import org.broadleafcommerce.profile.core.domain.Address
import org.broadleafcommerce.profile.core.domain.Customer
import org.broadleafcommerce.profile.core.domain.CustomerAddress
import org.broadleafcommerce.profile.core.service.AddressService
import org.broadleafcommerce.profile.core.service.CustomerAddressService
import spock.lang.Specification

class OrderMultishipOptionServiceImplSpec extends Specification {

    OrderMultishipOptionServiceImpl service
    OrderMultishipOptionDao dao = Mock()
    CustomerAddressService customerAddressService = Mock()
    AddressService addressService = Mock()
    OrderItemService orderItemService = Mock()
    FulfillmentOptionService fulfillmentOptionService = Mock()

    Order order = Mock()
    Customer customer = Mock()
    DiscreteOrderItem ownItem = Mock()
    Address ownAddress = Mock()
    CustomerAddress ownCustomerAddress = Mock()

    def setup() {
        service = new OrderMultishipOptionServiceImpl(
                orderMultishipOptionDao: dao,
                customerAddressService: customerAddressService,
                addressService: addressService,
                orderItemService: orderItemService,
                fulfillmentOptionService: fulfillmentOptionService
        )
        order.getId() >> 10L
        order.getCustomer() >> customer
        customer.getId() >> 100L
        ownItem.getId() >> 1L
        order.getDiscreteOrderItems() >> [ownItem]
        ownAddress.getId() >> 5L
        ownCustomerAddress.getAddress() >> ownAddress
        customerAddressService.readActiveCustomerAddressesByCustomerId(100L) >> [ownCustomerAddress]
        dao.readOrderMultishipOptions(10L) >> []
    }

    def "saving options that reference the order's own item and the customer's own address succeeds"() {
        given:
        OrderMultishipOption created = new OrderMultishipOptionImpl()

        when:
        service.saveOrderMultishipOptions(order, [dto(null, 1L, 5L)])

        then:
        1 * dao.create() >> created
        1 * dao.save(created) >> created
        created.orderItem.is(ownItem)
        created.address.is(ownAddress)
        0 * addressService.readAddressById(_)
        0 * orderItemService.readOrderItemById(_)
    }

    def "an address id belonging to another customer is rejected and nothing is saved"() {
        when:
        service.saveOrderMultishipOptions(order, [dto(null, 1L, 5L), dto(null, 1L, 999L)])

        then:
        thrown(IllegalArgumentException)
        0 * dao.create()
        0 * dao.save(_)
        0 * addressService.readAddressById(_)
    }

    def "an order item id from another order is rejected and nothing is saved"() {
        when:
        service.saveOrderMultishipOptions(order, [dto(null, 777L, 5L)])

        then:
        thrown(IllegalArgumentException)
        0 * dao.save(_)
        0 * orderItemService.readOrderItemById(_)
    }

    def "a missing order item id is rejected"() {
        when:
        service.saveOrderMultishipOptions(order, [dto(null, null, null)])

        then:
        thrown(IllegalArgumentException)
        0 * dao.save(_)
    }

    def "an option id that does not belong to the order is rejected"() {
        when:
        service.saveOrderMultishipOptions(order, [dto(42L, 1L, 5L)])

        then:
        thrown(IllegalArgumentException)
        0 * dao.save(_)
    }

    def "an existing option of the order keeps its already attached address"() {
        given:
        Address previouslyAttached = Mock()
        previouslyAttached.getId() >> 6L
        OrderMultishipOption existing = new OrderMultishipOptionImpl(id: 3L, address: previouslyAttached)
        Order orderWithOption = Mock()
        orderWithOption.getId() >> 11L
        orderWithOption.getCustomer() >> customer
        orderWithOption.getDiscreteOrderItems() >> [ownItem]
        dao.readOrderMultishipOptions(11L) >> [existing]

        when:
        service.saveOrderMultishipOptions(orderWithOption, [dto(3L, 1L, 6L)])

        then:
        0 * dao.create()
        1 * dao.save(existing) >> existing
        existing.address.is(previouslyAttached)
    }

    def "getOrderMultishipOptionsFromDTOs applies the same ownership checks"() {
        when:
        def options = service.getOrderMultishipOptionsFromDTOs(order, [dto(null, 1L, 5L)])

        then:
        options.size() == 1
        options[0].orderItem.is(ownItem)
        options[0].address.is(ownAddress)

        when:
        service.getOrderMultishipOptionsFromDTOs(order, [dto(null, 1L, 999L)])

        then:
        thrown(IllegalArgumentException)

        when:
        service.getOrderMultishipOptionsFromDTOs(order, [dto(null, 777L, null)])

        then:
        thrown(IllegalArgumentException)
    }

    def "a guest order without a customer id may only use addresses already on its options"() {
        given:
        Order guestOrder = Mock()
        guestOrder.getId() >> 12L
        guestOrder.getCustomer() >> null
        guestOrder.getDiscreteOrderItems() >> [ownItem]
        dao.readOrderMultishipOptions(12L) >> []

        when:
        service.saveOrderMultishipOptions(guestOrder, [dto(null, 1L, 5L)])

        then:
        thrown(IllegalArgumentException)
        0 * customerAddressService.readActiveCustomerAddressesByCustomerId(_)
    }

    private static OrderMultishipOptionDTO dto(Long id, Long orderItemId, Long addressId) {
        new OrderMultishipOptionDTO(id: id, orderItemId: orderItemId, addressId: addressId)
    }
}
