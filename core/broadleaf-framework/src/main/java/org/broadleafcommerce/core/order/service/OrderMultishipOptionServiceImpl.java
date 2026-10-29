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
import org.broadleafcommerce.core.order.domain.DiscreteOrderItem;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.domain.OrderItem;
import org.broadleafcommerce.core.order.domain.OrderMultishipOption;
import org.broadleafcommerce.core.order.domain.OrderMultishipOptionImpl;
import org.broadleafcommerce.core.order.service.call.OrderMultishipOptionDTO;
import org.broadleafcommerce.profile.core.domain.Address;
import org.broadleafcommerce.profile.core.domain.CustomerAddress;
import org.broadleafcommerce.profile.core.service.AddressService;
import org.broadleafcommerce.profile.core.service.CustomerAddressService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jakarta.annotation.Resource;

/**
 * @author Andre Azzolini (apazzolini)
 */
@Service("blOrderMultishipOptionService")
public class OrderMultishipOptionServiceImpl implements OrderMultishipOptionService {

    @Resource(name = "blAddressService")
    protected AddressService addressService;
    @Resource(name = "blCustomerAddressService")
    protected CustomerAddressService customerAddressService;
    @Resource(name = "blOrderItemService")
    protected OrderItemService orderItemService;
    @Resource(name = "blFulfillmentOptionService")
    protected FulfillmentOptionService fulfillmentOptionService;
    @Resource(name = "blFulfillmentGroupService")
    protected FulfillmentGroupService fulfillmentGroupService;
    @Resource(name = "blOrderMultishipOptionDao")
    OrderMultishipOptionDao orderMultishipOptionDao;

    @Override
    public OrderMultishipOption save(OrderMultishipOption orderMultishipOption) {
        return orderMultishipOptionDao.save(orderMultishipOption);
    }

    @Override
    public List<OrderMultishipOption> findOrderMultishipOptions(Long orderId) {
        return orderMultishipOptionDao.readOrderMultishipOptions(orderId);
    }

    @Override
    public List<OrderMultishipOption> findOrderItemOrderMultishipOptions(Long orderItemId) {
        return orderMultishipOptionDao.readOrderItemOrderMultishipOptions(orderItemId);
    }

    @Override
    public OrderMultishipOption create() {
        return orderMultishipOptionDao.create();
    }

    @Override
    public void deleteOrderItemOrderMultishipOptions(Long orderItemId) {
        List<OrderMultishipOption> options = findOrderItemOrderMultishipOptions(orderItemId);
        orderMultishipOptionDao.deleteAll(options);
    }

    @Override
    public void deleteOrderItemOrderMultishipOptions(Long orderItemId, int numToDelete) {
        List<OrderMultishipOption> options = findOrderItemOrderMultishipOptions(orderItemId);
        numToDelete = (numToDelete > options.size()) ? options.size() : numToDelete;
        options = options.subList(0, numToDelete);
        orderMultishipOptionDao.deleteAll(options);
    }

    @Override
    public void deleteAllOrderMultishipOptions(Order order) {
        List<OrderMultishipOption> options = findOrderMultishipOptions(order.getId());
        orderMultishipOptionDao.deleteAll(options);
    }

