/*-
 * #%L
 * BroadleafCommerce Framework
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
package org.broadleafcommerce.core.search.service.solr;

import org.broadleafcommerce.core.search.domain.SearchCriteria;

import java.util.Arrays;
import java.util.Set;

import junit.framework.TestCase;

public class SolrRequestHandlerValidatorTest extends TestCase {

    private static final String[] MALICIOUS_HANDLERS = {
            "/update?commit=true&x=",
            "/update?optimize=true&x=",
            "/select?q=*:*&rows=100000&x=",
            "/../admin/cores?action=UNLOAD&core=catalog&deleteIndex=true",
            "/../admin/collections?action=DELETE&name=catalog",
            "/admin/ping?action=disable",
            "/stream?expr=delete(catalog)",
            "update#frag",
            "/select ",
            "/select\n",
            "/sel ect",
            "/select/../update",
            "/select%3Fcommit=true",
            "//update",
            "/",
            "",
    };

    public void testNothingAllowedByDefault() {
        assertNull(SolrRequestHandlerValidator.resolve("/select", (String) null));
        assertNull(SolrRequestHandlerValidator.resolve("select", ""));
        assertNull(SolrRequestHandlerValidator.resolve("/update", " "));
    }

    public void testAllowlistedHandlerIsNormalized() {
        String allowed = "select, /browse";
        assertEquals("/select", SolrRequestHandlerValidator.resolve("select", allowed));
        assertEquals("/select", SolrRequestHandlerValidator.resolve("/select", allowed));
        assertEquals("/browse", SolrRequestHandlerValidator.resolve("browse", allowed));
        assertNull(SolrRequestHandlerValidator.resolve("/update", allowed));
        assertNull(SolrRequestHandlerValidator.resolve(null, allowed));
    }

    public void testMaliciousHandlersAreRejectedEvenWhenPrefixAllowlisted() {
        String allowed = "/select,/update,/admin,/stream";
        for (String handler : MALICIOUS_HANDLERS) {
            assertNull(handler, SolrRequestHandlerValidator.resolve(handler, allowed));
        }
    }

    public void testMalformedAllowlistEntriesAreIgnored() {
        Set<String> allowed = SolrRequestHandlerValidator.parseAllowedHandlers(
                "/update?commit=true,/../admin/cores,select");
        assertEquals(1, allowed.size());
        assertTrue(allowed.contains("/select"));
        assertNull(SolrRequestHandlerValidator.resolve("/update?commit=true", allowed));
    }

    public void testSolrSearchServiceOnlyUsesAllowlistedHandler() {
        SolrSearchServiceImpl service = new SolrSearchServiceImpl();
        SearchCriteria criteria = new SearchCriteria();

        for (String handler : Arrays.asList(MALICIOUS_HANDLERS)) {
            criteria.setRequestHandler(handler);
            assertNull(handler, service.getRequestHandler(criteria));
        }

        criteria.setRequestHandler("/select");
        assertNull(service.getRequestHandler(criteria));

        service.allowedRequestHandlers = "/select";
        assertEquals("/select", service.getRequestHandler(criteria));
        criteria.setRequestHandler("/update?commit=true&x=");
        assertNull(service.getRequestHandler(criteria));
    }

}
