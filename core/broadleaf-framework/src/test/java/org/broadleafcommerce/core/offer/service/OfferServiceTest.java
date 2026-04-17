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
package org.broadleafcommerce.core.offer.service;

import org.broadleafcommerce.common.money.Money;
import org.broadleafcommerce.core.offer.dao.CustomerOfferDao;
import org.broadleafcommerce.core.offer.dao.OfferCodeDao;
import org.broadleafcommerce.core.offer.dao.OfferDao;
import org.broadleafcommerce.core.offer.domain.CandidateItemOffer;
import org.broadleafcommerce.core.offer.domain.CandidateItemOfferImpl;
import org.broadleafcommerce.core.offer.domain.CandidateOrderOffer;
import org.broadleafcommerce.core.offer.domain.CandidateOrderOfferImpl;
import org.broadleafcommerce.core.offer.domain.CustomerOffer;
import org.broadleafcommerce.core.offer.domain.Offer;
import org.broadleafcommerce.core.offer.domain.OfferCode;
import org.broadleafcommerce.core.offer.domain.OfferImpl;
import org.broadleafcommerce.core.offer.domain.OfferOfferRuleXref;
import org.broadleafcommerce.core.offer.domain.OfferOfferRuleXrefImpl;
import org.broadleafcommerce.core.offer.domain.OfferRule;
import org.broadleafcommerce.core.offer.domain.OfferRuleImpl;
import org.broadleafcommerce.core.offer.domain.OrderAdjustment;
import org.broadleafcommerce.core.offer.domain.OrderAdjustmentImpl;
import org.broadleafcommerce.core.offer.domain.OrderItemAdjustment;
import org.broadleafcommerce.core.offer.domain.OrderItemAdjustmentImpl;
import org.broadleafcommerce.core.offer.service.discount.domain.PromotableItemFactoryImpl;
import org.broadleafcommerce.core.offer.service.discount.domain.PromotableOfferUtility;
import org.broadleafcommerce.core.offer.service.discount.domain.PromotableOfferUtilityImpl;
import org.broadleafcommerce.core.offer.service.processor.FulfillmentGroupOfferProcessor;
import org.broadleafcommerce.core.offer.service.processor.FulfillmentGroupOfferProcessorImpl;
import org.broadleafcommerce.core.offer.service.processor.ItemOfferProcessorImpl;
import org.broadleafcommerce.core.offer.service.processor.OfferTimeZoneProcessor;
import org.broadleafcommerce.core.offer.service.processor.OrderOfferProcessorImpl;
import org.broadleafcommerce.core.offer.service.type.OfferDiscountType;
import org.broadleafcommerce.core.offer.service.type.OfferRuleType;
import org.broadleafcommerce.core.order.dao.FulfillmentGroupItemDao;
import org.broadleafcommerce.core.order.dao.OrderItemDao;
import org.broadleafcommerce.core.order.domain.FulfillmentGroupItem;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.domain.OrderItem;
import org.broadleafcommerce.core.order.domain.OrderItemPriceDetail;
import org.broadleafcommerce.core.order.domain.OrderItemPriceDetailImpl;
import org.broadleafcommerce.core.order.domain.OrderItemQualifier;
import org.broadleafcommerce.core.order.domain.OrderItemQualifierImpl;
import org.broadleafcommerce.core.order.domain.OrderMultishipOption;
import org.broadleafcommerce.core.order.service.FulfillmentGroupService;
import org.broadleafcommerce.core.order.service.OrderItemService;
import org.broadleafcommerce.core.order.service.OrderMultishipOptionService;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.order.service.call.FulfillmentGroupItemRequest;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import junit.framework.TestCase;

/**
 * 
 * @author jfischer
 *
 */
public class OfferServiceTest extends TestCase { 
    
    protected OfferServiceImpl offerService;
    protected CustomerOfferDao customerOfferDaoMock;
    protected OfferCodeDao offerCodeDaoMock;
    protected OfferDao offerDaoMock;
    protected OrderItemDao orderItemDaoMock;
    protected OrderService orderServiceMock;
    protected OrderItemService orderItemServiceMock;
    protected FulfillmentGroupItemDao fgItemDaoMock;
    protected OfferDataItemProvider dataProvider = new OfferDataItemProvider();
    protected OfferTimeZoneProcessor offerTimeZoneProcessorMock;
    protected PromotableOfferUtility promotableOfferUtility;

    private FulfillmentGroupService fgServiceMock;
    private OrderMultishipOptionService multishipOptionServiceMock;

