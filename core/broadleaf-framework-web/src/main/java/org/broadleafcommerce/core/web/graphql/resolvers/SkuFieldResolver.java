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
package org.broadleafcommerce.core.web.graphql.resolvers;

import org.broadleafcommerce.common.money.Money;
import org.broadleafcommerce.core.catalog.domain.Sku;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * Field resolvers for the GraphQL {@code Sku} type. Maps Broadleaf {@link Money} values to
 * the shared {@code Money} GraphQL type so callers can select {@code amount} and
 * {@code currency} independently.
 */
@Controller
public class SkuFieldResolver {

    @SchemaMapping(typeName = "Sku", field = "salePrice")
    public Money salePrice(Sku sku) {
        return sku.getSalePrice();
    }

    @SchemaMapping(typeName = "Sku", field = "retailPrice")
    public Money retailPrice(Sku sku) {
        return sku.getRetailPrice();
    }

    @SchemaMapping(typeName = "Sku", field = "cost")
    public Money cost(Sku sku) {
        return sku.getCost();
    }

    @SchemaMapping(typeName = "Money", field = "currency")
    public String currency(Money money) {
        return money.getCurrency() != null ? money.getCurrency().getCurrencyCode() : null;
    }
}
