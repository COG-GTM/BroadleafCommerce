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
package org.broadleafcommerce.core.offer.service.processor;

import org.broadleafcommerce.common.money.Money;
import org.broadleafcommerce.common.service.GenericEntityService;
import org.broadleafcommerce.common.time.SystemTime;
import org.broadleafcommerce.core.offer.dao.CustomerOfferDao;
import org.broadleafcommerce.core.offer.dao.OfferCodeDao;
import org.broadleafcommerce.core.offer.dao.OfferDao;
import org.broadleafcommerce.core.offer.domain.Offer;
import org.broadleafcommerce.core.offer.domain.OfferImpl;
import org.broadleafcommerce.core.offer.domain.OfferQualifyingCriteriaXref;
import org.broadleafcommerce.core.offer.domain.OrderAdjustment;
import org.broadleafcommerce.core.offer.domain.OrderAdjustmentImpl;
import org.broadleafcommerce.core.offer.domain.OrderItemPriceDetailAdjustment;
import org.broadleafcommerce.core.offer.service.OfferDataItemProvider;
import org.broadleafcommerce.core.offer.service.OfferServiceImpl;
import org.broadleafcommerce.core.offer.service.OfferServiceUtilitiesImpl;
import org.broadleafcommerce.core.offer.service.discount.domain.PromotableItemFactoryImpl;
import org.broadleafcommerce.core.offer.service.discount.domain.PromotableOfferUtility;
import org.broadleafcommerce.core.offer.service.discount.domain.PromotableOfferUtilityImpl;
import org.broadleafcommerce.core.offer.service.type.OfferDiscountType;
import org.broadleafcommerce.core.offer.service.type.OfferItemRestrictionRuleType;
import org.broadleafcommerce.core.order.dao.FulfillmentGroupItemDao;
import org.broadleafcommerce.core.order.dao.OrderItemDao;
import org.broadleafcommerce.core.order.domain.DiscreteOrderItem;
import org.broadleafcommerce.core.order.domain.FulfillmentGroupItem;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.domain.OrderItem;
import org.broadleafcommerce.core.order.domain.OrderItemPriceDetail;
import org.broadleafcommerce.core.order.domain.OrderMultishipOption;
import org.broadleafcommerce.core.order.service.FulfillmentGroupService;
import org.broadleafcommerce.core.order.service.OrderItemService;
import org.broadleafcommerce.core.order.service.OrderMultishipOptionService;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.order.service.call.FulfillmentGroupItemRequest;
import org.easymock.EasyMock;
import org.easymock.IAnswer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;

import junit.framework.TestCase;

/**
 * Characterization tests for the Broadleaf offer (promotion/discount) engine.
 *
 * These tests do not assert what the engine <i>should</i> do - they pin what it <i>currently</i> does, so that a
 * future refactor or consolidation of pricing logic can prove it did not move a number. Every expectation in this
 * class was produced by running the engine as shipped.
 *
 * The cart used by every test comes from {@link OfferDataItemProvider#createBasicOrder()}:
 * <ul>
 *   <li>2 x "test1" @ $19.99 (category test1) = $39.98</li>
 *   <li>3 x "test2" @ $29.99 (category test2) = $89.97</li>
 *   <li>subtotal $129.95</li>
 * </ul>
 *
 * The rules exercised here are documented in docs/OFFER_RULE_INVENTORY.md.
 */
public class OfferEngineCharacterizationTest extends TestCase {

    /**
     * MVEL rule matching every item in the standard test cart (categories test1 and test2).
     */
    protected static final String MATCHES_BOTH_CART_CATEGORIES =
            "([MVEL.eval(\"toUpperCase()\",\"test1\"), MVEL.eval(\"toUpperCase()\",\"test2\")] contains "
                    + "MVEL.eval(\"toUpperCase()\", discreteOrderItem.category.name))";

    /**
     * MVEL rule matching only the cheaper item in the standard test cart ($19.99, category test1).
     */
    protected static final String MATCHES_ONLY_CATEGORY_TEST1 =
            "([MVEL.eval(\"toUpperCase()\",\"test1\")] contains "
                    + "MVEL.eval(\"toUpperCase()\", discreteOrderItem.category.name))";

