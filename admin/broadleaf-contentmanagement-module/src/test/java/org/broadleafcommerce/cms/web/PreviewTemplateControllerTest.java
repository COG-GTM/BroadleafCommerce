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

import org.broadleafcommerce.common.crossapp.service.CrossAppAuthService;
import org.broadleafcommerce.common.sandbox.domain.SandBox;
import org.broadleafcommerce.common.sandbox.domain.SandBoxType;
import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Proxy;

public class PreviewTemplateControllerTest {

    private final PreviewTemplateController controller = new PreviewTemplateController();

    @Before
    public void authenticateFromAdmin() {
        controller.crossAppAuthService = crossAppAuth(true, false);
    }

    @After
    public void clearContext() {
        BroadleafRequestContext.setBroadleafRequestContext(null);
    }

    @Test
    public void anonymousRequestWithoutContextIsNotFound() {
        assertNotFound("/preview/account/orders");
    }

    @Test
    public void productionSandBoxIsNotFound() {
        bindSandBox(null);
        assertNotFound("/preview/account/orders");

        bindSandBox(SandBoxType.PRODUCTION);
        assertNotFound("/preview/checkout/partials/paymentInfo");
    }

    @Test
    public void previewSandBoxRendersTemplate() {
        bindSandBox(SandBoxType.USER);
        Assert.assertEquals("templates/account/orders", controller.displayPreview(request("", "/preview/account/orders")));
        Assert.assertEquals("templates/catalog/product_detail-v2",
                controller.displayPreview(request("/store", "/store/preview/catalog/product_detail-v2")));
    }

    @Test
    public void unsafeTemplatePathsAreNotFoundInPreviewSandBox() {
        bindSandBox(SandBoxType.APPROVAL);
        String[] paths = {
                "/preview/",
                "/preview//account",
                "/preview/account/",
                "/preview/../WEB-INF/web",
                "/preview/account/..%2F..%2Fsecret",
                "/preview/layout/fullPageLayout::body",
                "/preview/~{layout}",
                "/preview/__${T(java.lang.Runtime)}__",
                "/preview/account/orders.html",
                "/preview/account orders"
        };
        for (String path : paths) {
            assertNotFound(path);
        }
    }

    @Test
    public void previewSandBoxWithoutAdminAuthenticationIsNotFound() {
        bindSandBox(SandBoxType.USER);

        controller.crossAppAuthService = null;
        assertNotFound("/preview/account/orders");

        controller.crossAppAuthService = crossAppAuth(false, false);
        assertNotFound("/preview/account/orders");

        controller.crossAppAuthService = crossAppAuth(true, true);
        assertNotFound("/preview/account/orders");
    }

    @Test
    public void requestOutsideMappingIsNotFound() {
        bindSandBox(SandBoxType.USER);
        assertNotFound(request("/store", "/other/preview/account"));
    }

    private void bindSandBox(SandBoxType type) {
        BroadleafRequestContext context = new BroadleafRequestContext();
        if (type != null) {
            SandBox sandBox = (SandBox) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {SandBox.class},
                    (proxy, method, args) -> "getSandBoxType".equals(method.getName()) ? type : null);
            context.setSandBox(sandBox);
        }
        BroadleafRequestContext.setBroadleafRequestContext(context);
    }

    private CrossAppAuthService crossAppAuth(boolean authedFromAdmin, boolean csr) {
        return (CrossAppAuthService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {CrossAppAuthService.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isAuthedFromAdmin":
                            return authedFromAdmin;
                        case "hasCsrPermission":
                            return csr;
                        default:
                            return null;
                    }
                });
    }

    private MockHttpServletRequest request(String contextPath, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setContextPath(contextPath);
        return request;
    }

    private void assertNotFound(String uri) {
        assertNotFound(request("", uri));
    }

    private void assertNotFound(MockHttpServletRequest request) {
        try {
            String view = controller.displayPreview(request);
            Assert.fail("Expected 404 for " + request.getRequestURI() + " but rendered " + view);
        } catch (ResponseStatusException e) {
            Assert.assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }
    }
}
