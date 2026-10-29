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
import java.util.LinkedHashSet;

import junit.framework.TestCase;

public class SolrRequestHandlerPolicyTest extends TestCase {

    public void testNothingIsAllowedByDefault() {
        assertNull(SolrRequestHandlerPolicy.resolve("/select", ""));
        assertNull(SolrRequestHandlerPolicy.resolve("browse", (String) null));
        assertTrue(SolrRequestHandlerPolicy.parseAllowedHandlers("  ").isEmpty());
    }

    public void testAllowlistedHandlerIsNormalized() {
        assertEquals("/browse", SolrRequestHandlerPolicy.resolve("browse", "/select, browse"));
        assertEquals("/select", SolrRequestHandlerPolicy.resolve("/select", "/select,/browse"));
    }

    public void testHandlerNotInAllowlistIsRejected() {
        assertNull(SolrRequestHandlerPolicy.resolve("/update", "/select,/browse"));
        assertNull(SolrRequestHandlerPolicy.resolve("/admin/cores", "/select"));
    }

    public void testUrlInjectionPayloadsAreRejected() {
        String allowed = "/select,/update";
        for (String payload : Arrays.asList(
                "/update?optimize=true&x=",
                "/select?rows=100000&fl=*&x=",
                "/../admin/cores?action=UNLOAD&core=catalog&deleteIndex=true&x=",
                "/select/../update",
                "/select&commit=true",
                "/select#x",
                "/select%3Fx",
                "//select",
                "/",
                "")) {
            assertNull(payload, SolrRequestHandlerPolicy.resolve(payload, allowed));
        }
    }

    public void testMalformedAllowlistEntriesAreDropped() {
        assertEquals(new LinkedHashSet<>(Arrays.asList("/select", "/browse")),
                SolrRequestHandlerPolicy.parseAllowedHandlers("/select,/update?commit=true,../admin, browse ,"));
    }

    public void testSearchServiceIgnoresHandlerThatIsNotAllowlisted() {
        final String[] allowed = { "" };
        SolrSearchServiceImpl service = new SolrSearchServiceImpl() {
            @Override
            protected String getAllowedRequestHandlers() {
                return allowed[0];
            }
        };
        SearchCriteria criteria = new SearchCriteria();

        criteria.setRequestHandler("/update?optimize=true&x=");
        assertNull(service.getRequestHandler(criteria));

        criteria.setRequestHandler("/browse");
        assertNull(service.getRequestHandler(criteria));

        allowed[0] = "/browse";
        assertEquals("/browse", service.getRequestHandler(criteria));

        criteria.setRequestHandler(null);
        assertNull(service.getRequestHandler(criteria));
    }

}