    @Override
    protected void setUp() throws Exception {
        offerService = new OfferServiceImpl() { 
            @Override
            protected List<OfferCode> refreshOfferCodesIfApplicable(Order order) {
                return order.getAddedOfferCodes();
            }
        };
        customerOfferDaoMock = Mockito.mock(CustomerOfferDao.class);
        orderServiceMock = Mockito.mock(OrderService.class);
        offerCodeDaoMock = Mockito.mock(OfferCodeDao.class);
        offerDaoMock = Mockito.mock(OfferDao.class);
        orderItemDaoMock = Mockito.mock(OrderItemDao.class);
        offerService.setCustomerOfferDao(customerOfferDaoMock);
        offerService.setOfferCodeDao(offerCodeDaoMock);
        offerService.setOfferDao(offerDaoMock);
        offerService.setOrderService(orderServiceMock);
        orderItemServiceMock = Mockito.mock(OrderItemService.class);
        fgItemDaoMock = Mockito.mock(FulfillmentGroupItemDao.class);
        fgServiceMock = Mockito.mock(FulfillmentGroupService.class);
        multishipOptionServiceMock = Mockito.mock(OrderMultishipOptionService.class);
        offerTimeZoneProcessorMock = Mockito.mock(OfferTimeZoneProcessor.class);
        promotableOfferUtility = new PromotableOfferUtilityImpl();

        OfferServiceUtilitiesImpl offerServiceUtilities = new OfferServiceUtilitiesImpl(promotableOfferUtility);
        offerServiceUtilities.setOfferDao(offerDaoMock);
        offerServiceUtilities.setPromotableItemFactory(new PromotableItemFactoryImpl(promotableOfferUtility));

        OrderOfferProcessorImpl orderProcessor = new OrderOfferProcessorImpl(promotableOfferUtility);
        orderProcessor.setOfferDao(offerDaoMock);
        orderProcessor.setOrderItemDao(orderItemDaoMock);
        orderProcessor.setPromotableItemFactory(new PromotableItemFactoryImpl(promotableOfferUtility));
        orderProcessor.setOfferTimeZoneProcessor(offerTimeZoneProcessorMock);
        orderProcessor.setOfferServiceUtilities(offerServiceUtilities);
        offerService.setOrderOfferProcessor(orderProcessor);


        ItemOfferProcessorImpl itemProcessor = new ItemOfferProcessorImpl(promotableOfferUtility);
        itemProcessor.setOfferDao(offerDaoMock);
        itemProcessor.setPromotableItemFactory(new PromotableItemFactoryImpl(promotableOfferUtility));
        itemProcessor.setOfferServiceUtilities(offerServiceUtilities);
        offerService.setItemOfferProcessor(itemProcessor);

        FulfillmentGroupOfferProcessor fgProcessor = new FulfillmentGroupOfferProcessorImpl(promotableOfferUtility);
        fgProcessor.setOfferDao(offerDaoMock);
        fgProcessor.setPromotableItemFactory(new PromotableItemFactoryImpl(promotableOfferUtility));
        offerService.setFulfillmentGroupOfferProcessor(fgProcessor);
        offerService.setPromotableItemFactory(new PromotableItemFactoryImpl(promotableOfferUtility));
    }

    public void replay() {
        // Mockito does not require replay - stubs are active immediately
    }

    public void verify() {
        // Mockito does not require explicit verify for stubbed methods
    }

