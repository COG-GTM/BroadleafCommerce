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
package org.broadleafcommerce.common.security.handler;

import org.broadleafcommerce.common.exception.SecurityServiceException;
import org.broadleafcommerce.common.exception.ServiceException;
import org.broadleafcommerce.common.security.service.ExploitProtectionService;
import org.broadleafcommerce.common.security.service.StaleStateProtectionService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SecurityFilterCsrfTokenTest {

    private static final String SESSION_TOKEN = "session-csrf-token";

    private SecurityFilter filter;

    @Before
    public void setUp() {
        filter = new SecurityFilter();
        filter.exploitProtectionService = new FixedTokenExploitProtectionService();
        filter.staleStateProtectionService = new DisabledStaleStateProtectionService();
    }

    @Test
    public void acceptsTokenInRequestBody() throws Exception {
        MockHttpServletRequest request = post();
        request.addParameter("csrfToken", SESSION_TOKEN);

        assertPassed(request);
    }

    @Test
    public void acceptsTokenInHeader() throws Exception {
        MockHttpServletRequest request = post();
        request.addHeader(CsrfTokenRequestResolver.CSRF_TOKEN_HEADER, SESSION_TOKEN);

        assertPassed(request);
    }

    @Test
    public void rejectsMissingToken() throws Exception {
        assertForbidden(post());
    }

    @Test
    public void rejectsWrongToken() throws Exception {
        MockHttpServletRequest request = post();
        request.addParameter("csrfToken", "not-the-token");

        assertForbidden(request);
    }

    @Test
    public void rejectsTokenInQueryString() throws Exception {
        MockHttpServletRequest request = post();
        request.setQueryString("csrfToken=" + SESSION_TOKEN);
        request.addParameter("csrfToken", SESSION_TOKEN);

        assertForbidden(request);
    }

    @Test
    public void acceptsTokenInQueryStringWhenExplicitlyAllowed() throws Exception {
        filter.setAllowQueryStringCsrfToken(true);
        MockHttpServletRequest request = post();
        request.setQueryString("csrfToken=" + SESSION_TOKEN);
        request.addParameter("csrfToken", SESSION_TOKEN);

        assertPassed(request);
    }

    @Test
    public void detectsUrlEncodedParameterNameInQueryString() {
        MockHttpServletRequest request = post();
        request.setQueryString("a=1&csrf%54oken=x");

        assertTrue(CsrfTokenRequestResolver.isInQueryString(request, "csrfToken"));
        assertFalse(CsrfTokenRequestResolver.isInQueryString(request, "stateVersionToken"));
        assertNull(CsrfTokenRequestResolver.resolveToken(request, "csrfToken", false));
    }

    @Test
    public void getRequestsAreNotChecked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/asset");

        assertPassed(request);
    }

    private MockHttpServletRequest post() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/asset/uploadAsset");
        request.setContentType("multipart/form-data; boundary=x");
        return request;
    }

    private void assertPassed(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    private void assertForbidden(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    private static class FixedTokenExploitProtectionService implements ExploitProtectionService {

        @Override
        public String cleanString(String string) {
            return string;
        }

        @Override
        public String cleanStringWithResults(String string) {
            return string;
        }

        @Override
        public void compareToken(String passedToken) throws ServiceException {
            if (!SESSION_TOKEN.equals(passedToken)) {
                throw new SecurityServiceException("XSRF token mismatch");
            }
        }

        @Override
        public String getCSRFToken() {
            return SESSION_TOKEN;
        }

        @Override
        public String getCsrfTokenParameter() {
            return "csrfToken";
        }

        @Override
        public String htmlDecode(String value) {
            return value;
        }
    }

    private static class DisabledStaleStateProtectionService implements StaleStateProtectionService {

        @Override
        public void compareToken(String passedToken) {
        }

        @Override
        public String getStateVersionToken() {
            return null;
        }

        @Override
        public void invalidateState() {
        }

        @Override
        public void invalidateState(boolean notify) {
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public String getStateVersionTokenParameter() {
            return "stateVersionToken";
        }

        @Override
        public boolean sendRedirectOnStateChange(HttpServletResponse response, String... names) {
            return false;
        }
    }

}
