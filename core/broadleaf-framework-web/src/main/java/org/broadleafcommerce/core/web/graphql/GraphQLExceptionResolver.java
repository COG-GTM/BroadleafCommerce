/*-
 * #%L
 * BroadleafCommerce Framework Web
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
package org.broadleafcommerce.core.web.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.broadleafcommerce.core.offer.service.exception.OfferMaxUseExceededException;
import org.broadleafcommerce.core.order.service.exception.AddToCartException;
import org.broadleafcommerce.core.order.service.exception.IllegalCartOperationException;
import org.broadleafcommerce.core.order.service.exception.RemoveFromCartException;
import org.broadleafcommerce.core.order.service.exception.UpdateCartException;
import org.broadleafcommerce.core.pricing.service.exception.PricingException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class GraphQLExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        String code = resolveCode(ex);
        Map<String, Object> extensions = new HashMap<>();
        extensions.put("code", code);
        return GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.INTERNAL_ERROR)
                .message(ex.getMessage() != null ? ex.getMessage() : code)
                .extensions(extensions)
                .build();
    }

    protected String resolveCode(Throwable ex) {
        if (ex instanceof OfferMaxUseExceededException) {
            return "OFFER_MAX_USE_EXCEEDED";
        }
        if (ex instanceof AddToCartException) {
            return "ADD_TO_CART_ERROR";
        }
        if (ex instanceof RemoveFromCartException) {
            return "REMOVE_FROM_CART_ERROR";
        }
        if (ex instanceof UpdateCartException) {
            return "UPDATE_CART_ERROR";
        }
        if (ex instanceof IllegalCartOperationException) {
            return "ILLEGAL_CART_OPERATION";
        }
        if (ex instanceof PricingException) {
            return "PRICING_ERROR";
        }
        return "INTERNAL_ERROR";
    }

}
