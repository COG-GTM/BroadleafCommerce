/*-
 * #%L
 * BroadleafCommerce Common Libraries
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
package org.broadleafcommerce.common.payment.service;

import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Marks a request as a server-to-server payment gateway callback whose origin the gateway module has explicitly
 * authenticated (for example by verifying a webhook signature or a mutually authenticated connection).
 * <p>
 * Order and saved-payment ownership checks in the payment gateway services compare the referenced order or customer
 * with the customer bound to the current request. A server-to-server callback has no shopper session, so it is
 * typically bound to a new anonymous customer and those checks reject it. A gateway module's webhook endpoint should
 * call {@link #markAuthenticated(HttpServletRequest)} only after it has authenticated the gateway; the ownership checks
 * are then skipped for that request. Shopper-facing return endpoints must never set this marker.
 * <p>
 * The marker is a servlet request attribute, which cannot be supplied by the HTTP client.
 */
public final class PaymentGatewayCallbackAuthentication {

    public static final String AUTHENTICATED_GATEWAY_CALLBACK_ATTRIBUTE = "blAuthenticatedPaymentGatewayCallback";

    private PaymentGatewayCallbackAuthentication() {
    }

    public static void markAuthenticated(HttpServletRequest request) {
        request.setAttribute(AUTHENTICATED_GATEWAY_CALLBACK_ATTRIBUTE, Boolean.TRUE);
    }

    public static boolean isAuthenticated(WebRequest request) {
        return request != null && Boolean.TRUE.equals(
                request.getAttribute(AUTHENTICATED_GATEWAY_CALLBACK_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
        );
    }

    public static boolean isCurrentRequestAuthenticated() {
        BroadleafRequestContext context = BroadleafRequestContext.getBroadleafRequestContext(false);
        return context != null && isAuthenticated(context.getWebRequest());
    }

}
