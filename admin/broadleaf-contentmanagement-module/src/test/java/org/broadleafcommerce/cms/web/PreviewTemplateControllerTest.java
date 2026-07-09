/*-
 * #%L
 * BroadleafCommerce CMS Module
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
package org.broadleafcommerce.cms.web;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import junit.framework.TestCase;

public class PreviewTemplateControllerTest extends TestCase {

    private final PreviewTemplateController controller = new PreviewTemplateController();

    private MockHttpServletRequest buildRequest(String requestURI) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestURI);
        request.setContextPath("");
        return request;
    }

    public void testValidTemplatePath() {
        assertEquals("templates/homepage", controller.displayPreview(buildRequest("/preview/homepage")));
        assertEquals("templates/pages/about_us", controller.displayPreview(buildRequest("/preview/pages/about_us")));
        assertEquals("templates/pages/some-page_1", controller.displayPreview(buildRequest("/preview/pages/some-page_1")));
    }

    public void testRejectsTemplateInjectionPayloads() {
        assertRejected("/preview/__${T(java.lang.Runtime).getRuntime()}__::x");
        assertRejected("/preview/foo::bar");
        assertRejected("/preview/${expr}");
        assertRejected("/preview/foo.html");
    }

    public void testRejectsPathTraversal() {
        assertRejected("/preview/../WEB-INF/web");
        assertRejected("/preview/..");
        assertRejected("/preview/");
        assertRejected("/preview/foo//bar");
    }

    private void assertRejected(String requestURI) {
        try {
            controller.displayPreview(buildRequest(requestURI));
            fail("Expected ResponseStatusException for URI: " + requestURI);
        } catch (ResponseStatusException e) {
            // expected
        }
    }
}