    @Override
    public void saveOrderMultishipOptions(Order order, List<OrderMultishipOptionDTO> optionDTOs) {
        Map<Long, OrderMultishipOption> currentOptions = new HashMap<>();
        for (OrderMultishipOption option : findOrderMultishipOptions(order.getId())) {
            currentOptions.put(option.getId(), option);
        }
        Map<Long, OrderItem> orderItems = getOrderItemsById(order);
        Map<Long, Address> addresses = getAllowedAddressesById(order, currentOptions.values());

        // Resolve and validate every DTO before mutating any option so a rejected
        // request leaves the order's existing options untouched
        List<OrderItem> resolvedItems = new ArrayList<>();
        List<Address> resolvedAddresses = new ArrayList<>();
        for (OrderMultishipOptionDTO dto : optionDTOs) {
            if (dto.getId() != null && !currentOptions.containsKey(dto.getId())) {
                throw new IllegalArgumentException(
                        "Multiship option " + dto.getId() + " does not belong to order " + order.getId()
                );
            }
            resolvedItems.add(resolveOrderItem(order, orderItems, dto.getOrderItemId()));
            resolvedAddresses.add(resolveAddress(order, addresses, dto.getAddressId()));
        }

        List<OrderMultishipOption> orderMultishipOptions = new ArrayList<OrderMultishipOption>();
        for (int i = 0; i < optionDTOs.size(); i++) {
            OrderMultishipOptionDTO dto = optionDTOs.get(i);
            OrderMultishipOption option = dto.getId() == null ? null : currentOptions.get(dto.getId());
            if (option == null) {
                option = orderMultishipOptionDao.create();
            }

            option.setOrder(order);
            option.setOrderItem(resolvedItems.get(i));
            option.setAddress(resolvedAddresses.get(i));

            if (dto.getFulfillmentOptionId() != null) {
                option.setFulfillmentOption(
                        fulfillmentOptionService.readFulfillmentOptionById(dto.getFulfillmentOptionId())
                );
            } else {
                option.setFulfillmentOption(null);
            }

            orderMultishipOptions.add(option);
        }

        for (OrderMultishipOption option : orderMultishipOptions) {
            save(option);
        }
    }

    @Override
    public List<OrderMultishipOption> getOrGenerateOrderMultishipOptions(Order order) {
        List<OrderMultishipOption> orderMultishipOptions = findOrderMultishipOptions(order.getId());
        if (orderMultishipOptions == null || orderMultishipOptions.size() == 0) {
            orderMultishipOptions = generateOrderMultishipOptions(order);
        }

        // Create a map representing the current discrete order item counts for the order
        Map<Long, Integer> orderDiscreteOrderItemCounts = new HashMap<>();
        for (DiscreteOrderItem item : order.getDiscreteOrderItems()) {
            orderDiscreteOrderItemCounts.put(item.getId(), item.getQuantity());
        }

        List<OrderMultishipOption> optionsToRemove = new ArrayList<>();
        for (OrderMultishipOption option : orderMultishipOptions) {
            Integer count = orderDiscreteOrderItemCounts.get(option.getOrderItem().getId());
            if (count == null || count == 0) {
                optionsToRemove.add(option);
            } else {
                count--;
                orderDiscreteOrderItemCounts.put(option.getOrderItem().getId(), count);
            }
        }

        for (Entry<Long, Integer> entry : orderDiscreteOrderItemCounts.entrySet()) {
            DiscreteOrderItem item = (DiscreteOrderItem) orderItemService.readOrderItemById(entry.getKey());
            orderMultishipOptions.addAll(createPopulatedOrderMultishipOption(order, item, entry.getValue()));
        }

        orderMultishipOptions.removeAll(optionsToRemove);
        orderMultishipOptionDao.deleteAll(optionsToRemove);

        return orderMultishipOptions;
    }

    @Override
    public List<OrderMultishipOption> getOrderMultishipOptionsFromDTOs(
            Order order,
            List<OrderMultishipOptionDTO> optionDtos
    ) {
        List<OrderMultishipOption> currentOptions = findOrderMultishipOptions(order.getId());
        Map<Long, OrderMultishipOption> currentOptionsById = new HashMap<>();
        for (OrderMultishipOption option : currentOptions) {
            currentOptionsById.put(option.getId(), option);
        }
        Map<Long, OrderItem> orderItems = getOrderItemsById(order);
        Map<Long, Address> addresses = getAllowedAddressesById(order, currentOptions);

        List<OrderMultishipOption> orderMultishipOptions = new ArrayList<>();
        for (OrderMultishipOptionDTO optionDto : optionDtos) {
            if (optionDto.getId() != null && !currentOptionsById.containsKey(optionDto.getId())) {
                throw new IllegalArgumentException(
                        "Multiship option " + optionDto.getId() + " does not belong to order " + order.getId()
                );
            }
            OrderMultishipOption option = new OrderMultishipOptionImpl();
            option.setAddress(resolveAddress(order, addresses, optionDto.getAddressId()));
            if (optionDto.getFulfillmentOptionId() != null) {
                option.setFulfillmentOption(fulfillmentOptionService.readFulfillmentOptionById(
                        optionDto.getFulfillmentOptionId()
                ));
            }
            option.setId(optionDto.getId());
            option.setOrder(order);
            option.setOrderItem(resolveOrderItem(order, orderItems, optionDto.getOrderItemId()));
            orderMultishipOptions.add(option);
        }
        return orderMultishipOptions;
    }

