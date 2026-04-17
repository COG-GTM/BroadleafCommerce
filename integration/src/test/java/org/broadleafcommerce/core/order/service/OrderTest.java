/*-
 * #%L
 * BroadleafCommerce Integration
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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Predicate;
import org.broadleafcommerce.core.catalog.dao.SkuDao;
import org.broadleafcommerce.core.catalog.domain.Product;
import org.broadleafcommerce.core.catalog.domain.Sku;
import org.broadleafcommerce.core.order.domain.DiscreteOrderItem;
import org.broadleafcommerce.core.order.domain.FulfillmentGroup;
import org.broadleafcommerce.core.order.domain.FulfillmentGroupItem;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.domain.OrderItem;
import org.broadleafcommerce.core.order.service.call.OrderItemRequestDTO;
import org.broadleafcommerce.core.order.service.exception.AddToCartException;
import org.broadleafcommerce.core.order.service.exception.RemoveFromCartException;
import org.broadleafcommerce.core.order.service.exception.UpdateCartException;
import org.broadleafcommerce.core.order.service.type.OrderStatus;
import org.broadleafcommerce.core.payment.PaymentInfoDataProvider;
import org.broadleafcommerce.core.payment.domain.OrderPayment;
import org.broadleafcommerce.core.pricing.service.exception.PricingException;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Calendar;
import java.util.List;

import jakarta.annotation.Resource;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OrderTest extends OrderBaseTest {

    private Long orderId = null;
    private int numOrderItems = 0;

    @Resource(name = "blOrderItemService")
    private OrderItemService orderItemService;
    
    @Resource
    private SkuDao skuDao;
    
    @org.junit.jupiter.api.Order(1)

    
    @Test
    @Transactional
    @Rollback(false)
    public void createCartForCustomer() {
        String userName = "customer1";
        Customer customer = customerService.readCustomerByUsername(userName);

        Order order = orderService.createNewCartForCustomer(customer);
        assert order != null;
        assert order.getId() != null;
        this.orderId = order.getId();
    }

    @org.junit.jupiter.api.Order(2)


    @Test
    @Transactional
    @Rollback(false)
    public void findCurrentCartForCustomer() {
        String userName = "customer1";
        Customer customer = customerService.readCustomerByUsername(userName);

        Order order = orderService.findCartForCustomer(customer);
        assert order != null;
        assert order.getId() != null;
        this.orderId = order.getId();
    }

    @org.junit.jupiter.api.Order(3)


    @Test
    @Rollback(false)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addItemToOrder() throws AddToCartException {
        numOrderItems++;
        // In the database, some Skus are inactive and some are active. This ensures that we pull back an active one
        // to test a successful cart add
        Sku sku = getFirstActiveSku();
        Order order = orderService.findOrderById(orderId);
        assert order != null;
        assert sku.getId() != null;
        
        OrderItemRequestDTO itemRequest = new OrderItemRequestDTO();
        itemRequest.setQuantity(1);
        itemRequest.setSkuId(sku.getId());

        order = orderService.createNewCartForCustomer(customerService.readCustomerByUsername("customer1"));
        orderId = order.getId();

        order = orderService.addItem(orderId, itemRequest, true);
        
        DiscreteOrderItem item = (DiscreteOrderItem) orderService.findLastMatchingItem(order, sku.getId(), null);
        
        assert item != null;
        assert item.getQuantity() == numOrderItems;
        assert item.getSku() != null;
        assert item.getSku().equals(sku);
        
        assert order.getFulfillmentGroups().size() == 1;
        
        FulfillmentGroup fg = order.getFulfillmentGroups().get(0);
        assert fg.getFulfillmentGroupItems().size() == 1;
        
        FulfillmentGroupItem fgItem = fg.getFulfillmentGroupItems().get(0);
        assert fgItem.getOrderItem().equals(item);
        assert fgItem.getQuantity() == item.getQuantity();
    }
    
    @org.junit.jupiter.api.Order(4)

    
    @Test
    @Rollback(false)
    @Transactional
    public void addAnotherItemToOrder() throws AddToCartException, PricingException, RemoveFromCartException {
     // In the database, some Skus are inactive and some are active. This ensures that we pull back an active one
        // to test a successful cart add
        Sku sku = getFirstActiveSku();
        Order order = orderService.findOrderById(orderId);
        assert order != null;
        assert sku.getId() != null;
        orderService.setAutomaticallyMergeLikeItems(true); 
        
        OrderItemRequestDTO itemRequest = new OrderItemRequestDTO();
        itemRequest.setQuantity(1);
        itemRequest.setSkuId(sku.getId());
        // Note that we are not incrementing the numOrderItems count because it should have gotten merged
        order = orderService.addItem(orderId, itemRequest, true);
        DiscreteOrderItem item = (DiscreteOrderItem) orderService.findLastMatchingItem(order, sku.getId(), null);
        
        assert item.getSku() != null;
        assert item.getSku().equals(sku);
        assert item.getQuantity() == 2;  // item-was merged with prior item.

        order = orderService.findOrderById(orderId);

        assert(order.getOrderItems().size()==1);
        assert(order.getOrderItems().get(0).getQuantity()==2);
        
        assert order.getFulfillmentGroups().size() == 1;
        
        FulfillmentGroup fg = order.getFulfillmentGroups().get(0);
        assert fg.getFulfillmentGroupItems().size() == 1;
        
        FulfillmentGroupItem fgItem = fg.getFulfillmentGroupItems().get(0);
        assert fgItem.getOrderItem().equals(item);
        assert fgItem.getQuantity() == item.getQuantity();

        //add the same item multiple times to the cart without merging or pricing
        boolean currentVal = orderService.getAutomaticallyMergeLikeItems();
        orderService.setAutomaticallyMergeLikeItems(false);
        itemRequest = new OrderItemRequestDTO();
        itemRequest.setQuantity(1);
        itemRequest.setSkuId(sku.getId());
        order = orderService.addItem(orderId, itemRequest, false);
        order = orderService.addItem(orderId, itemRequest, false);
        DiscreteOrderItem item2 = (DiscreteOrderItem) orderService.findLastMatchingItem(order, sku.getId(), null);
        assert item2.getSku() != null;
        assert item2.getSku().equals(sku);
        assert item2.getQuantity() == 1;  // item-was not auto-merged with prior items.
        order = orderService.findOrderById(orderId);
        assert(order.getOrderItems().size()==3);
        //reset the cart state back to what it was prior
        order = orderService.removeItem(order.getId(), order.getOrderItems().get(1).getId(), true);
        order = orderService.removeItem(order.getId(), order.getOrderItems().get(1).getId(), true);
        orderService.setAutomaticallyMergeLikeItems(currentVal);
        assert(order.getOrderItems().size()==1);
        assert(order.getOrderItems().get(0).getQuantity()==2);
    }
    
    /**
     * From the list of all Skus in the database, gets a Sku that is active
     * @return
     */
    public Sku getFirstActiveSku() {
        List<Sku> skus = skuDao.readAllSkus();
        return CollectionUtils.find(skus, new Predicate<Sku>() {

            @Override
            public boolean evaluate(Sku sku) {
                return sku.isActive();
            }
        });
    }
    
    @org.junit.jupiter.api.Order(5)

    
    @Test
    @Transactional
    public void testIllegalAddScenarios() throws AddToCartException {
        Order order = orderService.findOrderById(orderId);
        assert order != null;
        
        Product activeProduct = addTestProduct("mug", "cups", true);
        Product inactiveProduct = addTestProduct("cup", "cups", false);
        
        // Inactive skus should not be added
        OrderItemRequestDTO itemRequest = new OrderItemRequestDTO().setQuantity(1).setSkuId(inactiveProduct.getDefaultSku().getId());
        boolean addSuccessful = true;
        try {
            order = orderService.addItem(orderId, itemRequest, true);
        } catch (AddToCartException e) {
            addSuccessful = false;
        }
        assert !addSuccessful;
        
        // Products that have SKUs marked as inactive should not be added either
        itemRequest = new OrderItemRequestDTO().setQuantity(1).setProductId(inactiveProduct.getId());
        addSuccessful = true;
        try {
            order = orderService.addItem(orderId, itemRequest, true);
        } catch (AddToCartException e) {
            addSuccessful = false;
        }
        assert !addSuccessful;
        
        // Negative quantities are not allowed
        itemRequest = new OrderItemRequestDTO().setQuantity(-1).setSkuId(activeProduct.getDefaultSku().getId());
        addSuccessful = true;
        try {
            order = orderService.addItem(orderId, itemRequest, true);
        } catch (AddToCartException e) {
            addSuccessful = false;
            assert e.getCause() instanceof IllegalArgumentException;
        }
        assert !addSuccessful;
        
        // Order must exist
        itemRequest = new OrderItemRequestDTO().setQuantity(1).setSkuId(activeProduct.getDefaultSku().getId());
        addSuccessful = true;
        try {
            order = orderService.addItem(-1L, itemRequest, true);
        } catch (AddToCartException e) {
            addSuccessful = false;
            assert e.getCause() instanceof IllegalArgumentException;
        }
        assert !addSuccessful;
        
        // If a product is provided, it must exist
        itemRequest = new OrderItemRequestDTO().setQuantity(1).setProductId(-1L);
        addSuccessful = true;
        try {
            order = orderService.addItem(orderId, itemRequest, true);
        } catch (AddToCartException e) {
            addSuccessful = false;
            assert e.getCause() instanceof IllegalArgumentException;
        }
        assert !addSuccessful;
        
        // The SKU must exist
        itemRequest = new OrderItemRequestDTO().setQuantity(1).setSkuId(-1L);
        addSuccessful = true;
        try {
            order = orderService.addItem(orderId, itemRequest, true);
        } catch (AddToCartException e) {
            addSuccessful = false;
            assert e.getCause() instanceof IllegalArgumentException;
        }
        assert !addSuccessful;
    }
    
    @org.junit.jupiter.api.Order(6)

    
    @Test
    @Transactional
    public void testIllegalUpdateScenarios() throws UpdateCartException, AddToCartException, RemoveFromCartException {
        Order order = orderService.findOrderById(orderId);
        assert order != null;
        
        Product activeProduct = addTestProduct("mug", "cups", true);
        Product inactiveProduct = addTestProduct("cup", "cups", false);
        
        // Inactive skus should not be added
        OrderItemRequestDTO itemRequest = new OrderItemRequestDTO().setQuantity(1).setSkuId(activeProduct.getDefaultSku().getId());
        boolean addSuccessful = true;
        try {
            order = orderService.addItem(orderId, itemRequest, true);
        } catch (AddToCartException e) {
            addSuccessful = false;
        }
        assert addSuccessful;
        
        // should not be able to update to negative quantity
        OrderItem item = orderService.findLastMatchingItem(order, activeProduct.getDefaultSku().getId(), activeProduct.getId());
        itemRequest = new OrderItemRequestDTO().setQuantity(-3).setOrderItemId(item.getId());
        boolean updateSuccessful = true;
        try {
            orderService.updateItemQuantity(orderId, itemRequest, true);
        } catch (UpdateCartException e) {
            updateSuccessful = false;
        }
        assert !updateSuccessful;
        
      /* old bundles are not working in hibernate 6
        //shouldn't be able to update the quantity of a DOI inside of a bundle
        ProductBundle bundle = addProductBundle();
        itemRequest = new OrderItemRequestDTO().setQuantity(1).setProductId(bundle.getId()).setSkuId(bundle.getDefaultSku().getId());
        addSuccessful = true;
        try {
            order = orderService.addItem(orderId, itemRequest, true);
        } catch (AddToCartException e) {
            addSuccessful = false;
        }
        assert addSuccessful;
        
        BundleOrderItem bundleItem = (BundleOrderItem) orderService.findLastMatchingItem(order,
                                                                                         bundle.getDefaultSku().getId(),
                                                                                         bundle.getId());
        //should just be a single DOI inside the bundle
        DiscreteOrderItem doi = bundleItem.getDiscreteOrderItems().get(0);
        itemRequest = new OrderItemRequestDTO().setQuantity(4).setOrderItemId(doi.getId());
        try {
            orderService.updateItemQuantity(orderId, itemRequest, true);
        } catch (UpdateCartException e) {
            updateSuccessful = false;
        }
        assert !updateSuccessful;*/
    }

   /*
   old bundles are not working with hibernate 6
   @Test
    @Rollback(false)
    public void addBundleToOrder() throws AddToCartException {
        numOrderItems++;
        Sku sku = skuDao.readFirstSku();
        Order order = orderService.findOrderById(orderId);
        assert order != null;
        assert sku.getId() != null;
        
        ProductBundle bundleItem = addProductBundle();
        OrderItemRequestDTO orderItemRequestDTO = new OrderItemRequestDTO();
        orderItemRequestDTO.setProductId(bundleItem.getId());
        orderItemRequestDTO.setSkuId(bundleItem.getDefaultSku().getId());
        orderItemRequestDTO.setQuantity(1);
        
        order = orderService.addItem(order.getId(), orderItemRequestDTO, true);
        BundleOrderItem item = (BundleOrderItem) orderService.findLastMatchingItem(order, bundleItem.getDefaultSku().getId(), null);
        bundleOrderItemId = item.getId();
        assert item != null;
        assert item.getQuantity() == 1;
        assert item.getDiscreteOrderItems().size() == 1;
    }
    
    @Test
    @Rollback(false)
    @Transactional
    public void removeBundleFromOrder() throws RemoveFromCartException {
        Order order = orderService.findOrderById(orderId);
        List<OrderItem> orderItems = order.getOrderItems();
        assert orderItems.size() == numOrderItems;
        int startingSize = orderItems.size();
        BundleOrderItem bundleOrderItem = (BundleOrderItem) orderItemService.readOrderItemById(bundleOrderItemId);
        assert bundleOrderItem != null;
        assert bundleOrderItem.getDiscreteOrderItems() != null;
        assert bundleOrderItem.getDiscreteOrderItems().size() == 1;
        order = orderService.removeItem(order.getId(), bundleOrderItem.getId(), true);
        List<OrderItem> items = order.getOrderItems();
        assert items != null;
        assert items.size() == startingSize - 1;
    }*/
    
    @org.junit.jupiter.api.Order(7)

    
    @Test
    @Transactional
    public void getItemsForOrder() {
        Order order = orderService.findOrderById(orderId);
        List<OrderItem> orderItems = order.getOrderItems();
        assert orderItems != null;
        assert orderItems.size() == numOrderItems;
    }

    @org.junit.jupiter.api.Order(8)


    @Test
    @Transactional
    public void testManyToOneFGItemToOrderItem() throws UpdateCartException, RemoveFromCartException, PricingException {
        // Grab the order and the first OrderItem
        Order order = orderService.findOrderById(orderId);
        List<OrderItem> orderItems = order.getOrderItems();
        assert orderItems.size() > 0;
        OrderItem item = orderItems.get(0);
        
        // Set the quantity of the first OrderItem to 10
        OrderItemRequestDTO orderItemRequestDTO = new OrderItemRequestDTO();
        orderItemRequestDTO.setOrderItemId(item.getId());
        orderItemRequestDTO.setQuantity(10);
        order = orderService.updateItemQuantity(order.getId(), orderItemRequestDTO, true);
        
        // Assert that the quantity has changed
        OrderItem updatedItem = orderItemService.readOrderItemById(item.getId());
        assert updatedItem != null;
        assert updatedItem.getQuantity() == 10;
        
        // Assert that the appropriate fulfillment group item has changed
        assert order.getFulfillmentGroups().size() == 1;
        FulfillmentGroup fg = order.getFulfillmentGroups().get(0);
        assert fg.getFulfillmentGroupItems().size() == 1;
        FulfillmentGroupItem fgItem = null;
        for (FulfillmentGroupItem fgi : fg.getFulfillmentGroupItems()) {
            if (fgi.getOrderItem().equals(updatedItem)) {
                fgItem = fgi;
            }
        }
        
        assert fgItem != null;

    }

    @org.junit.jupiter.api.Order(9)


    @Test
    @Transactional
    public void updateItemsInOrder() throws UpdateCartException, RemoveFromCartException {
        // Grab the order and the first OrderItem
        Order order = orderService.findOrderById(orderId);
        List<OrderItem> orderItems = order.getOrderItems();
        assert orderItems.size() > 0;
        OrderItem item = orderItems.get(0);
        
        // Set the quantity of the first OrderItem to 10
        OrderItemRequestDTO orderItemRequestDTO = new OrderItemRequestDTO();
        orderItemRequestDTO.setOrderItemId(item.getId());
        orderItemRequestDTO.setQuantity(10);
        order = orderService.updateItemQuantity(order.getId(), orderItemRequestDTO, true);
        
        // Assert that the quantity has changed
        OrderItem updatedItem = orderItemService.readOrderItemById(item.getId());
        assert updatedItem != null;
        assert updatedItem.getQuantity() == 10;
        
        // Assert that the appropriate fulfillment group item has changed
        assert order.getFulfillmentGroups().size() == 1;
        FulfillmentGroup fg = order.getFulfillmentGroups().get(0);
        assert fg.getFulfillmentGroupItems().size() == 1;
        boolean fgItemUpdated = false;
        for (FulfillmentGroupItem fgi : fg.getFulfillmentGroupItems()) {
            if (fgi.getOrderItem().equals(updatedItem)) {
                assert fgi.getQuantity() == 10;
                fgItemUpdated = true;
            }
        }
        assert fgItemUpdated;
        
        // Set the quantity of the first OrderItem to 5
        orderItemRequestDTO = new OrderItemRequestDTO();
        orderItemRequestDTO.setOrderItemId(item.getId());
        orderItemRequestDTO.setQuantity(5);
        order = orderService.updateItemQuantity(order.getId(), orderItemRequestDTO, true);
        
        // Assert that the quantity has changed - going to a smaller quantity is also ok
        updatedItem = orderItemService.readOrderItemById(item.getId());
        assert updatedItem != null;
        assert updatedItem.getQuantity() == 5;
        
        // Assert that the appropriate fulfillment group item has changed
        assert order.getFulfillmentGroups().size() == 1;
        fg = order.getFulfillmentGroups().get(0);
        assert fg.getFulfillmentGroupItems().size() == 1;
        fgItemUpdated = false;
        for (FulfillmentGroupItem fgi : fg.getFulfillmentGroupItems()) {
            if (fgi.getOrderItem().equals(updatedItem)) {
                assert fgi.getQuantity() == 5;
                fgItemUpdated = true;
            }
        }
        assert fgItemUpdated;
        
        // Setting the quantity to 0 should in fact remove the item completely
        int startingSize = order.getOrderItems().size();
        orderItemRequestDTO = new OrderItemRequestDTO();
        orderItemRequestDTO.setOrderItemId(item.getId());
        orderItemRequestDTO.setQuantity(0);
        order = orderService.updateItemQuantity(order.getId(), orderItemRequestDTO, true);
        
        // Assert that the item has been removed
        updatedItem = orderItemService.readOrderItemById(item.getId());
        assert updatedItem == null;
        assert order.getOrderItems().size() == startingSize - 1;
        
        // Assert that the appropriate fulfillment group item has been removed
        assert order.getFulfillmentGroups().size() == 0;
        /*
        TODO Since we commented out some tests above, there is no longer an additional item
        in the cart, hence the fulfillment group is removed

        fg = order.getFulfillmentGroups().get(0);
        assert fg.getFulfillmentGroupItems().size() == startingSize - 1;
        boolean fgItemRemoved = true;
        for (FulfillmentGroupItem fgi : fg.getFulfillmentGroupItems()) {
            if (fgi.getOrderItem().equals(updatedItem)) {
                fgItemRemoved = false;
            }
        }
        assert fgItemRemoved;*/
    }

    @org.junit.jupiter.api.Order(10)


    @Test
    @Transactional
    public void removeItemFromOrder() throws RemoveFromCartException {
        // Grab the order and the first OrderItem
        Order order = orderService.findOrderById(orderId);
        List<OrderItem> orderItems = order.getOrderItems();
        assert orderItems.size() > 0;
        int startingSize = orderItems.size();
        OrderItem item = orderItems.get(0);
        Long itemId = item.getId();
        assert item != null;
        
        // Remove the item
        order = orderService.removeItem(order.getId(), item.getId(), true);
        List<OrderItem> items = order.getOrderItems();
        OrderItem updatedItem = orderItemService.readOrderItemById(item.getId());
        
        // Assert that the item has been removed
        assert items != null;
        assert items.size() == startingSize - 1;
        assert updatedItem == null;
    }

    @org.junit.jupiter.api.Order(11)


    @Test
    @Transactional
    public void checkOrderItems() throws PricingException {
        Order order = orderService.findOrderById(orderId);
        // The removal from the removeBundleFromOrder() has actually persisted.
        // However, the previous two transactions were rolled back and thus the items still exist.
        assert order.getOrderItems().size() == 1;
        
        // As mentioned, the bundleOrderItem however has gone away
/*
        old bundles not working
        BundleOrderItem bundleOrderItem = (BundleOrderItem) orderItemService.readOrderItemById(bundleOrderItemId);
        assert bundleOrderItem == null;
*/
    }

    
    @org.junit.jupiter.api.Order(12)


    
    @Test
    @Transactional
    public void getOrdersForCustomer() {
        String username = "customer1";
        Customer customer = customerService.readCustomerByUsername(username);
        List<Order> orders = orderService.findOrdersForCustomer(customer);
        assert orders != null;
        assert orders.size() > 0;
    }

    @org.junit.jupiter.api.Order(13)


    @Test
    public void findCartForAnonymousCustomer() {
        Customer customer = customerService.createCustomerFromId(null);
        Order order = orderService.findCartForCustomer(customer);
        assert order == null;
        order = orderService.createNewCartForCustomer(customer);
        Long orderId = order.getId();
        Order newOrder = orderService.findOrderById(orderId);
        assert newOrder != null;
        assert newOrder.getCustomer() != null;
    }

    @org.junit.jupiter.api.Order(14)


    @Test
    @Transactional
    public void findOrderByOrderNumber() throws PricingException {
        Customer customer = customerService.createCustomerFromId(null);
        Order order = orderService.createNewCartForCustomer(customer);
        order.setOrderNumber("3456");
        order = orderService.save(order, false);
        Long orderId = order.getId();

        Order newOrder = orderService.findOrderByOrderNumber("3456");
        assert newOrder.getId().equals(orderId);

        Order nullOrder = orderService.findOrderByOrderNumber(null);
        assert nullOrder == null;

        nullOrder = orderService.findOrderByOrderNumber("");
        assert nullOrder == null;
    }

    @org.junit.jupiter.api.Order(15)


    @Test
    @Transactional
    public void findNamedOrderForCustomer() throws PricingException {
        Customer customer = customerService.createCustomerFromId(null);
        Order order = orderService.createNewCartForCustomer(customer);
        order.setStatus(OrderStatus.NAMED);
        order.setName("COOL ORDER");
        order = orderService.save(order, false);
        Long orderId = order.getId();

        Order newOrder = orderService.findNamedOrderForCustomer("COOL ORDER", customer);
        assert newOrder.getId().equals(orderId);
    }

    @org.junit.jupiter.api.Order(16)


    @Test
    @Transactional
    public void testReadOrdersForCustomer() throws PricingException {
        Customer customer = customerService.createCustomerFromId(null);
        Order order = orderService.createNewCartForCustomer(customer);
        order.setStatus(OrderStatus.IN_PROCESS);
        order = orderService.save(order, false);

        List<Order> newOrders = orderService.findOrdersForCustomer(customer, OrderStatus.IN_PROCESS);
        boolean containsOrder = newOrders.contains(order);

        assert containsOrder == true;

        containsOrder = false;
        newOrders = orderService.findOrdersForCustomer(customer, null);

        if (newOrders.contains(order)) {
            containsOrder = true;
        }

        assert containsOrder == true;
    }

    @org.junit.jupiter.api.Order(17)


    @Test
    public void testOrderProperties() throws PricingException {
        Customer customer = customerService.createCustomerFromId(null);
        Order order = orderService.createNewCartForCustomer(customer);

        assert order.getSubTotal() == null;
        assert order.getTotal() == null;

        Calendar testCalendar = Calendar.getInstance();
        order.setSubmitDate(testCalendar.getTime());
        assert order.getSubmitDate().equals(testCalendar.getTime());
    }

    @org.junit.jupiter.api.Order(18)


    @Test
    public void testNamedOrderForCustomer() throws PricingException {
        Customer customer = customerService.createCustomerFromId(null);
        customer = customerService.saveCustomer(customer);
        Order order = orderService.createNamedOrderForCustomer("Birthday Order", customer);
        Long orderId = order.getId();
        assert order != null;
        assert order.getName().equals("Birthday Order");
        assert order.getCustomer().equals(customer);
        
        orderService.cancelOrder(order);

        assert orderService.findOrderById(orderId) == null;
    }

    @org.junit.jupiter.api.Order(19)


    @ParameterizedTest
    @MethodSource("org.broadleafcommerce.core.payment.PaymentInfoDataProvider#provideBasicSalesPaymentInfo")
    @Rollback(false)
    @Transactional
    public void addPaymentToOrder(OrderPayment paymentInfo) {
        Order order = orderService.findOrderById(orderId);
        paymentInfo = orderService.addPaymentToOrder(order, paymentInfo, null);

        order = orderService.findOrderById(orderId);
        OrderPayment payment = order.getPayments()
                .get(order.getPayments().indexOf(paymentInfo));

        assert payment != null;
        assert payment.getOrder() != null;
        assert payment.getOrder().equals(order);
    }

    @org.junit.jupiter.api.Order(20)


    @ParameterizedTest
    @MethodSource("org.broadleafcommerce.core.payment.PaymentInfoDataProvider#provideBasicSalesPaymentInfo")
    @Transactional
    public void testOrderPaymentInfos(OrderPayment info) throws PricingException {
        Customer customer = customerService.saveCustomer(createNamedCustomer());
        Order order = orderService.createNewCartForCustomer(customer);
        info = orderService.addPaymentToOrder(order, info, null);

        boolean foundInfo = false;
        assert order.getPayments() != null;
        for (OrderPayment testInfo : order.getPayments()) {
            if (testInfo.equals(info)) {
                foundInfo = true;
                break;
            }
        }
        assert foundInfo == true;
        assert orderService.findPaymentsForOrder(order) != null;
    }

    @org.junit.jupiter.api.Order(21)


    @Test
    public void findCartForNullCustomerId() {
        assert orderService.findCartForCustomer(new CustomerImpl()) == null;
    }

    @org.junit.jupiter.api.Order(22)


    @Test
    public void testSubmitOrder() throws PricingException {
        Customer customer = customerService.createCustomerFromId(null);
        Order order = orderService.createNewCartForCustomer(customer);
        order.setStatus(OrderStatus.IN_PROCESS);
        order = orderService.save(order, false);
        Long orderId = order.getId();

        Order confirmedOrder = orderService.confirmOrder(order);

        confirmedOrder = orderService.findOrderById(confirmedOrder.getId());
        Long confirmedOrderId = confirmedOrder.getId();

        assert orderId.equals(confirmedOrderId);
        assert confirmedOrder.getStatus().equals(OrderStatus.SUBMITTED);
    }

}
