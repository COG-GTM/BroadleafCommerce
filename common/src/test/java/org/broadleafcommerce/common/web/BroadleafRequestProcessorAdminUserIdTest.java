/*-
 * #%L
 * BroadleafCommerce Common Libraries
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
package org.broadleafcommerce.common.web;

import org.broadleafcommerce.common.crossapp.service.CrossAppAuthService;
import org.broadleafcommerce.common.util.DeployBehaviorUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Proxy;
import java.util.HashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class BroadleafRequestProcessorAdminUserIdTest {

    private BroadleafRequestProcessor processor;

    @Before
    public void setUp() {
        processor = new BroadleafRequestProcessor();
        processor.siteResolver = nullReturning(BroadleafSiteResolver.class);
        processor.localeResolver = nullReturning(BroadleafLocaleResolver.class);
        processor.currencyResolver = nullReturning(BroadleafCurrencyResolver.class);
        processor.sandboxResolver = nullReturning(BroadleafSandBoxResolver.class);
        processor.themeResolver = nullReturning(BroadleafThemeResolver.class);
        processor.broadleafTimeZoneResolver = nullReturning(BroadleafTimeZoneResolver.class);
        processor.deployBehaviorUtil = new DeployBehaviorUtil();
        processor.entityExtensionManagers = new HashMap<>();
    }

    @After
    public void tearDown() {
        BroadleafRequestContext.setBroadleafRequestContext(null);
    }

    @Test
    public void adminUserIdParameterIsIgnoredWithoutCrossAppAuth() {
        processor.process(requestWithAdminUserIdParam());

        Assert.assertNull(BroadleafRequestContext.getBroadleafRequestContext().getAdminUserId());
    }

    @Test
    public void adminUserIdParameterIsIgnoredWhenNotAuthedFromAdmin() {
        processor.crossAppAuthService = new StubCrossAppAuthService(false, 7L);

        processor.process(requestWithAdminUserIdParam());

        Assert.assertNull(BroadleafRequestContext.getBroadleafRequestContext().getAdminUserId());
    }

    @Test
    public void adminUserIdComesFromAuthenticatedCrossAppSession() {
        processor.crossAppAuthService = new StubCrossAppAuthService(true, 7L);

        processor.process(requestWithAdminUserIdParam());

        Assert.assertEquals(Long.valueOf(7L), BroadleafRequestContext.getBroadleafRequestContext().getAdminUserId());
    }

    private ServletWebRequest requestWithAdminUserIdParam() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setParameter("blAdminUserId", "1");
        return new ServletWebRequest(request, new MockHttpServletResponse());
    }

    private static <T> T nullReturning(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (proxy, method, args) -> null));
    }

    private static class StubCrossAppAuthService implements CrossAppAuthService {

        private final boolean authedFromAdmin;
        private final Long adminId;

        StubCrossAppAuthService(boolean authedFromAdmin, Long adminId) {
            this.authedFromAdmin = authedFromAdmin;
            this.adminId = adminId;
        }

        @Override
        public void useSiteAuthToken(Long adminUserId, String token, HttpServletRequest request,
                HttpServletResponse response) {
        }

        @Override
        public boolean isAuthedFromAdmin() {
            return authedFromAdmin;
        }

        @Override
        public Long getCurrentAuthedAdminId() {
            return authedFromAdmin ? adminId : null;
        }

        @Override
        public boolean hasCsrPermission() {
            return false;
        }

        @Override
        public boolean hasQuotePermission() {
            return false;
        }
    }

}
