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
package org.broadleafcommerce.common.security;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.net.MalformedURLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class BroadleafAuthenticationFailureHandlerTest {

    private static final String DEFAULT_FAILURE_URL = "/login?error=true";

    private final BroadleafAuthenticationFailureHandler handler =
            new BroadleafAuthenticationFailureHandler(DEFAULT_FAILURE_URL);

    @Test
    public void rejectsOffSiteUrls() {
        String[] offSite = {
                "//3232235781",
                "//[::1]",
                "//[2001:db8::1]/login",
                "//intranet-host",
                "/\\3232235781",
                "/\\\\3232235781",
                "\\\\3232235781",
                "///3232235781",
                "//evil\u3002com",
                "http://evil.com",
                "https:evil.com",
                "javascript:alert(1)",
                "login",
                "/\t/3232235781",
                "/login\u0000",
                "/login\u3000x",
                "/%2F%2F3232235781",
                "/%2f3232235781",
                "/%5C3232235781",
                "/%252F%252F3232235781",
                "/%09/3232235781",
                "/%25252525252F%25252525252F3232235781",
                "",
                null
        };
        for (String url : offSite) {
            assertNull("expected rejection of " + url, handler.validateUrlParam(url));
        }
    }

    @Test
    public void acceptsLocalPaths() {
        String[] local = {"/", "/login?error=true", "/account/orders", "/checkout.htm", "/www/help",
                "/search?q=a%2Fb#top", "/search?q=red|blue", "/search?q=100%", "/login?reason=a+b"};
        for (String url : local) {
            assertEquals(url, handler.validateUrlParam(url));
        }
    }

    @Test
    public void ignoresProtocolRelativeFailureUrls() throws Exception {
        for (String failureUrl : new String[] {"//3232235781", "/\\3232235781", "//[::1]", "//intranet-host"}) {
            assertEquals(failureUrl, DEFAULT_FAILURE_URL, redirectFor(failureUrl, null));
        }
    }

    @Test
    public void ignoresEncodedProtocolRelativeFailureUrl() throws Exception {
        assertEquals(DEFAULT_FAILURE_URL, redirectFor("%2F%2F3232235781", null));
    }

    @Test
    public void keepsLocalFailureUrlAndEncodesSuccessUrl() throws Exception {
        assertEquals("/login?error=locked&successUrl=%2Faccount%3Ftab%3Dorders%26x%3D1",
                redirectFor("/login?error=locked", "/account?tab=orders&x=1"));
    }

    @Test
    public void dropsOffSiteSuccessUrl() throws Exception {
        assertEquals(DEFAULT_FAILURE_URL, redirectFor(null, "//3232235781"));
        assertEquals(DEFAULT_FAILURE_URL, redirectFor(null, "/\\3232235781"));
    }

    @Test
    public void localRedirectStrategyRejectsProtocolRelativeUrls() throws Exception {
        for (String url : new String[] {"//3232235781", "/\\3232235781", "//[::1]"}) {
            try {
                new LocalRedirectStrategy().sendRedirect(new MockHttpServletRequest("POST", "/login_post.htm"),
                        new MockHttpServletResponse(), url);
                fail("expected MalformedURLException for " + url);
            } catch (MalformedURLException expected) {
                // expected
            }
        }
    }

    @Test
    public void localRedirectStrategyKeepsLocalPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login_post.htm");
        request.setContextPath("/store");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new LocalRedirectStrategy().sendRedirect(request, response, DEFAULT_FAILURE_URL);

        assertEquals("/store/login?error=true", response.getRedirectedUrl());
    }

    private String redirectFor(String failureUrl, String successUrl) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login_post.htm");
        if (failureUrl != null) {
            request.setParameter("failureUrl", failureUrl);
        }
        if (successUrl != null) {
            request.setParameter("successUrl", successUrl);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));
        return response.getRedirectedUrl();
    }
}
