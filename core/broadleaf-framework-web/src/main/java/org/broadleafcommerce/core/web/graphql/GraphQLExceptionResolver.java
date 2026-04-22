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

/**
 * Maps Broadleaf domain exceptions to GraphQL errors with stable error codes in the
 * {@code extensions} map. Unrecognized exceptions are mapped to {@code INTERNAL_ERROR}.
 */
@Component("blGraphQLExceptionResolver")
public class GraphQLExceptionResolver extends DataFetcherExceptionResolverAdapter {

    public static final String ERROR_CODE_KEY = "errorCode";
    public static final String CLASSIFICATION_KEY = "classification";

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        String errorCode;
        ErrorType errorType = ErrorType.INTERNAL_ERROR;

        if (ex instanceof AddToCartException) {
            errorCode = "ADD_TO_CART_ERROR";
            errorType = ErrorType.BAD_REQUEST;
        } else if (ex instanceof PricingException) {
            errorCode = "PRICING_ERROR";
        } else if (ex instanceof OfferMaxUseExceededException) {
            errorCode = "OFFER_MAX_USE_EXCEEDED";
            errorType = ErrorType.BAD_REQUEST;
        } else if (ex instanceof IllegalCartOperationException) {
            errorCode = "ILLEGAL_CART_OPERATION";
            errorType = ErrorType.BAD_REQUEST;
        } else if (ex instanceof RemoveFromCartException) {
            errorCode = "REMOVE_FROM_CART_ERROR";
            errorType = ErrorType.BAD_REQUEST;
        } else if (ex instanceof UpdateCartException) {
            errorCode = "UPDATE_CART_ERROR";
            errorType = ErrorType.BAD_REQUEST;
        } else {
            errorCode = "INTERNAL_ERROR";
        }

        Map<String, Object> extensions = new HashMap<>();
        extensions.put(ERROR_CODE_KEY, errorCode);
        extensions.put(CLASSIFICATION_KEY, errorType.name());

        boolean safeToExposeMessage = errorType != ErrorType.INTERNAL_ERROR && ex.getMessage() != null;
        String message = safeToExposeMessage ? ex.getMessage() : errorCode;

        return GraphqlErrorBuilder.newError(env)
                .errorType(errorType)
                .message(message)
                .extensions(extensions)
                .build();
    }
}
