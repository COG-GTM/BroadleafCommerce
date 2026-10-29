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
package org.broadleafcommerce.core.registration;

import org.broadleafcommerce.common.extension.ExtensionResultStatusType;
import org.broadleafcommerce.common.util.BLCRequestUtils;
import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.extension.PostUpdateOrderExtensionHandler;
import org.broadleafcommerce.core.order.extension.PostUpdateOrderExtensionManager;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.service.CustomerService;
import org.broadleafcommerce.profile.core.service.listener.PostRegistrationObserver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

/**
 * When {@code merge.orders.after.registration=true}, assigns guest orders that share the newly registered customer's
 * email address to that customer.
 * <p>
 * Registration does not prove control of the email address, so only guest orders owned by a customer bound to the
 * registering browser session (the registering customer itself or the session's anonymous customer) are merged.
 * Implementors that verify email ownership can widen this by overriding
 * {@link #isOrderEligibleForMerge(Customer, Order, Set)}.
 */
@Component("blMergeOrdersByEmailPostRegistrationObserver")
public class MergeOrdersByEmailPostRegistrationObserver implements PostRegistrationObserver {

    // Mirrors CustomerStateRequestProcessor, which lives in broadleaf-profile-web
    protected static final String ANONYMOUS_CUSTOMER_SESSION_ATTRIBUTE_NAME = "_blc_anonymousCustomer";
    protected static final String ANONYMOUS_CUSTOMER_ID_SESSION_ATTRIBUTE_NAME = "_blc_anonymousCustomerId";

    @Value("${merge.orders.after.registration:false}")
    private boolean enabled;

    @Resource(name = "blCustomerService")
    private CustomerService customerService;

    @Resource(name = "blOrderService")
    private OrderService orderService;

    @Resource(name = "blPostUpdateOrderExtensionManager")
    private PostUpdateOrderExtensionManager extensionManager;

    @PostConstruct
    protected void init() {
        if (enabled) {
            customerService.addPostRegisterListener(this);
        }
    }

    @Override
    public void processRegistrationEvent(Customer customer) {
        Set<Long> sessionCustomerIds = resolveSessionCustomerIds(customer);
        List<Order> orders = orderService.findOrdersByEmail(customer.getEmailAddress());
        List<Order> updOrders = new ArrayList<>();
        for (Order o : orders) {
            if (isOrderEligibleForMerge(customer, o, sessionCustomerIds)) {
                o.setCustomer(customer);
                updOrders.add(o);
            }
        }
        List<PostUpdateOrderExtensionHandler> handlers = extensionManager.getHandlers();
        for (PostUpdateOrderExtensionHandler h : handlers) {
            ExtensionResultStatusType status = h.postUpdateAll(updOrders);
            if (!extensionManager.shouldContinue(status, null, null, null)) {
                break;
            }
        }
    }

    /**
     * Only guest orders whose customer is bound to the registering session may be merged; matching on the
     * (unverified) email address alone would let anyone claim another shopper's guest orders.
     */
    protected boolean isOrderEligibleForMerge(Customer customer, Order order, Set<Long> sessionCustomerIds) {
        Customer orderCustomer = order.getCustomer();
        return orderCustomer != null
                && !orderCustomer.isRegistered()
                && orderCustomer.getId() != null
                && sessionCustomerIds.contains(orderCustomer.getId());
    }

    /**
     * Ids of the customers bound to the current browser session: the registering customer and the anonymous
     * customer stored in session by {@code CustomerStateRequestProcessor}.
     */
    protected Set<Long> resolveSessionCustomerIds(Customer customer) {
        Set<Long> ids = new HashSet<>();
        if (customer.getId() != null) {
            ids.add(customer.getId());
        }
        BroadleafRequestContext context = BroadleafRequestContext.getBroadleafRequestContext();
        WebRequest request = context == null ? null : context.getWebRequest();
        if (request != null) {
            Object anonymousCustomer = BLCRequestUtils.getSessionAttributeIfOk(request, ANONYMOUS_CUSTOMER_SESSION_ATTRIBUTE_NAME);
            if (anonymousCustomer instanceof Customer && ((Customer) anonymousCustomer).getId() != null) {
                ids.add(((Customer) anonymousCustomer).getId());
            }
            Object anonymousCustomerId = BLCRequestUtils.getSessionAttributeIfOk(request, ANONYMOUS_CUSTOMER_ID_SESSION_ATTRIBUTE_NAME);
            if (anonymousCustomerId instanceof Long) {
                ids.add((Long) anonymousCustomerId);
            }
        }
        return ids;
    }

}
