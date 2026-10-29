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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.MalformedURLException;

public class LocalRedirectStrategyTest {

    private static final String[] UNSAFE_URLS = {
            "//evil.example",
            "//evil.example/login",
            "/\\evil.example",
            "\\\\evil.example",
            "\\/evil.example",
            "/\t/evil.example",
            "/\n/evil.example",
            "/%2fevil.example",
            "/%2Fevil.example",
            "/%5cevil.example",
            "/%252fevil.example",
            "/%09/evil.example",
            "/account\\..\\\\evil.example",
            "/%zz",
            "/%2e%2e//evil.example",
            "/%2E%2E/%2f/evil.example",
            "/./%2fevil.example",
            "/..//evil.example",
            "/account/%252e%252e//evil.example",
            "/search?q=a\r\nSet-Cookie:x=1",
            "/search q?x=1",
            "evil.example",
            "https://evil.example",
            "javascript:alert(1)",
            ""
    };

    @Test
    public void localPathsAreAccepted() {
        assertTrue(LocalRedirectStrategy.isLocalPath("/"));
        assertTrue(LocalRedirectStrategy.isLocalPath("/account"));
        assertTrue(LocalRedirectStrategy.isLocalPath("/account/orders?page=2#top"));
        assertTrue(LocalRedirectStrategy.isLocalPath("/search?q=red%20shoes&redirect=//x"));
        assertTrue(LocalRedirectStrategy.isLocalPath("/my%20page"));
        assertTrue(LocalRedirectStrategy.isLocalPath("/search?q=red shoes"));
        assertTrue(LocalRedirectStrategy.isLocalPath("/search?q=red shoes#results"));
        assertTrue(LocalRedirectStrategy.isLocalPath("/products/v1.2/item..name"));
    }

    @Test
    public void unsafeUrlsAreNotLocalPaths() {
        assertFalse(LocalRedirectStrategy.isLocalPath(null));
        for (String url : UNSAFE_URLS) {
            assertFalse(url, LocalRedirectStrategy.isLocalPath(url));
        }
    }

    @Test
    public void protocolRelativeSuccessUrlIsRejected() throws Exception {
        for (String url : new String[] {"//evil.example", "/\\evil.example", "\\\\evil.example", "/%2fevil.example"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login_post.htm");
            request.setParameter("successUrl", url);
            MockHttpServletResponse response = new MockHttpServletResponse();
            try {
                new LocalRedirectStrategy().sendRedirect(request, response, url);
                fail("Expected redirect to be rejected: " + url);
            } catch (MalformedURLException expected) {
                assertNull(response.getRedirectedUrl());
            }
        }
    }

    @Test
    public void protocolRelativeUrlIsRejectedEvenWhenNotARequestParameter() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            new LocalRedirectStrategy().sendRedirect(new MockHttpServletRequest(), response, "//evil.example");
            fail("Expected redirect to be rejected");
        } catch (MalformedURLException expected) {
            assertNull(response.getRedirectedUrl());
        }
    }

    @Test
    public void localSuccessUrlIsRedirectedWithContextPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/store/login_post.htm");
        request.setContextPath("/store");
        request.setParameter("successUrl", "/account");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new LocalRedirectStrategy().sendRedirect(request, response, "/account");

        assertEquals("/store/account", response.getRedirectedUrl());
    }

    @Test
    public void sameHostAbsoluteSuccessUrlIsStillAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login_post.htm");
        request.setServerName("acme.example");
        request.setParameter("successUrl", "https://acme.example/account");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new LocalRedirectStrategy().sendRedirect(request, response, "https://acme.example/account");

        assertEquals("https://acme.example/account", response.getRedirectedUrl());
    }

    @Test
    public void otherHostAbsoluteSuccessUrlIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login_post.htm");
        request.setServerName("acme.example");
        request.setParameter("successUrl", "https://evil.example/account");
        try {
            new LocalRedirectStrategy().sendRedirect(request, new MockHttpServletResponse(), "https://evil.example/account");
            fail("Expected redirect to be rejected");
        } catch (MalformedURLException expected) {
            // expected
        }
    }

}
