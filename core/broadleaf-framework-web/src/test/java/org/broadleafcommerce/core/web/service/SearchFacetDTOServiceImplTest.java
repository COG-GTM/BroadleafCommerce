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
package org.broadleafcommerce.core.web.service;

import org.broadleafcommerce.core.search.domain.SearchCriteria;
import org.broadleafcommerce.core.web.catalog.CategoryHandlerMapping;
import org.easymock.EasyMock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import junit.framework.TestCase;

public class SearchFacetDTOServiceImplTest extends TestCase {

    public void testQtIsIgnoredWhenNoHandlersAreAllowlisted() {
        SearchCriteria criteria = buildSearchCriteria("", "/update?optimize=true&x=");
        assertNull(criteria.getRequestHandler());
        assertFalse(criteria.getFilterCriteria().containsKey(SearchCriteria.REQUEST_HANDLER));
    }

    public void testQtInjectionPayloadIsRejectedEvenWhenAllowlistIsConfigured() {
        assertNull(buildSearchCriteria("/select,/update", "/update?optimize=true&x=").getRequestHandler());
        assertNull(buildSearchCriteria("/select", "/../admin/cores?action=UNLOAD&x=").getRequestHandler());
        assertNull(buildSearchCriteria("/select", "/replication").getRequestHandler());
    }

    public void testAllowlistedQtIsHonored() {
        assertEquals("/browse", buildSearchCriteria("/select,/browse", "browse").getRequestHandler());
    }

    protected SearchCriteria buildSearchCriteria(final String allowedHandlers, String qt) {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put(SearchCriteria.QUERY_STRING, new String[] { "sauce" });
        params.put(SearchCriteria.REQUEST_HANDLER, new String[] { qt });

        HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.expect(request.getParameterMap()).andReturn(Collections.unmodifiableMap(params)).anyTimes();
        EasyMock.expect(request.getParameter(SearchCriteria.QUERY_STRING)).andReturn(null).anyTimes();
        EasyMock.expect(request.getAttribute(CategoryHandlerMapping.CURRENT_CATEGORY_ATTRIBUTE_NAME))
                .andReturn(null).anyTimes();
        EasyMock.replay(request);

        SearchFacetDTOServiceImpl service = new SearchFacetDTOServiceImpl() {
            @Override
            protected int getDefaultPageSize() {
                return 15;
            }

            @Override
            protected int getMaxPageSize() {
                return 100;
            }

            @Override
            protected String getAllowedRequestHandlers() {
                return allowedHandlers;
            }
        };
        return service.buildSearchCriteria(request);
    }

}