    public void testApplyOffersToOrder_Order() throws Exception {
        final ThreadLocal<Order> myOrder = new ThreadLocal<>();
        Mockito.when(offerDaoMock.createOrderItemPriceDetailAdjustment()).thenAnswer(OfferDataItemProvider.getCreateOrderItemPriceDetailAdjustmentAnswer());

        CandidateOrderOfferAnswer candidateOrderOfferAnswer = new CandidateOrderOfferAnswer();
        OrderAdjustmentAnswer orderAdjustmentAnswer = new OrderAdjustmentAnswer();
        Mockito.when(offerDaoMock.createOrderAdjustment()).thenAnswer(orderAdjustmentAnswer);

        OrderItemPriceDetailAnswer orderItemPriceDetailAnswer = new OrderItemPriceDetailAnswer();
        Mockito.when(orderItemDaoMock.createOrderItemPriceDetail()).thenAnswer(orderItemPriceDetailAnswer);

        OrderItemQualifierAnswer orderItemQualifierAnswer = new OrderItemQualifierAnswer();
        Mockito.when(orderItemDaoMock.createOrderItemQualifier()).thenAnswer(orderItemQualifierAnswer);

        CandidateItemOfferAnswer candidateItemOfferAnswer = new CandidateItemOfferAnswer();
        OrderItemAdjustmentAnswer orderItemAdjustmentAnswer = new OrderItemAdjustmentAnswer();

        Mockito.when(fgServiceMock.addItemToFulfillmentGroup(Mockito.isA(FulfillmentGroupItemRequest.class), Mockito.eq(false))).thenAnswer(OfferDataItemProvider.getAddItemToFulfillmentGroupAnswer());
        Mockito.when(orderServiceMock.removeItem(Mockito.isA(Long.class), Mockito.isA(Long.class), Mockito.eq(false))).thenAnswer(OfferDataItemProvider.getRemoveItemFromOrderAnswer());
        Mockito.when(orderServiceMock.save(Mockito.isA(Order.class), Mockito.isA(Boolean.class))).thenAnswer(OfferDataItemProvider.getSaveOrderAnswer());
        Mockito.when(orderServiceMock.findOrderById(Mockito.isA(Long.class))).thenAnswer(new Answer<Order>() {
            @Override
            public Order answer(InvocationOnMock invocation) throws Throwable {
                return myOrder.get();
            }
        });

        Mockito.when(orderServiceMock.getAutomaticallyMergeLikeItems()).thenReturn(true);
        Mockito.when(orderItemServiceMock.saveOrderItem(Mockito.isA(OrderItem.class))).thenAnswer(OfferDataItemProvider.getSaveOrderItemAnswer());
        Mockito.when(fgItemDaoMock.save(Mockito.isA(FulfillmentGroupItem.class))).thenAnswer(OfferDataItemProvider.getSaveFulfillmentGroupItemAnswer());

        Mockito.when(multishipOptionServiceMock.findOrderMultishipOptions(Mockito.isA(Long.class))).thenAnswer(new Answer<List<OrderMultishipOption>>() {
            @Override
            public List<OrderMultishipOption> answer(InvocationOnMock invocation) throws Throwable {
                return new ArrayList<>();
            }
        });

        Mockito.doNothing().when(multishipOptionServiceMock).deleteAllOrderMultishipOptions(Mockito.isA(Order.class));
        Mockito.when(fgServiceMock.collapseToOneShippableFulfillmentGroup(Mockito.isA(Order.class), Mockito.eq(false))).thenAnswer(OfferDataItemProvider.getSameOrderAnswer());
        Mockito.when(fgItemDaoMock.create()).thenAnswer(OfferDataItemProvider.getCreateFulfillmentGroupItemAnswer());
        Mockito.doNothing().when(fgItemDaoMock).delete(Mockito.isA(FulfillmentGroupItem.class));

        Mockito.when(offerTimeZoneProcessorMock.getTimeZone(Mockito.isA(OfferImpl.class))).thenReturn(TimeZone.getTimeZone("CST"));

        replay();

        Order order = dataProvider.createBasicOrder();
        myOrder.set(order);
        List<Offer> offers = dataProvider.createOrderBasedOffer("order.subTotal.getAmount()>126", OfferDiscountType.PERCENT_OFF);

        offerService.applyAndSaveOffersToOrder(offers, order);

        int adjustmentCount = order.getOrderAdjustments().size();

        assertTrue(adjustmentCount == 1);
        assertTrue(order.getSubTotal().subtract(order.getOrderAdjustmentsValue()).equals(new Money(116.95D)));

        order = dataProvider.createBasicOrder();
        myOrder.set(order);
        offers = dataProvider.createOrderBasedOffer("order.subTotal.getAmount()>126", OfferDiscountType.PERCENT_OFF);
        List<Offer> offers2 = dataProvider.createItemBasedOfferWithItemCriteria(
            "order.subTotal.getAmount()>20",
            OfferDiscountType.PERCENT_OFF,
            "([MVEL.eval(\"toUpperCase()\",\"test1\"), MVEL.eval(\"toUpperCase()\",\"test2\")] contains MVEL.eval(\"toUpperCase()\", discreteOrderItem.category.name))",
            "([MVEL.eval(\"toUpperCase()\",\"test1\"), MVEL.eval(\"toUpperCase()\",\"test2\")] contains MVEL.eval(\"toUpperCase()\", discreteOrderItem.category.name))"
        );
        offers.addAll(offers2);

        offerService.applyAndSaveOffersToOrder(offers, order);

        //with the item offers in play, the subtotal restriction for the order offer is no longer valid
        adjustmentCount = countItemAdjustments(order);
        int qualifierCount = countItemQualifiers(order);

        assertTrue(adjustmentCount == 2);
        assertTrue(qualifierCount == 2);
        adjustmentCount = order.getOrderAdjustments().size();
        assertTrue(adjustmentCount == 0);
        //assertTrue(order.getSubTotal().equals(new Money(124.95D)));

        order = dataProvider.createBasicOrder();
        myOrder.set(order);
        OfferRule orderRule = new OfferRuleImpl();
        //orderRule.setMatchRule("order.subTotal.getAmount()>124");
        orderRule.setMatchRule("order.subTotal.getAmount()>100");
        Offer offer = offers.get(0);
        OfferOfferRuleXref ruleXref = new OfferOfferRuleXrefImpl(offer, orderRule, OfferRuleType.ORDER.getType());
        offer.getOfferMatchRulesXref().put(OfferRuleType.ORDER.getType(), ruleXref);

        offerService.applyAndSaveOffersToOrder(offers, order);

        //now that the order restriction has been lessened, even with the item level discounts applied, 
        // the order offer still qualifies
        adjustmentCount = countItemAdjustments(order);
        qualifierCount = countItemQualifiers(order);

        assertTrue(adjustmentCount == 2);
        assertTrue(qualifierCount == 2);
        adjustmentCount = order.getOrderAdjustments().size();
        assertTrue(adjustmentCount == 1);
        assertTrue(order.getSubTotal().subtract(order.getOrderAdjustmentsValue()).equals(new Money(112.45D)));
        assertTrue(order.getSubTotal().equals(new Money(124.95D)));

        order = dataProvider.createBasicPromotableOrder(promotableOfferUtility).getOrder();
        myOrder.set(order);
        //offers.get(0).setCombinableWithOtherOffers(false);
        List<Offer> offers3 = dataProvider.createOrderBasedOffer("order.subTotal.getAmount()>20", OfferDiscountType.AMOUNT_OFF);
        offers.addAll(offers3);

        offerService.applyAndSaveOffersToOrder(offers, order);

        adjustmentCount = order.getOrderAdjustments().size();
        assertTrue(adjustmentCount == 2);

        order = dataProvider.createBasicPromotableOrder(promotableOfferUtility).getOrder();
        myOrder.set(order);
        offers.get(0).setCombinableWithOtherOffers(false);

        offerService.applyAndSaveOffersToOrder(offers, order);

        //there is a non combinable order offer now
        adjustmentCount = countItemAdjustments(order);
        qualifierCount = countItemQualifiers(order);

        assertTrue(adjustmentCount == 2);
        assertTrue(qualifierCount == 2);
        adjustmentCount = order.getOrderAdjustments().size();
        assertTrue(adjustmentCount == 1);
        assertTrue(order.getSubTotal().subtract(order.getOrderAdjustmentsValue()).equals(new Money(112.45D)));
        assertTrue(order.getSubTotal().equals(new Money(124.95D)));

        order = dataProvider.createBasicPromotableOrder(promotableOfferUtility).getOrder();
        myOrder.set(order);
        offers.get(0).setTotalitarianOffer(true);

        offerService.applyAndSaveOffersToOrder(offers, order);

        //there is a totalitarian order offer now - it is better than the item offers - the item offers are removed
        adjustmentCount = countItemAdjustments(order);
        qualifierCount = countItemQualifiers(order);

        assertTrue(adjustmentCount == 0);
        assertTrue(qualifierCount == 0);
        adjustmentCount = order.getOrderAdjustments().size();
        assertTrue(adjustmentCount == 1);
        assertTrue(order.getSubTotal().subtract(order.getOrderAdjustmentsValue()).equals(new Money(116.95D)));
        assertTrue(order.getSubTotal().equals(new Money(129.95D)));

        order = dataProvider.createBasicPromotableOrder(promotableOfferUtility).getOrder();
        myOrder.set(order);
        offers.get(0).setValue(new BigDecimal(".05"));
        offers.get(2).setValue(new BigDecimal(".01"));
        offers.get(2).setDiscountType(OfferDiscountType.PERCENT_OFF);

        offerService.applyAndSaveOffersToOrder(offers, order);

        //even though the first order offer is totalitarian, it is worth less than the order item offer, so it is removed.
        //the other order offer is still valid, however, and is included.
        adjustmentCount = countItemAdjustments(order);

        assertTrue(adjustmentCount == 2);
        adjustmentCount = order.getOrderAdjustments().size();
        assertTrue(adjustmentCount == 1);
        assertTrue(order.getSubTotal().subtract(order.getOrderAdjustmentsValue()).equals(new Money(124.94D)));
        assertTrue(order.getSubTotal().equals(new Money(124.95D)));

        verify();
    }