    /**
     * Indexes the order items (including discrete items nested in bundles) that belong to the given order.
     * Multiship options may only reference these items.
     */
    protected Map<Long, OrderItem> getOrderItemsById(Order order) {
        Map<Long, OrderItem> orderItems = new HashMap<>();
        if (order.getOrderItems() != null) {
            for (OrderItem item : order.getOrderItems()) {
                if (item.getId() != null) {
                    orderItems.put(item.getId(), item);
                }
            }
        }
        for (DiscreteOrderItem item : order.getDiscreteOrderItems()) {
            if (item.getId() != null) {
                orderItems.put(item.getId(), item);
            }
        }
        return orderItems;
    }

    /**
     * Indexes the addresses a multiship option on this order may reference: the active saved addresses of the
     * order's customer, plus addresses already attached to this order's existing multiship options.
     */
    protected Map<Long, Address> getAllowedAddressesById(Order order, Iterable<OrderMultishipOption> currentOptions) {
        Map<Long, Address> addresses = new HashMap<>();
        for (OrderMultishipOption option : currentOptions) {
            Address address = option.getAddress();
            if (address != null && address.getId() != null) {
                addresses.put(address.getId(), address);
            }
        }
        if (order.getCustomer() != null && order.getCustomer().getId() != null) {
            List<CustomerAddress> customerAddresses = customerAddressService
                    .readActiveCustomerAddressesByCustomerId(order.getCustomer().getId());
            if (customerAddresses != null) {
                for (CustomerAddress customerAddress : customerAddresses) {
                    Address address = customerAddress.getAddress();
                    if (address != null && address.getId() != null) {
                        addresses.put(address.getId(), address);
                    }
                }
            }
        }
        return addresses;
    }

    protected OrderItem resolveOrderItem(Order order, Map<Long, OrderItem> orderItems, Long orderItemId) {
        OrderItem orderItem = orderItemId == null ? null : orderItems.get(orderItemId);
        if (orderItem == null) {
            throw new IllegalArgumentException(
                    "Order item " + orderItemId + " does not belong to order " + order.getId()
            );
        }
        return orderItem;
    }

    protected Address resolveAddress(Order order, Map<Long, Address> addresses, Long addressId) {
        if (addressId == null) {
            return null;
        }
        Address address = addresses.get(addressId);
        if (address == null) {
            throw new IllegalArgumentException(
                    "Address " + addressId + " does not belong to the customer of order " + order.getId()
            );
        }
        return address;
    }

    @Override
    public List<OrderMultishipOption> generateOrderMultishipOptions(Order order) {
        List<OrderMultishipOption> orderMultishipOptions = new ArrayList<>();
        for (DiscreteOrderItem discreteOrderItem : order.getDiscreteOrderItems()) {
            orderMultishipOptions.addAll(createPopulatedOrderMultishipOption(
                    order, discreteOrderItem, discreteOrderItem.getQuantity()
            ));
        }

        return orderMultishipOptions;
    }

    protected List<OrderMultishipOption> createPopulatedOrderMultishipOption(
            Order order,
            DiscreteOrderItem item,
            Integer quantity
    ) {
        List<OrderMultishipOption> orderMultishipOptions = new ArrayList<>();
        if (!fulfillmentGroupService.isShippable(item.getSku().getFulfillmentType()) || quantity == 0) {
            return orderMultishipOptions;
        }
//        for (int i = 0; i < quantity; i++) {
        OrderMultishipOption orderMultishipOption = new OrderMultishipOptionImpl();
        orderMultishipOption.setOrder(order);
        orderMultishipOption.setOrderItem(item);
        orderMultishipOptions.add(orderMultishipOption);
//        }
        return orderMultishipOptions;
    }

}
