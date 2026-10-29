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
import org.easymock.EasyMock;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import junit.framework.TestCase;

public class SearchFacetDTOServiceImplTest extends TestCase {

    private SearchFacetDTOServiceImpl service(final String allowedHandlers) {
        return new SearchFacetDTOServiceImpl() {
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
    }

    private HttpServletRequest request(String qt) {
        Map<String, String[]> params = new HashMap<>();
        params.put(SearchCriteria.REQUEST_HANDLER, new String[] { qt });
        params.put("color", new String[] { "red" });
        HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.expect(request.getParameterMap()).andReturn(params).anyTimes();
        EasyMock.replay(request);
        return request;
    }

    public void testQtIgnoredWhenNoHandlersAllowed() {
        String[] inputs = {
                "/update?commit=true&x=",
                "update?optimize=true&x=",
                "/../admin/cores?action=UNLOAD&core=catalog&deleteIndex=true",
                "/select",
                "select",
        };
        for (String qt : inputs) {
            SearchCriteria criteria = service(null).buildSearchCriteria(request(qt));
            assertNull(qt, criteria.getRequestHandler());
            assertFalse(criteria.getFilterCriteria().containsKey(SearchCriteria.REQUEST_HANDLER));
            assertTrue(criteria.getFilterCriteria().containsKey("color"));
        }
    }

    public void testOnlyAllowlistedQtIsAccepted() {
        SearchFacetDTOServiceImpl service = service("/select");
        assertEquals("/select", service.buildSearchCriteria(request("select")).getRequestHandler());
        assertEquals("/select", service.buildSearchCriteria(request("/select")).getRequestHandler());
        assertNull(service.buildSearchCriteria(request("/select?q=*:*&rows=100000")).getRequestHandler());
        assertNull(service.buildSearchCriteria(request("/update?commit=true&x=")).getRequestHandler());
        assertNull(service.buildSearchCriteria(request("/select/../update")).getRequestHandler());
    }

}