    private int countItemAdjustments(Order order) {
        int adjustmentCount = 0;
        for (OrderItem item : order.getOrderItems()) {
            for (OrderItemPriceDetail detail : item.getOrderItemPriceDetails()) {
                if (detail.getOrderItemPriceDetailAdjustments() != null) {
                    adjustmentCount += detail.getOrderItemPriceDetailAdjustments().size();
                }

            }
        }
        return adjustmentCount;
    }

    private int countItemQualifiers(Order order) {
        int qualifierCount = 0;
        for (OrderItem item : order.getOrderItems()) {
            for (OrderItemQualifier qualifier : item.getOrderItemQualifiers()) {
                qualifierCount = qualifierCount += qualifier.getQuantity();
            }
        }
        return qualifierCount;
    }

    public void testApplyOffersToOrder_Items() throws Exception {
        final ThreadLocal<Order> myOrder = new ThreadLocal<>();
        Mockito.when(offerDaoMock.createOrderItemPriceDetailAdjustment()).thenAnswer(OfferDataItemProvider.getCreateOrderItemPriceDetailAdjustmentAnswer());

        CandidateItemOfferAnswer answer = new CandidateItemOfferAnswer();
        OrderItemAdjustmentAnswer answer2 = new OrderItemAdjustmentAnswer();

        OrderItemPriceDetailAnswer orderItemPriceDetailAnswer = new OrderItemPriceDetailAnswer();
        Mockito.when(orderItemDaoMock.createOrderItemPriceDetail()).thenAnswer(orderItemPriceDetailAnswer);

        OrderItemQualifierAnswer orderItemQualifierAnswer = new OrderItemQualifierAnswer();
        Mockito.when(orderItemDaoMock.createOrderItemQualifier()).thenAnswer(orderItemQualifierAnswer);

        Mockito.when(orderServiceMock.getAutomaticallyMergeLikeItems()).thenReturn(true);
        Mockito.when(orderServiceMock.save(Mockito.isA(Order.class), Mockito.isA(Boolean.class))).thenAnswer(OfferDataItemProvider.getSaveOrderAnswer());
        Mockito.when(orderItemServiceMock.saveOrderItem(Mockito.isA(OrderItem.class))).thenAnswer(OfferDataItemProvider.getSaveOrderItemAnswer());
        Mockito.when(fgItemDaoMock.save(Mockito.isA(FulfillmentGroupItem.class))).thenAnswer(OfferDataItemProvider.getSaveFulfillmentGroupItemAnswer());

        Mockito.when(fgServiceMock.addItemToFulfillmentGroup(Mockito.isA(FulfillmentGroupItemRequest.class), Mockito.eq(false))).thenAnswer(OfferDataItemProvider.getAddItemToFulfillmentGroupAnswer());
        Mockito.when(orderServiceMock.removeItem(Mockito.isA(Long.class), Mockito.isA(Long.class), Mockito.eq(false))).thenAnswer(OfferDataItemProvider.getRemoveItemFromOrderAnswer());

        Mockito.when(multishipOptionServiceMock.findOrderMultishipOptions(Mockito.isA(Long.class))).thenAnswer(new Answer<List<OrderMultishipOption>>() {
            @Override
            public List<OrderMultishipOption> answer(InvocationOnMock invocation) throws Throwable {
                return new ArrayList<>();
            }
        });

        Mockito.when(orderServiceMock.findOrderById(Mockito.isA(Long.class))).thenAnswer(new Answer<Order>() {
            @Override
            public Order answer(InvocationOnMock invocation) throws Throwable {
                return myOrder.get();
            }
        });

        Mockito.doNothing().when(multishipOptionServiceMock).deleteAllOrderMultishipOptions(Mockito.isA(Order.class));
        Mockito.when(fgServiceMock.collapseToOneShippableFulfillmentGroup(Mockito.isA(Order.class), Mockito.eq(false))).thenAnswer(OfferDataItemProvider.getSameOrderAnswer());
        Mockito.when(fgItemDaoMock.create()).thenAnswer(OfferDataItemProvider.getCreateFulfillmentGroupItemAnswer());
        Mockito.doNothing().when(fgItemDaoMock).delete(Mockito.isA(FulfillmentGroupItem.class));
        Mockito.when(offerTimeZoneProcessorMock.getTimeZone(Mockito.isA(OfferImpl.class))).thenReturn(TimeZone.getTimeZone("CST"));

        replay();

        Order order = dataProvider.createBasicPromotableOrder(promotableOfferUtility).getOrder();
        myOrder.set(order);
        List<Offer> offers = dataProvider.createItemBasedOfferWithItemCriteria(
            "order.subTotal.getAmount()>20",
            OfferDiscountType.PERCENT_OFF,
            "([MVEL.eval(\"toUpperCase()\",\"test1\"), MVEL.eval(\"toUpperCase()\",\"test2\")] contains MVEL.eval(\"toUpperCase()\", discreteOrderItem.category.name))",
            "([MVEL.eval(\"toUpperCase()\",\"test1\"), MVEL.eval(\"toUpperCase()\",\"test2\")] contains MVEL.eval(\"toUpperCase()\", discreteOrderItem.category.name))"
        );

        offerService.applyAndSaveOffersToOrder(offers, order);

        int adjustmentCount = countItemAdjustments(order);

        assertTrue(adjustmentCount == 2);

        order = dataProvider.createBasicPromotableOrder(promotableOfferUtility).getOrder();
        myOrder.set(order);

        offers = dataProvider.createItemBasedOfferWithItemCriteria(
            "order.subTotal.getAmount()>20",
            OfferDiscountType.PERCENT_OFF,
            "([MVEL.eval(\"toUpperCase()\",\"test1\"), MVEL.eval(\"toUpperCase()\",\"test2\")] contains MVEL.eval(\"toUpperCase()\", discreteOrderItem.category.name))",
            "([MVEL.eval(\"toUpperCase()\",\"test5\"), MVEL.eval(\"toUpperCase()\",\"test6\")] contains MVEL.eval(\"toUpperCase()\", discreteOrderItem.category.name))"
        );

        offerService.applyAndSaveOffersToOrder(offers, order);

        adjustmentCount = countItemAdjustments(order);

        //Qualifiers are there, but the targets are not, so no adjustments
        assertTrue(adjustmentCount == 0);

        verify();
    }

