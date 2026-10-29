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
import org.broadleafcommerce.common.security.service.ExploitProtectionService;
import org.broadleafcommerce.common.security.service.StaleStateProtectionService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import jakarta.servlet.http.HttpServletResponse;

public class SecurityFilterCsrfTokenTest {

    private static final String TOKEN = "session-csrf-token";

    private SecurityFilter securityFilter;
    private CsrfFilter csrfFilter;

    @Before
    public void setUp() {
        securityFilter = new SecurityFilter();
        securityFilter.exploitProtectionService = new FixedTokenExploitProtectionService();
        securityFilter.staleStateProtectionService = new DisabledStaleStateProtectionService();

        csrfFilter = new CsrfFilter();
        csrfFilter.exploitProtectionService = new FixedTokenExploitProtectionService();
    }

    @Test
    public void tokenInRequestBodyIsAccepted() throws Exception {
        MockHttpServletRequest request = post();
        request.addParameter("csrfToken", TOKEN);

        assertPassed(runSecurityFilter(request));
        assertPassed(runCsrfFilter(request));
    }

    @Test
    public void tokenInMultipartBodyIsAccepted() throws Exception {
        MockHttpServletRequest request = post();
        request.setContentType("multipart/form-data; boundary=----blc");
        request.addParameter("csrfToken", TOKEN);

        assertPassed(runSecurityFilter(request));
    }

    @Test
    public void tokenInQueryStringIsRejected() throws Exception {
        MockHttpServletRequest request = post();
        request.setContentType("multipart/form-data; boundary=----blc");
        request.setQueryString("csrfToken=" + TOKEN);
        request.addParameter("csrfToken", TOKEN);

        assertForbidden(runSecurityFilter(request));
        assertForbidden(runCsrfFilter(request));
    }

    @Test
    public void urlEncodedTokenNameInQueryStringIsRejected() throws Exception {
        MockHttpServletRequest request = post();
        request.setQueryString("foo=bar&csrf%54oken=" + TOKEN);
        request.addParameter("csrfToken", TOKEN);

        assertForbidden(runSecurityFilter(request));
    }

    @Test
    public void tokenInQueryStringIsAcceptedWhenExplicitlyAllowed() throws Exception {
        securityFilter.setAllowQueryStringCsrfToken(true);
        MockHttpServletRequest request = post();
        request.setQueryString("csrfToken=" + TOKEN);
        request.addParameter("csrfToken", TOKEN);

        assertPassed(runSecurityFilter(request));
    }

    @Test
    public void tokenInHeaderIsAccepted() throws Exception {
        MockHttpServletRequest request = post();
        request.addHeader(CsrfTokenRequestResolver.CSRF_TOKEN_HEADER, TOKEN);

        assertPassed(runSecurityFilter(request));
    }

    @Test
    public void missingOrWrongTokenIsRejected() throws Exception {
        assertForbidden(runSecurityFilter(post()));

        MockHttpServletRequest wrong = post();
        wrong.addParameter("csrfToken", "forged");
        assertForbidden(runSecurityFilter(wrong));
    }

    @Test
    public void otherQueryParametersDoNotAffectBodyToken() throws Exception {
        MockHttpServletRequest request = post();
        request.setQueryString("sectionKey=product&csrfTokenX=1");
        request.addParameter("csrfToken", TOKEN);

        assertPassed(runSecurityFilter(request));
    }

    @Test
    public void getRequestsAreNotChecked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/product");

        assertPassed(runSecurityFilter(request));
    }

    private MockHttpServletRequest post() {
        return new MockHttpServletRequest("POST", "/admin/asset/uploadAsset");
    }

    private MockFilterChain runSecurityFilter(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        securityFilter.doFilter(request, response, chain);
        lastStatus = response.getStatus();
        return chain;
    }

    private MockFilterChain runCsrfFilter(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        csrfFilter.doFilter(request, response, chain);
        lastStatus = response.getStatus();
        return chain;
    }

    private int lastStatus;

    private void assertPassed(MockFilterChain chain) {
        assertNotNull("request should reach the rest of the filter chain", chain.getRequest());
        assertEquals(HttpServletResponse.SC_OK, lastStatus);
    }

    private void assertForbidden(MockFilterChain chain) {
        assertNull("request must not reach the rest of the filter chain", chain.getRequest());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, lastStatus);
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
        public void compareToken(String passedToken) throws SecurityServiceException {
            if (!TOKEN.equals(passedToken)) {
                throw new SecurityServiceException("Invalid CSRF token");
            }
        }

        @Override
        public String getCSRFToken() {
            return TOKEN;
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
        public boolean sendRedirectOnStateChange(HttpServletResponse response, String... stateChangeParams) {
            return false;
        }
    }

}
