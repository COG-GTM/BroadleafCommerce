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
import org.broadleafcommerce.core.catalog.domain.Sku;
import org.broadleafcommerce.core.catalog.service.CatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL query resolvers for catalog lookups. Delegates directly to {@link CatalogService}
 * without modifying existing REST controller behavior.
 */
@Controller
public class CatalogQueryResolver {

    protected static final int DEFAULT_LIMIT = 50;
    protected static final int DEFAULT_OFFSET = 0;

    @Autowired
    @Qualifier("blCatalogService")
    protected CatalogService catalogService;

    @QueryMapping
    public Product product(@Argument Long id) {
        return catalogService.findProductById(id);
    }

    @QueryMapping
    public Product productByExternalId(@Argument String externalId) {
        return catalogService.findProductByExternalId(externalId);
    }

    @QueryMapping
    public Product productByUri(@Argument String uri) {
        return catalogService.findProductByURI(uri);
    }

    @QueryMapping
    public List<Product> products(@Argument String name,
                                  @Argument Integer limit,
                                  @Argument Integer offset) {
        int effectiveLimit = limit != null ? limit : DEFAULT_LIMIT;
        int effectiveOffset = offset != null ? offset : DEFAULT_OFFSET;
        return catalogService.findProductsByName(name, effectiveLimit, effectiveOffset);
    }

    @QueryMapping
    public Category category(@Argument Long id) {
        return catalogService.findCategoryById(id);
    }

    @QueryMapping
    public Category categoryByUri(@Argument String uri) {
        return catalogService.findCategoryByURI(uri);
    }

    @QueryMapping
    public List<Category> categories(@Argument String name,
                                     @Argument Integer limit,
                                     @Argument Integer offset) {
        int effectiveLimit = limit != null ? limit : DEFAULT_LIMIT;
        int effectiveOffset = offset != null ? offset : DEFAULT_OFFSET;
        return catalogService.findCategoriesByName(name, effectiveLimit, effectiveOffset);
    }

    @QueryMapping
    public Sku sku(@Argument Long id) {
        return catalogService.findSkuById(id);
    }

    @QueryMapping
    public Sku skuByUpc(@Argument String upc) {
        return catalogService.findSkuByUpc(upc);
    }
}
