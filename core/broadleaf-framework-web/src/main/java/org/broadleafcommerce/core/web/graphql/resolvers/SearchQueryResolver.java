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

import org.broadleafcommerce.core.catalog.domain.Product;
import org.broadleafcommerce.core.search.domain.SearchCriteria;
import org.broadleafcommerce.core.search.domain.SearchResult;
import org.broadleafcommerce.core.search.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.Collections;
import java.util.List;

/**
 * GraphQL resolver for product search. Constructs a {@link SearchCriteria} from the
 * query/limit/offset arguments and delegates to {@link SearchService#findSearchResults}.
 */
@Controller
public class SearchQueryResolver {

    protected static final int DEFAULT_LIMIT = 50;
    protected static final int DEFAULT_OFFSET = 0;

    @Autowired
    @Qualifier("blSearchService")
    protected SearchService searchService;

    @QueryMapping
    public List<Product> search(@Argument String query,
                                @Argument Integer limit,
                                @Argument Integer offset) throws Exception {
        int effectiveLimit = limit != null ? limit : DEFAULT_LIMIT;
        int effectiveOffset = offset != null ? offset : DEFAULT_OFFSET;

        SearchCriteria criteria = new SearchCriteria();
        criteria.setQuery(query);
        criteria.setPageSize(effectiveLimit);
        criteria.setStartIndex(effectiveOffset);
        int page = effectiveLimit > 0 ? (effectiveOffset / effectiveLimit) + 1 : 1;
        criteria.setPage(page);

        SearchResult result = searchService.findSearchResults(criteria);
        if (result == null || result.getProducts() == null) {
            return Collections.emptyList();
        }
        return result.getProducts();
    }
}