    /**
     * MVEL rule matching only the more expensive item in the standard test cart ($29.99, category test2).
     */
    protected static final String MATCHES_ONLY_CATEGORY_TEST2 =
            "([MVEL.eval(\"toUpperCase()\",\"test2\")] contains "
                    + "MVEL.eval(\"toUpperCase()\", discreteOrderItem.category.name))";

    /**
     * MVEL rule matching nothing in the standard test cart.
     */
    protected static final String MATCHES_NOTHING_IN_CART =
            "([MVEL.eval(\"toUpperCase()\",\"test5\")] contains "
                    + "MVEL.eval(\"toUpperCase()\", discreteOrderItem.category.name))";

    protected static final String ORDER_RULE_ALWAYS_TRUE = "order.subTotal.getAmount()>20";

    protected OfferDao offerDaoMock;
    protected OrderItemDao orderItemDaoMock;
    protected OrderService orderServiceMock;
    protected OrderItemService orderItemServiceMock;
    protected FulfillmentGroupItemDao fgItemDaoMock;
    protected FulfillmentGroupService fgServiceMock;
    protected OrderMultishipOptionService multishipOptionServiceMock;
    protected OfferTimeZoneProcessor offerTimeZoneProcessorMock;
    protected GenericEntityService genericEntityServiceMock;

    protected PromotableOfferUtility promotableOfferUtility;
    protected OfferDataItemProvider dataProvider = new OfferDataItemProvider();
    protected OfferServiceImpl offerService;

    @Override
    protected void setUp() throws Exception {
        CustomerOfferDao customerOfferDaoMock = EasyMock.createMock(CustomerOfferDao.class);
        OfferCodeDao offerCodeDaoMock = EasyMock.createMock(OfferCodeDao.class);
        offerDaoMock = EasyMock.createMock(OfferDao.class);
        orderItemDaoMock = EasyMock.createMock(OrderItemDao.class);
        orderServiceMock = EasyMock.createMock(OrderService.class);
        orderItemServiceMock = EasyMock.createMock(OrderItemService.class);
        fgItemDaoMock = EasyMock.createMock(FulfillmentGroupItemDao.class);
        fgServiceMock = EasyMock.createMock(FulfillmentGroupService.class);
        multishipOptionServiceMock = EasyMock.createMock(OrderMultishipOptionService.class);
        offerTimeZoneProcessorMock = EasyMock.createMock(OfferTimeZoneProcessor.class);
        genericEntityServiceMock = EasyMock.createMock(GenericEntityService.class);
        promotableOfferUtility = new PromotableOfferUtilityImpl();

        OfferServiceUtilitiesImpl offerServiceUtilities = new OfferServiceUtilitiesImpl(promotableOfferUtility);
        offerServiceUtilities.setOfferDao(offerDaoMock);
        offerServiceUtilities.setPromotableItemFactory(new PromotableItemFactoryImpl(promotableOfferUtility));
        offerServiceUtilities.setGenericEntityService(genericEntityServiceMock);

        ItemOfferProcessorImpl itemProcessor = new ItemOfferProcessorImpl(promotableOfferUtility);
        itemProcessor.setOfferDao(offerDaoMock);
        itemProcessor.setOrderItemDao(orderItemDaoMock);
        itemProcessor.setOfferTimeZoneProcessor(offerTimeZoneProcessorMock);
        itemProcessor.setPromotableItemFactory(new PromotableItemFactoryImpl(promotableOfferUtility));
        itemProcessor.setOfferServiceUtilities(offerServiceUtilities);

        OrderOfferProcessorImpl orderProcessor = new OrderOfferProcessorImpl(promotableOfferUtility);
        orderProcessor.setOfferDao(offerDaoMock);
        orderProcessor.setOrderItemDao(orderItemDaoMock);
        orderProcessor.setOfferTimeZoneProcessor(offerTimeZoneProcessorMock);
        orderProcessor.setPromotableItemFactory(new PromotableItemFactoryImpl(promotableOfferUtility));
        orderProcessor.setOfferServiceUtilities(offerServiceUtilities);

        offerService = new OfferServiceImpl();
        offerService.setCustomerOfferDao(customerOfferDaoMock);
        offerService.setOfferCodeDao(offerCodeDaoMock);
        offerService.setOfferDao(offerDaoMock);
        offerService.setOrderOfferProcessor(orderProcessor);
        offerService.setItemOfferProcessor(itemProcessor);
        offerService.setPromotableItemFactory(new PromotableItemFactoryImpl(promotableOfferUtility));
        offerService.setOrderService(orderServiceMock);
    }

