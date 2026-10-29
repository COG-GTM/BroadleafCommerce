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
import org.broadleafcommerce.common.sandbox.dao.SandBoxDao;
import org.broadleafcommerce.common.sandbox.domain.SandBox;
import org.broadleafcommerce.common.sandbox.domain.SandBoxImpl;
import org.broadleafcommerce.common.sandbox.domain.SandBoxType;
import org.broadleafcommerce.common.time.FixedTimeSource;
import org.broadleafcommerce.common.time.SystemTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.lang.reflect.Proxy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BroadleafSandBoxResolverImplTest {

    private static final Long USER_SANDBOX_ID = 2L;

    private BroadleafSandBoxResolverImpl resolver;
    private SandBox userSandBox;
    private int sandBoxLookups;

    @Before
    public void setUp() {
        SystemTime.reset();
        userSandBox = new SandBoxImpl();
        userSandBox.setId(USER_SANDBOX_ID);
        userSandBox.setSandBoxType(SandBoxType.USER);

        SandBoxDao sandBoxDao = (SandBoxDao) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{SandBoxDao.class}, (proxy, method, args) -> {
                    if ("retrieve".equals(method.getName())) {
                        sandBoxLookups++;
                        return USER_SANDBOX_ID.equals(args[0]) ? userSandBox : null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        resolver = new BroadleafSandBoxResolverImpl();
        ReflectionTestUtils.setField(resolver, "sandBoxDao", sandBoxDao);
    }

    @After
    public void tearDown() {
        SystemTime.reset();
    }

    @Test
    public void anonymousPreviewIsIgnoredWithoutCrossAppAuthService() {
        ServletWebRequest request = previewRequest();

        SandBox resolved = resolver.resolveSandBox(request, null);

        assertNull(resolved);
        assertNull(request.getAttribute(BroadleafSandBoxResolverImpl.SANDBOX_VAR, WebRequest.SCOPE_REQUEST));
        assertNull(request.getAttribute(BroadleafSandBoxResolver.SANDBOX_ID_VAR, WebRequest.SCOPE_SESSION));
        assertNull(request.getAttribute("blSandboxDateTime", WebRequest.SCOPE_SESSION));
        assertFalse(SystemTime.getTimeSource() instanceof FixedTimeSource);
        assertEquals(0, sandBoxLookups);
    }

    @Test
    public void previewIsIgnoredWhenSessionIsNotAuthedFromAdmin() {
        resolver.crossAppAuthService = crossAppAuth(false, false);
        ServletWebRequest request = previewRequest();

        assertNull(resolver.resolveSandBox(request, null));
        assertFalse(SystemTime.getTimeSource() instanceof FixedTimeSource);
        assertEquals(0, sandBoxLookups);
    }

    @Test
    public void previewIsIgnoredInCsrMode() {
        resolver.crossAppAuthService = crossAppAuth(true, true);
        ServletWebRequest request = previewRequest();

        assertNull(resolver.resolveSandBox(request, null));
        assertFalse(SystemTime.getTimeSource() instanceof FixedTimeSource);
    }

    @Test
    public void staleSessionSandBoxIsIgnoredWithoutCrossAppAuthService() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/");
        servletRequest.getSession().setAttribute(BroadleafSandBoxResolver.SANDBOX_ID_VAR, USER_SANDBOX_ID);

        assertNull(resolver.resolveSandBox(new ServletWebRequest(servletRequest), null));
        assertFalse(SystemTime.getTimeSource() instanceof FixedTimeSource);
        assertEquals(0, sandBoxLookups);
    }

    @Test
    public void adminAuthenticatedSessionCanPreviewAndOverrideTime() {
        resolver.crossAppAuthService = crossAppAuth(true, false);
        ServletWebRequest request = previewRequest();

        SandBox resolved = resolver.resolveSandBox(request, null);

        assertSame(userSandBox, resolved);
        assertEquals(USER_SANDBOX_ID, request.getAttribute(BroadleafSandBoxResolver.SANDBOX_ID_VAR, WebRequest.SCOPE_SESSION));
        assertTrue(SystemTime.getTimeSource() instanceof FixedTimeSource);
    }

    private ServletWebRequest previewRequest() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/");
        servletRequest.addParameter(BroadleafSandBoxResolver.SANDBOX_ID_VAR, USER_SANDBOX_ID.toString());
        servletRequest.addParameter("blSandboxDateTimeRibbonOverride", "1");
        servletRequest.addParameter("blSandboxDisplayDateTimeDate", "11/28/2025");
        servletRequest.addParameter("blSandboxDisplayDateTimeHours", "9");
        servletRequest.addParameter("blSandboxDisplayDateTimeMinutes", "00");
        servletRequest.addParameter("blSandboxDisplayDateTimeAMPM", "AM");
        return new ServletWebRequest(servletRequest);
    }

    private CrossAppAuthService crossAppAuth(final boolean authedFromAdmin, final boolean csr) {
        return new CrossAppAuthService() {
            @Override
            public void useSiteAuthToken(Long adminUserId, String token, HttpServletRequest request,
                                         HttpServletResponse response) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isAuthedFromAdmin() {
                return authedFromAdmin;
            }

            @Override
            public Long getCurrentAuthedAdminId() {
                return authedFromAdmin ? 1L : null;
            }

            @Override
            public boolean hasCsrPermission() {
                return csr;
            }

            @Override
            public boolean hasQuotePermission() {
                return false;
            }
        };
    }

}
