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
import org.broadleafcommerce.core.order.domain.OrderImpl;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

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
    public void testPricingKeepsManualFlushModeWithoutOuterTransaction() throws Exception {
        Product product = addTestProduct("flush mode product", "flush mode category");
        Customer customer = customerService.saveCustomer(customerService.createCustomerFromId(null));
        Order cart = orderService.createNewCartForCustomer(customer);
        cart = orderService.addItem(cart.getId(), new OrderItemRequestDTO(product.getId(),
                product.getDefaultSku().getId(), product.getCategory().getId(), 2), false);

        OrderServiceImpl target = AopTestUtils.getUltimateTargetObject(orderService);
        List<FlushMode> flushModesDuringPricing = new ArrayList<>();
        TransactionTemplate pricingTransaction = new TransactionTemplate(transactionManager);
        PricingService recordingPricingService = order -> pricingTransaction.execute(status -> {
            flushModesDuringPricing.add(em.unwrap(Session.class).getHibernateFlushMode());
            try {
                return pricingService.executePricing(order);
            } catch (PricingException e) {
                throw new RuntimeException(e);
            }
        });

        // Mirrors a storefront request: an open-entity-manager-in-view entity manager and no outer transaction
        TransactionSynchronizationManager.bindResource(emf, new EntityManagerHolder(emf.createEntityManager()));
        ReflectionTestUtils.setField(target, "pricingService", recordingPricingService);
        Order priced;
        try {
            priced = orderService.save(cart, true);
        } finally {
            ReflectionTestUtils.setField(target, "pricingService", pricingService);
            EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager.unbindResource(emf);
            holder.getEntityManager().close();
        }

        assert flushModesDuringPricing.size() == 1 : flushModesDuringPricing;
        assert flushModesDuringPricing.get(0) == FlushMode.MANUAL : flushModesDuringPricing;
        assert priced.getTotal() != null && priced.getTotal().greaterThan(BigDecimal.ZERO);

        EntityManager fresh = emf.createEntityManager();
        try {
            Order reloaded = fresh.find(OrderImpl.class, priced.getId());
            assert reloaded.getSubTotal().equals(priced.getSubTotal()) : reloaded.getSubTotal() + " != " + priced.getSubTotal();
            assert reloaded.getTotal().equals(priced.getTotal()) : reloaded.getTotal() + " != " + priced.getTotal();
        } finally {
            fresh.close();
        }
    }
}