    protected void replay() throws Exception {
        EasyMock.expect(orderItemDaoMock.createOrderItemPriceDetail()).andAnswer(OfferDataItemProvider.getCreateOrderItemPriceDetailAnswer()).anyTimes();
        EasyMock.expect(orderItemDaoMock.createOrderItemQualifier()).andAnswer(OfferDataItemProvider.getCreateOrderItemQualifierAnswer()).anyTimes();
        EasyMock.expect(offerDaoMock.createOrderItemPriceDetailAdjustment()).andAnswer(OfferDataItemProvider.getCreateOrderItemPriceDetailAdjustmentAnswer()).anyTimes();
        EasyMock.expect(offerDaoMock.createOrderAdjustment()).andAnswer(new IAnswer<OrderAdjustment>() {

            @Override
            public OrderAdjustment answer() throws Throwable {
                return new OrderAdjustmentImpl();
            }
        }).anyTimes();

        EasyMock.expect(fgServiceMock.addItemToFulfillmentGroup(EasyMock.isA(FulfillmentGroupItemRequest.class), EasyMock.eq(false))).andAnswer(OfferDataItemProvider.getAddItemToFulfillmentGroupAnswer()).anyTimes();
        EasyMock.expect(orderServiceMock.removeItem(EasyMock.isA(Long.class), EasyMock.isA(Long.class), EasyMock.eq(false))).andAnswer(OfferDataItemProvider.getRemoveItemFromOrderAnswer()).anyTimes();
        EasyMock.expect(orderServiceMock.save(EasyMock.isA(Order.class), EasyMock.isA(Boolean.class))).andAnswer(OfferDataItemProvider.getSaveOrderAnswer()).anyTimes();
        EasyMock.expect(orderServiceMock.getAutomaticallyMergeLikeItems()).andReturn(true).anyTimes();

        EasyMock.expect(orderItemServiceMock.saveOrderItem(EasyMock.isA(OrderItem.class))).andAnswer(OfferDataItemProvider.getSaveOrderItemAnswer()).anyTimes();
        EasyMock.expect(fgItemDaoMock.save(EasyMock.isA(FulfillmentGroupItem.class))).andAnswer(OfferDataItemProvider.getSaveFulfillmentGroupItemAnswer()).anyTimes();
        EasyMock.expect(fgItemDaoMock.create()).andAnswer(OfferDataItemProvider.getCreateFulfillmentGroupItemAnswer()).anyTimes();
        fgItemDaoMock.delete(EasyMock.isA(FulfillmentGroupItem.class));
        EasyMock.expectLastCall().anyTimes();

        EasyMock.expect(multishipOptionServiceMock.findOrderMultishipOptions(EasyMock.isA(Long.class))).andAnswer(new IAnswer<List<OrderMultishipOption>>() {

            @Override
            public List<OrderMultishipOption> answer() throws Throwable {
                return new ArrayList<OrderMultishipOption>();
            }
        }).anyTimes();
        multishipOptionServiceMock.deleteAllOrderMultishipOptions(EasyMock.isA(Order.class));
        EasyMock.expectLastCall().anyTimes();
        EasyMock.expect(fgServiceMock.collapseToOneShippableFulfillmentGroup(EasyMock.isA(Order.class), EasyMock.eq(false))).andAnswer(OfferDataItemProvider.getSameOrderAnswer()).anyTimes();
        EasyMock.expect(offerTimeZoneProcessorMock.getTimeZone(EasyMock.isA(OfferImpl.class))).andReturn(TimeZone.getTimeZone("CST")).anyTimes();

        EasyMock.replay(offerDaoMock);
        EasyMock.replay(orderItemDaoMock);
        EasyMock.replay(orderServiceMock);
        EasyMock.replay(orderItemServiceMock);
        EasyMock.replay(fgItemDaoMock);
        EasyMock.replay(fgServiceMock);
        EasyMock.replay(multishipOptionServiceMock);
        EasyMock.replay(offerTimeZoneProcessorMock);
    }

