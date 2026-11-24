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

import org.broadleafcommerce.core.catalog.domain.Product;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.service.call.OrderItemRequestDTO;
import org.broadleafcommerce.core.pricing.service.PricingService;
import org.broadleafcommerce.core.pricing.service.exception.PricingException;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.test.CommonSetupBaseTest;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

/**
 * Covers {@link OrderServiceImpl#save(Order, Boolean)} with pricing when no transaction is open yet, as from a storefront
 * request where only the open-entity-manager-in-view entity manager is bound.
 */
public class CartPricingFlushModeTest extends CommonSetupBaseTest {

    @Resource(name = "entityManagerFactory")
    protected EntityManagerFactory emf;

    @Resource(name = "blTransactionManager")
    protected PlatformTransactionManager transactionManager;

    @Resource(name = "blPricingService")
    protected PricingService pricingService;

    @PersistenceContext(unitName = "blPU")
    protected EntityManager em;

    @Test(groups = "testCartPricingFlushMode")
    public void testPricingKeepsManualFlushModeAndFlushesAtPricingCommit() throws Exception {
        Order cart = createUnpricedCart("flush mode product");
        BigDecimal totalBeforePricing = readCommittedTotal(cart.getId());
        List<FlushMode> flushModesDuringPricing = new ArrayList<>();
        List<BigDecimal> totalsCommittedByPricing = new ArrayList<>();

        Order priced = saveWithPricing(cart, order -> {
            flushModesDuringPricing.add(em.unwrap(Session.class).getHibernateFlushMode());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    totalsCommittedByPricing.add(readCommittedTotal(order.getId()));
                }
            });
            return pricingService.executePricing(order);
        });

        assert flushModesDuringPricing.equals(List.of(FlushMode.MANUAL)) : flushModesDuringPricing;
        assert priced.getTotal() != null && priced.getTotal().greaterThan(BigDecimal.ZERO);
        assert !sameAmount(totalBeforePricing, priced.getTotal().getAmount()) : totalBeforePricing;
        assert totalsCommittedByPricing.size() == 1 : totalsCommittedByPricing;
        assert sameAmount(totalsCommittedByPricing.get(0), priced.getTotal().getAmount())
                : totalsCommittedByPricing.get(0) + " != " + priced.getTotal();
    }

    @Test(groups = "testCartPricingFlushMode")
    public void testFailedPricingRollsBackAndRestoresFlushMode() throws Exception {
        Order cart = createUnpricedCart("flush mode rollback product");
        BigDecimal totalBeforePricing = readCommittedTotal(cart.getId());
        List<FlushMode> flushModesAroundSave = new ArrayList<>();

        PricingService failingPricingService = order -> {
            pricingService.executePricing(order);
            throw new IllegalStateException("pricing failed after changing the order");
        };
        PricingException thrown = null;
        try {
            saveWithPricing(cart, failingPricingService, flushModesAroundSave);
        } catch (PricingException e) {
            thrown = e;
        }

        assert thrown != null && thrown.getCause() instanceof IllegalStateException : thrown;
        assert flushModesAroundSave.size() == 2 && flushModesAroundSave.get(0) == flushModesAroundSave.get(1)
                : flushModesAroundSave;
        assert !TransactionSynchronizationManager.isActualTransactionActive();
        assert sameAmount(readCommittedTotal(cart.getId()), totalBeforePricing);
    }

    protected Order createUnpricedCart(String productName) throws Exception {
        Product product = addTestProduct(productName, "flush mode category");
        Customer customer = customerService.saveCustomer(customerService.createCustomerFromId(null));
        Order cart = orderService.createNewCartForCustomer(customer);
        return orderService.addItem(cart.getId(), new OrderItemRequestDTO(product.getId(),
                product.getDefaultSku().getId(), product.getCategory().getId(), 2), false);
    }

    protected Order saveWithPricing(Order cart, PricingService pricing) throws PricingException {
        return saveWithPricing(cart, pricing, new ArrayList<>());
    }

    /**
     * Calls {@code save(cart, true)} with a request-scoped entity manager bound and no outer transaction. The given pricing
     * runs the way {@code @Transactional} {@code executePricing} does: in a transaction it joins or starts.
     */
    protected Order saveWithPricing(Order cart, PricingService pricing, List<FlushMode> flushModesAroundSave)
            throws PricingException {
        OrderServiceImpl target = AopTestUtils.getUltimateTargetObject(orderService);
        TransactionTemplate pricingTransaction = new TransactionTemplate(transactionManager);
        PricingService transactionalPricing = order -> pricingTransaction.execute(status -> {
            try {
                return pricing.executePricing(order);
            } catch (PricingException e) {
                throw new IllegalStateException(e);
            }
        });

        TransactionSynchronizationManager.bindResource(emf, new EntityManagerHolder(emf.createEntityManager()));
        ReflectionTestUtils.setField(target, "pricingService", transactionalPricing);
        try {
            flushModesAroundSave.add(em.unwrap(Session.class).getHibernateFlushMode());
            try {
                return orderService.save(cart, true);
            } finally {
                flushModesAroundSave.add(em.unwrap(Session.class).getHibernateFlushMode());
            }
        } finally {
            ReflectionTestUtils.setField(target, "pricingService", pricingService);
            EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager.unbindResource(emf);
            holder.getEntityManager().close();
        }
    }

    /**
     * Reads the order total straight from the database, bypassing the persistence context and the second-level cache.
     */
    protected BigDecimal readCommittedTotal(Long orderId) {
        EntityManager fresh = emf.createEntityManager();
        try {
            Object total = fresh.createNativeQuery("SELECT ORDER_TOTAL FROM BLC_ORDER WHERE ORDER_ID = ?1")
                    .setParameter(1, orderId)
                    .getSingleResult();
            return total == null ? null : new BigDecimal(total.toString());
        } finally {
            fresh.close();
        }
    }

    protected boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? Objects.equals(a, b) : a.compareTo(b) == 0;
    }
}
