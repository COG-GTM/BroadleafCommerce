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

import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.broadleafcommerce.core.checkout.service.exception.CheckoutException;
import org.broadleafcommerce.core.offer.service.exception.OfferMaxUseExceededException;
import org.broadleafcommerce.core.order.service.exception.AddToCartException;
import org.broadleafcommerce.core.order.service.exception.IllegalCartOperationException;
import org.broadleafcommerce.core.order.service.exception.RemoveFromCartException;
import org.broadleafcommerce.core.order.service.exception.UpdateCartException;
import org.broadleafcommerce.core.pricing.service.exception.PricingException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Maps Broadleaf domain exceptions to structured GraphQL errors with stable
 * {@code code} extensions so clients can branch on a known error taxonomy.
 */
@Component
public class GraphQLExceptionResolver extends DataFetcherExceptionResolverAdapter {

    public static final String CODE_ADD_TO_CART = "ADD_TO_CART_ERROR";
    public static final String CODE_PRICING = "PRICING_ERROR";
    public static final String CODE_OFFER_MAX_USE_EXCEEDED = "OFFER_MAX_USE_EXCEEDED";
    public static final String CODE_ILLEGAL_CART_OPERATION = "ILLEGAL_CART_OPERATION";
    public static final String CODE_CHECKOUT = "CHECKOUT_ERROR";
    public static final String CODE_REMOVE_FROM_CART = "REMOVE_FROM_CART_ERROR";
    public static final String CODE_UPDATE_CART = "UPDATE_CART_ERROR";

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof AddToCartException) {
            return buildError(ex, env, ErrorType.ValidationError, CODE_ADD_TO_CART);
        }
        if (ex instanceof RemoveFromCartException) {
            return buildError(ex, env, ErrorType.ValidationError, CODE_REMOVE_FROM_CART);
        }
        if (ex instanceof UpdateCartException) {
            return buildError(ex, env, ErrorType.ValidationError, CODE_UPDATE_CART);
        }
        if (ex instanceof IllegalCartOperationException) {
            return buildError(ex, env, ErrorType.ValidationError, CODE_ILLEGAL_CART_OPERATION);
        }
        if (ex instanceof OfferMaxUseExceededException) {
            return buildError(ex, env, ErrorType.ValidationError, CODE_OFFER_MAX_USE_EXCEEDED);
        }
        if (ex instanceof CheckoutException) {
            return buildError(ex, env, ErrorType.ValidationError, CODE_CHECKOUT);
        }
        if (ex instanceof PricingException) {
            return buildError(ex, env, ErrorType.DataFetchingException, CODE_PRICING);
        }
        return null;
    }

    protected GraphQLError buildError(
            Throwable ex,
            DataFetchingEnvironment env,
            ErrorClassification classification,
            String code
    ) {
        return GraphqlErrorBuilder.newError(env)
                .message(ex.getMessage())
                .errorType(classification)
                .extensions(Map.of("code", code))
                .build();
    }

}