    protected void verify() {
        EasyMock.verify(offerDaoMock);
        EasyMock.verify(orderItemDaoMock);
        EasyMock.verify(orderServiceMock);
        EasyMock.verify(orderItemServiceMock);
        EasyMock.verify(fgItemDaoMock);
        EasyMock.verify(fgServiceMock);
        EasyMock.verify(multishipOptionServiceMock);
        EasyMock.verify(offerTimeZoneProcessorMock);
    }

    // -----------------------------------------------------------------------------------------------------------
    // Test scenarios
    // -----------------------------------------------------------------------------------------------------------

    /**
     * Two item promotions targeting the same items. "Stackable" in the Broadleaf admin is not a stored flag - it is
     * offerItemTargetRuleType (see OfferCustomPersistenceHandler.buildOfferItemTargetRuleTypeProperty). With
     * TARGET/QUALIFIER_TARGET a unit that already received a discount can receive a second one; with NONE it cannot.
     */
    public void testTwoStackableOffersBothDiscountTheSameUnitsButNonStackableOffersDoNot() throws Exception {
        replay();

        // Non-stackable (offerItemTargetRuleType = NONE): a unit discounted by one promotion is off limits to the other
        Offer nonStackableA = itemOfferMatchingWholeCart(1L, OfferDiscountType.PERCENT_OFF, BigDecimal.valueOf(10));
        Offer nonStackableB = itemOfferMatchingWholeCart(2L, OfferDiscountType.PERCENT_OFF, BigDecimal.valueOf(10));

        Order order = dataProvider.createBasicOrder();
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(nonStackableA, nonStackableB), order);

        // All 5 units are consumed by the first offer; the second offer discounts nothing
        assertEquals(5, discountedUnitCount(order, nonStackableA));
        assertEquals(0, discountedUnitCount(order, nonStackableB));
        assertEquals(5, totalDiscountedUnitCount(order));

        // Stackable (offerItemTargetRuleType = QUALIFIER_TARGET): the same units may be reused as targets
        Offer stackableA = itemOfferMatchingWholeCart(3L, OfferDiscountType.PERCENT_OFF, BigDecimal.valueOf(10));
        stackableA.setOfferItemTargetRuleType(OfferItemRestrictionRuleType.QUALIFIER_TARGET);
        Offer stackableB = itemOfferMatchingWholeCart(4L, OfferDiscountType.PERCENT_OFF, BigDecimal.valueOf(10));
        stackableB.setOfferItemTargetRuleType(OfferItemRestrictionRuleType.QUALIFIER_TARGET);

