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

import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

public class PreviewTemplateControllerTest {

    private final PreviewTemplateController controller = new PreviewTemplateController();

    @Test
    public void resolvesTemplateUnderTemplatesFolder() {
        Assert.assertEquals("templates/content/home", controller.displayPreview(request("/admin", "/admin/preview/content/home")));
        Assert.assertEquals("templates/layout/full_page.v2", controller.displayPreview(request("", "/preview/layout/full_page.v2")));
    }

    @Test
    public void rejectsTraversalAndAmbiguousPaths() {
        String[] uris = {
                "/admin/preview/../../x",
                "/admin/preview/____/____/x",
                "/admin/preview/content/../../x",
                "/admin/preview/./x",
                "/admin/preview/content//x",
                "/admin/preview/x;jsessionid=1",
                "/admin/preview/%2e%2e/x",
                "/admin/preview/..%2fx",
                "/admin/preview/content\\..\\x",
                "/admin/preview/",
                "/admin/preview/x/",
                "/admin/preview/.x",
                "/admin/other/x"
        };
        for (String uri : uris) {
            assertNotFound(uri);
        }
    }

    private void assertNotFound(String uri) {
        try {
            String view = controller.displayPreview(request("/admin", uri));
            Assert.fail("Expected 404 for " + uri + " but got view " + view);
        } catch (ResponseStatusException e) {
            Assert.assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }
    }

    private MockHttpServletRequest request(String contextPath, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setContextPath(contextPath);
        return request;
    }

}
