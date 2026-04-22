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

import org.broadleafcommerce.core.catalog.domain.Category;
import org.broadleafcommerce.core.catalog.domain.Product;
import org.broadleafcommerce.core.catalog.domain.ProductOption;
import org.broadleafcommerce.core.catalog.domain.Sku;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * Field resolvers for the GraphQL {@code Product} type. Lazy-loads nested associations
 * so the underlying JPA proxies are only initialized when the caller selects the field.
 */
@Controller
public class ProductFieldResolver {

    @SchemaMapping(typeName = "Product", field = "defaultCategory")
    public Category defaultCategory(Product product) {
        return product.getDefaultCategory();
    }

    @SchemaMapping(typeName = "Product", field = "defaultSku")
    public Sku defaultSku(Product product) {
        return product.getDefaultSku();
    }

    @SchemaMapping(typeName = "Product", field = "additionalSkus")
    public List<Sku> additionalSkus(Product product) {
        return product.getAdditionalSkus();
    }

    @SchemaMapping(typeName = "Product", field = "productOptions")
    public List<ProductOption> productOptions(Product product) {
        return product.getProductOptions();
    }
}