        order = dataProvider.createBasicOrder();
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(stackableA, stackableB), order);

        // Every unit now carries both discounts
        assertEquals(5, discountedUnitCount(order, stackableA));
        assertEquals(5, discountedUnitCount(order, stackableB));
        assertEquals(10, totalDiscountedUnitCount(order));

        verify();
    }

    /**
     * Priority is the first sort key in every comparator the engine uses (ItemOfferComparator,
     * ItemOfferQtyOneComparator, ItemOfferWeightedPercentComparator), so a lower priority number is applied first and
     * - when the offers cannot stack - wins outright even though the customer would have saved more with the other one.
     */
    public void testTheLowerPriorityNumberWinsEvenWhenTheOtherOfferWouldSaveMore() throws Exception {
        replay();

        Offer smallDiscountUrgentPriority = itemOfferMatchingWholeCart(1L, OfferDiscountType.PERCENT_OFF, BigDecimal.valueOf(10));
        smallDiscountUrgentPriority.setPriority(1);
        Offer bigDiscountLowerPriority = itemOfferMatchingWholeCart(2L, OfferDiscountType.PERCENT_OFF, BigDecimal.valueOf(50));
        bigDiscountLowerPriority.setPriority(5);

        Order order = dataProvider.createBasicOrder();
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(smallDiscountUrgentPriority, bigDiscountLowerPriority), order);

        // The 10% offer takes every unit; the 50% offer never gets applied
        assertEquals(5, discountedUnitCount(order, smallDiscountUrgentPriority));
        assertEquals(0, discountedUnitCount(order, bigDiscountLowerPriority));
        // discounts are rounded per unit, not on the order total: 2 x $2.00 + 3 x $3.00
        assertEquals(new Money("13.00"), order.getTotalAdjustmentsValue());

        // Swap the priorities and the customer gets the better deal instead
        smallDiscountUrgentPriority.setPriority(5);
        bigDiscountLowerPriority.setPriority(1);

        order = dataProvider.createBasicOrder();
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(smallDiscountUrgentPriority, bigDiscountLowerPriority), order);

        assertEquals(0, discountedUnitCount(order, smallDiscountUrgentPriority));
        assertEquals(5, discountedUnitCount(order, bigDiscountLowerPriority));
        assertEquals(new Money("65.00"), order.getTotalAdjustmentsValue());

        verify();
    }

    /**
     * With equal priority the tie-break is potential savings (ItemOfferComparator), so a flat $10 off beats 10% off on
     * this cart. Note the amount-off value is applied per unit, not once per order.
     */
    public void testAtEqualPriorityTheAmountOffOfferBeatsThePercentOffOfferOnThisCart() throws Exception {
        replay();

        Offer tenPercentOff = itemOfferMatchingWholeCart(1L, OfferDiscountType.PERCENT_OFF, BigDecimal.valueOf(10));
        Offer tenDollarsOffEachUnit = itemOfferMatchingWholeCart(2L, OfferDiscountType.AMOUNT_OFF, BigDecimal.valueOf(10));
        assertEquals(tenPercentOff.getPriority(), tenDollarsOffEachUnit.getPriority());

        Order order = dataProvider.createBasicOrder();
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(tenPercentOff, tenDollarsOffEachUnit), order);

        assertEquals(0, discountedUnitCount(order, tenPercentOff));
        assertEquals(5, discountedUnitCount(order, tenDollarsOffEachUnit));
        // $10 off each of the 5 units, not $10 off the order
        assertEquals(new Money("50.00"), order.getTotalAdjustmentsValue());

        verify();
    }

    /**
     * Qualifier/target criteria: "buy a test1 item to get a discount on a test2 item". The qualifying unit is consumed
     * and does not itself receive the discount, and the offer disappears entirely if the qualifying item is not in the
     * cart.
     */
    public void testAnOfferWithAQualifierOnlyDiscountsTheTargetAndOnlyWhenTheQualifyingItemIsInTheCart() throws Exception {
        replay();

        Offer buyTest1GetTest2Discounted = dataProvider.createItemBasedOfferWithItemCriteria(
                ORDER_RULE_ALWAYS_TRUE,
                OfferDiscountType.PERCENT_OFF,
                MATCHES_ONLY_CATEGORY_TEST1,
                MATCHES_ONLY_CATEGORY_TEST2
        ).get(0);
        buyTest1GetTest2Discounted.setId(1L);

        Order order = dataProvider.createBasicOrder();
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(buyTest1GetTest2Discounted), order);

        // 2 test1 units qualify, so the promotion runs twice and discounts 2 of the 3 test2 units.
        // No test1 unit is discounted - qualifiers are consumed, not rewarded.
        assertEquals(2, discountedUnitCount(order, buyTest1GetTest2Discounted));
        assertEquals(0, discountedUnitCountForItemNamed(order, "test1"));
        assertEquals(2, discountedUnitCountForItemNamed(order, "test2"));

        // Same offer, but nothing in the cart can qualify -> no discount at all
        Offer qualifierNotInCart = dataProvider.createItemBasedOfferWithItemCriteria(
                ORDER_RULE_ALWAYS_TRUE,
                OfferDiscountType.PERCENT_OFF,
                MATCHES_NOTHING_IN_CART,
                MATCHES_ONLY_CATEGORY_TEST2
        ).get(0);
        qualifierNotInCart.setId(2L);

        order = dataProvider.createBasicOrder();
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(qualifierNotInCart), order);

        assertEquals(0, totalDiscountedUnitCount(order));
        assertEquals(new Money("0.00"), order.getTotalAdjustmentsValue());

        verify();
    }

    /**
     * An order-level offer and an item-level offer do not add up when either is totalitarian: the engine applies both
     * and then throws away the smaller side (OrderOfferProcessorImpl.compareAndAdjustOrderAndItemOffers). Ties are
     * resolved in favour of the order offer.
     */
    public void testATotalitarianOrderOfferAndAnItemOfferNeverBothSurvive() throws Exception {
        replay();

        // Order offer worth 20% of $129.95 = $25.99; item offer worth 10% = $13.00. The order offer wins.
        Offer totalitarianOrderOffer = dataProvider.createOrderBasedOffer(ORDER_RULE_ALWAYS_TRUE, OfferDiscountType.PERCENT_OFF).get(0);
        totalitarianOrderOffer.setId(1L);
        totalitarianOrderOffer.setValue(BigDecimal.valueOf(20));
        totalitarianOrderOffer.setTotalitarianOffer(true);
        Offer itemOffer = itemOfferMatchingWholeCart(2L, OfferDiscountType.PERCENT_OFF, BigDecimal.valueOf(10));

        Order order = dataProvider.createBasicOrder();
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(totalitarianOrderOffer, itemOffer), order);

        assertEquals(0, totalDiscountedUnitCount(order));
        assertEquals(1, order.getOrderAdjustments().size());
        assertEquals(new Money("25.99"), order.getTotalAdjustmentsValue());

        // Make the item offer the better one (50% = $65.00) and the order offer is discarded instead
        itemOffer.setValue(BigDecimal.valueOf(50));

        order = dataProvider.createBasicOrder();
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(totalitarianOrderOffer, itemOffer), order);

        assertEquals(5, totalDiscountedUnitCount(order));
        assertEquals(0, order.getOrderAdjustments().size());
        assertEquals(new Money("65.00"), order.getTotalAdjustmentsValue());

        verify();
    }

    /**
     * maxUsesPerOrder caps how many times an item promotion may be handed out within a single order;
     * 0 means unlimited (OfferImpl.isUnlimitedUsePerOrder).
     */
    public void testMaxUsesPerOrderLimitsHowManyUnitsAnItemOfferCanDiscount() throws Exception {
        replay();

        Offer cappedAtTwoUses = itemOfferMatchingWholeCart(1L, OfferDiscountType.PERCENT_OFF, BigDecimal.valueOf(10));
        assertTrue(cappedAtTwoUses.isUnlimitedUsePerOrder());
        cappedAtTwoUses.setMaxUsesPerOrder(2);

        Order order = dataProvider.createBasicOrder();
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(cappedAtTwoUses), order);

        assertEquals(2, discountedUnitCount(order, cappedAtTwoUses));

        verify();
    }

    /**
     * FIX_PRICE means "this unit now costs X", not "take X off". Only ORDER_ITEM offers may use it - an ORDER offer
     * with FIX_PRICE is dropped with a log warning (OrderOfferProcessorImpl.filterOrderLevelOffer).
     */
    public void testFixedPriceOfferSetsTheUnitPriceWhileAFixedPriceOrderOfferIsIgnoredEntirely() throws Exception {
        replay();

        Offer everyUnitCostsFiveDollars = itemOfferMatchingWholeCart(1L, OfferDiscountType.FIX_PRICE, BigDecimal.valueOf(5));

        Order order = dataProvider.createBasicOrder();
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(everyUnitCostsFiveDollars), order);

        // 2 x ($19.99 - $5.00) + 3 x ($29.99 - $5.00) = $29.98 + $74.97
        assertEquals(5, discountedUnitCount(order, everyUnitCostsFiveDollars));
        assertEquals(new Money("104.95"), order.getTotalAdjustmentsValue());
        assertEquals(new Money("25.00"), order.getSubTotal());

        Offer fixedPriceOrderOffer = dataProvider.createOrderBasedOffer(ORDER_RULE_ALWAYS_TRUE, OfferDiscountType.FIX_PRICE).get(0);
        fixedPriceOrderOffer.setId(2L);

        order = dataProvider.createBasicOrder();
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(fixedPriceOrderOffer), order);

        assertEquals(0, order.getOrderAdjustments().size());
        assertEquals(new Money("129.95"), order.getSubTotal());

        verify();
    }

    /**
     * Date filtering happens before any pricing work (AbstractBaseProcessor.filterOffers -> removeOutOfDateOffers).
     * An offer with no start date is treated as not yet active, which is easy to miss.
     */
    public void testOffersOutsideTheirActiveDatesAreDroppedIncludingOffersWithNoStartDate() throws Exception {
        replay();

        Offer expiredYesterday = itemOfferMatchingWholeCart(1L, OfferDiscountType.PERCENT_OFF, BigDecimal.valueOf(10));
        expiredYesterday.setStartDate(daysFromNow(-10));
        expiredYesterday.setEndDate(daysFromNow(-1));

        Offer startsTomorrow = itemOfferMatchingWholeCart(2L, OfferDiscountType.PERCENT_OFF, BigDecimal.valueOf(10));
        startsTomorrow.setStartDate(daysFromNow(1));
        startsTomorrow.setEndDate(daysFromNow(10));

        Offer noStartDateAtAll = itemOfferMatchingWholeCart(3L, OfferDiscountType.PERCENT_OFF, BigDecimal.valueOf(10));
        noStartDateAtAll.setStartDate(null);
        noStartDateAtAll.setEndDate(null);

        Order order = dataProvider.createBasicOrder();
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(expiredYesterday, startsTomorrow, noStartDateAtAll), order);

        assertEquals(0, totalDiscountedUnitCount(order));
        assertEquals(new Money("129.95"), order.getSubTotal());

        verify();
    }

    /**
     * An offer can require a minimum order subtotal (Offer.getOrderMinSubTotal), checked against the subtotal before
     * adjustments in OfferServiceUtilitiesImpl.orderMeetsSubtotalRequirements.
     */
    public void testAnOrderOfferIsSkippedWhenTheCartIsBelowItsMinimumSubtotal() throws Exception {
        replay();

        Offer needsTwoHundredDollarCart = dataProvider.createOrderBasedOffer(ORDER_RULE_ALWAYS_TRUE, OfferDiscountType.PERCENT_OFF).get(0);
        needsTwoHundredDollarCart.setId(1L);
        needsTwoHundredDollarCart.setOrderMinSubTotal(new Money("200.00"));

        Order order = dataProvider.createBasicOrder();
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(needsTwoHundredDollarCart), order);

        assertEquals(0, order.getOrderAdjustments().size());

        // Lower the threshold below the $129.95 cart and the same offer now applies
        needsTwoHundredDollarCart.setOrderMinSubTotal(new Money("100.00"));

        order = dataProvider.createBasicOrder();
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(needsTwoHundredDollarCart), order);

        assertEquals(1, order.getOrderAdjustments().size());
        assertEquals(new Money("13.00"), order.getTotalAdjustmentsValue());

        verify();
    }

    /**
     * Combinability at the order level: two combinable order offers both apply and the second one is calculated on the
     * subtotal left by the first (PromotableOrderImpl.canApplyOrderOffer / OrderOfferProcessorImpl.applyAllOrderOffers).
     * A non-combinable order offer stops the loop.
     */
    public void testTwoCombinableOrderOffersCompoundWhileANonCombinableOneAppliesAlone() throws Exception {
        replay();

        Offer tenPercentOffOrder = dataProvider.createOrderBasedOffer(ORDER_RULE_ALWAYS_TRUE, OfferDiscountType.PERCENT_OFF).get(0);
        tenPercentOffOrder.setId(1L);
        Offer anotherTenPercentOffOrder = dataProvider.createOrderBasedOffer(ORDER_RULE_ALWAYS_TRUE, OfferDiscountType.PERCENT_OFF).get(0);
        anotherTenPercentOffOrder.setId(2L);

        Order order = dataProvider.createBasicOrder();
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(tenPercentOffOrder, anotherTenPercentOffOrder), order);

        // $13.00 off $129.95, then $11.70 off the remaining $116.95 - the second discount is NOT 10% of the original
        assertEquals(2, order.getOrderAdjustments().size());
        assertEquals(new Money("24.70"), order.getTotalAdjustmentsValue());

        anotherTenPercentOffOrder.setCombinableWithOtherOffers(false);

        order = dataProvider.createBasicOrder();
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(anotherTenPercentOffOrder, tenPercentOffOrder), order);

        assertEquals(1, order.getOrderAdjustments().size());
        assertEquals(new Money("13.00"), order.getTotalAdjustmentsValue());

        verify();
    }

    /**
     * A promotion flagged "does not apply to sale price" is thrown away for any item whose sale price already beats the
     * discounted retail price (PromotableOrderItemPriceDetailImpl.chooseSaleOrRetailAdjustments).
     */
    public void testARetailOnlyOfferIsDiscardedForItemsAlreadyOnABetterSalePrice() throws Exception {
        replay();

        Offer retailOnlyHalfOff = itemOfferMatchingWholeCart(1L, OfferDiscountType.PERCENT_OFF, BigDecimal.valueOf(50));
        retailOnlyHalfOff.setApplyDiscountToSalePrice(false);

        Order order = dataProvider.createBasicOrder();
        // test1 goes on sale at $1.00 (better than 50% off $19.99); test2 at $25.00 (worse than 50% off $29.99)
        ((DiscreteOrderItem) order.getOrderItems().get(0)).getSku().setSalePrice(new Money(1D));
        ((DiscreteOrderItem) order.getOrderItems().get(1)).getSku().setSalePrice(new Money(25D));
        order.updatePrices();
        offerService.applyAndSaveOffersToOrder(offerList(retailOnlyHalfOff), order);

        assertEquals(0, discountedUnitCountForItemNamed(order, "test1"));
        assertEquals(3, discountedUnitCountForItemNamed(order, "test2"));
        // 2 x $1.00 sale + 3 x $14.99 discounted retail ($29.99 less a $15.00 adjustment)
        assertEquals(new Money("46.97"), order.getSubTotal());

        verify();
    }

    // -----------------------------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------------------------

    protected Date daysFromNow(int days) {
        return new Date(SystemTime.asMillis() + (days * 24L * 60L * 60L * 1000L));
    }

    /**
     * An ORDER_ITEM offer with no qualifying criteria that targets one unit of anything in the standard test cart.
     */
    protected Offer itemOfferMatchingWholeCart(Long id, OfferDiscountType discountType, BigDecimal value) {
        Offer offer = dataProvider.createItemBasedOffer(
                ORDER_RULE_ALWAYS_TRUE,
                MATCHES_BOTH_CART_CATEGORIES,
                discountType
        ).get(0);
        offer.setId(id);
        offer.setValue(value);
        // OfferDataItemProvider leaves this null; a persisted OfferImpl always has an (empty) set
        offer.setQualifyingItemCriteriaXref(new HashSet<OfferQualifyingCriteriaXref>());
        return offer;
    }

    protected List<Offer> offerList(Offer... offers) {
        List<Offer> offerList = new ArrayList<Offer>();
        for (Offer offer : offers) {
            offerList.add(offer);
        }
        return offerList;
    }

    /**
     * Number of units (not price details) that received an adjustment from the given offer.
     */
    protected int discountedUnitCount(Order order, Offer offer) {
        int count = 0;
        for (OrderItem item : order.getOrderItems()) {
            for (OrderItemPriceDetail detail : item.getOrderItemPriceDetails()) {
                for (OrderItemPriceDetailAdjustment adjustment : detail.getOrderItemPriceDetailAdjustments()) {
                    if (adjustment.getOffer().getId().equals(offer.getId())) {
                        count += detail.getQuantity();
                    }
                }
            }
        }
        return count;
    }

    protected int discountedUnitCountForItemNamed(Order order, String itemName) {
        int count = 0;
        for (OrderItem item : order.getOrderItems()) {
            if (!itemName.equals(item.getName())) {
                continue;
            }
            for (OrderItemPriceDetail detail : item.getOrderItemPriceDetails()) {
                if (!detail.getOrderItemPriceDetailAdjustments().isEmpty()) {
                    count += detail.getQuantity();
                }
            }
        }
        return count;
    }

    /**
     * Total number of discounts handed out, counted per unit; two stacked offers on one unit count as two.
     */
    protected int totalDiscountedUnitCount(Order order) {
        int count = 0;
        for (OrderItem item : order.getOrderItems()) {
            for (OrderItemPriceDetail detail : item.getOrderItemPriceDetails()) {
                count += detail.getOrderItemPriceDetailAdjustments().size() * detail.getQuantity();
            }
        }
        return count;
    }

}
