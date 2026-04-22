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
import org.broadleafcommerce.core.catalog.service.CatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * Field resolvers for the GraphQL {@code Category} type. Each method receives the parent
 * {@link Category} as the first argument and lazily loads child collections via
 * {@link CatalogService} so they are only fetched when requested.
 */
@Controller
public class CategoryFieldResolver {

    protected static final int DEFAULT_LIMIT = 50;
    protected static final int DEFAULT_OFFSET = 0;

    @Autowired
    @Qualifier("blCatalogService")
    protected CatalogService catalogService;

    @SchemaMapping(typeName = "Category", field = "activeSubCategories")
    public List<Category> activeSubCategories(Category category,
                                              @Argument Integer limit,
                                              @Argument Integer offset) {
        int effectiveLimit = limit != null ? limit : DEFAULT_LIMIT;
        int effectiveOffset = offset != null ? offset : DEFAULT_OFFSET;
        return catalogService.findActiveSubCategoriesByCategory(category, effectiveLimit, effectiveOffset);
    }

    @SchemaMapping(typeName = "Category", field = "products")
    public List<Product> products(Category category,
                                  @Argument Integer limit,
                                  @Argument Integer offset) {
        int effectiveLimit = limit != null ? limit : DEFAULT_LIMIT;
        int effectiveOffset = offset != null ? offset : DEFAULT_OFFSET;
        return catalogService.findActiveProductsByCategory(category, effectiveLimit, effectiveOffset);
    }
}
