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

    private final BroadleafAuthenticationFailureHandler handler =
            new BroadleafAuthenticationFailureHandler("/login?error=true");

    @Test
    public void rejectsOffSiteUrls() {
        String[] offSite = {
                "//3232235777",
                "//evil\u3002com",
                "//[::1]",
                "//[2001:db8::1]/x",
                "/\\evil",
                "/\\/evil.com",
                "\\\\evil.com",
                "//evil.com",
                "///evil.com",
                "http://evil.com",
                "https:evil.com",
                "javascript:alert(1)",
                "evil",
                "/\t/evil.com",
                "/login\u3000x",
                "/login\u0000",
                "/%2F%2Fevil.com",
                "/%2f3232235777",
                "/%5Cevil.com",
                "/%252F%252Fevil.com",
                "/%09/evil.com",
                "/%2F%2Fevil.com?x=%",
                "/%2F%2Fevil.com?x=%zz",
                "/%25252525252F%25252525252Fevil.com",
                "",
                null
        };
        for (String url : offSite) {
            assertNull("expected rejection of " + url, handler.validateUrlParam(url));
        }
    }

    @Test
    public void acceptsLocalPaths() {
        String[] local = {"/", "/login?error=true", "/account/orders", "/checkout.htm", "/search?q=a%2Fb#top",
                "/search?q=red|blue", "/search?filter[color]=red", "/search?q=100%",
                "/login?reason=a+b"};
        for (String url : local) {
            assertEquals(url, handler.validateUrlParam(url));
        }
    }

    @Test
    public void ignoresProtocolRelativeFailureUrl() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login_post.htm");
        request.setParameter("failureUrl", "//3232235777");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        assertEquals("/login?error=true", response.getRedirectedUrl());
    }

    @Test
    public void keepsLocalFailureUrlAndEncodesSuccessUrl() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login_post.htm");
        request.setParameter("failureUrl", "/login?error=locked");
        request.setParameter("successUrl", "/account?tab=orders&x=1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        assertEquals("/login?error=locked&successUrl=%2Faccount%3Ftab%3Dorders%26x%3D1", response.getRedirectedUrl());
    }

    @Test
    public void keepsLocalFailureUrlWithEncodedPlus() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login_post.htm");
        request.setParameter("failureUrl", "/login?reason=a%2Bb");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        assertEquals("/login?reason=a+b", response.getRedirectedUrl());
    }

    @Test
    public void dropsOffSiteSuccessUrl() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login_post.htm");
        request.setParameter("successUrl", "//evil\u3002com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        assertEquals("/login?error=true", response.getRedirectedUrl());
    }

    @Test
    public void localRedirectStrategyRejectsProtocolRelativeRequestUrl() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login_post.htm");
        request.setParameter("failureUrl", "//3232235777");
        try {
            new LocalRedirectStrategy().sendRedirect(request, new MockHttpServletResponse(), "//3232235777");
            fail("expected MalformedURLException");
        } catch (MalformedURLException expected) {
            // expected
        }
    }

    @Test
    public void localRedirectStrategyKeepsLocalPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login_post.htm");
        request.setContextPath("/store");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new LocalRedirectStrategy().sendRedirect(request, response, "/login?error=true");

        assertEquals("/store/login?error=true", response.getRedirectedUrl());
    }
}