    public void testBuildOfferListForOrder() throws Exception {
        Mockito.when(customerOfferDaoMock.readCustomerOffersByCustomer(Mockito.isA(Customer.class))).thenReturn(new ArrayList<CustomerOffer>());
        Mockito.when(offerDaoMock.readOffersByAutomaticDeliveryType()).thenReturn(dataProvider.createCustomerBasedOffer(null, dataProvider.yesterday(), dataProvider.tomorrow(), OfferDiscountType.PERCENT_OFF));

        replay();

        Order order = dataProvider.createBasicPromotableOrder(promotableOfferUtility).getOrder();
        List<Offer> offers = offerService.buildOfferListForOrder(order);

        assertTrue(offers.size() == 1);

        verify();
    }

    public class CandidateItemOfferAnswer implements Answer<CandidateItemOffer> {

        @Override
        public CandidateItemOffer answer(InvocationOnMock invocation) throws Throwable {
            return new CandidateItemOfferImpl();
        }

    }

    public class OrderItemAdjustmentAnswer implements Answer<OrderItemAdjustment> {

        @Override
        public OrderItemAdjustment answer(InvocationOnMock invocation) throws Throwable {
            return new OrderItemAdjustmentImpl();
        }

    }

    public class CandidateOrderOfferAnswer implements Answer<CandidateOrderOffer> {

        @Override
        public CandidateOrderOffer answer(InvocationOnMock invocation) throws Throwable {
            return new CandidateOrderOfferImpl();
        }

    }

    public class OrderAdjustmentAnswer implements Answer<OrderAdjustment> {

        @Override
        public OrderAdjustment answer(InvocationOnMock invocation) throws Throwable {
            return new OrderAdjustmentImpl();
        }

    }

    public class OrderItemPriceDetailAnswer implements Answer<OrderItemPriceDetail> {

        @Override
        public OrderItemPriceDetail answer(InvocationOnMock invocation) throws Throwable {
            return new OrderItemPriceDetailImpl();
        }
    }

    public class OrderItemQualifierAnswer implements Answer<OrderItemQualifier> {

        @Override
        public OrderItemQualifier answer(InvocationOnMock invocation) throws Throwable {
            return new OrderItemQualifierImpl();
        }
    }
}
