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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.MalformedURLException;

public class LocalRedirectStrategyTest {

    private static final String[] OFF_SITE_URLS = {
            "//evil.example",
            "//evil.example/account",
            "/\\evil.example",
            "/\\/evil.example",
            "\\\\evil.example",
            "/\t/evil.example",
            "/\n/evil.example",
            "https://evil.example/account",
            "http://acme-store.example@evil.example/",
            "https://evil.example\\@acme-store.example/account",
            "javascript:alert(1)",
            "/%2fevil.example",
            "/%2Fevil.example/account",
            "/%5cevil.example",
            "/%252fevil.example",
            "/%09/evil.example"
    };

    @Test
    public void rejectsOffSiteSuccessUrl() throws Exception {
        for (String url : OFF_SITE_URLS) {
            assertRejected("", "successUrl", url);
        }
    }

    @Test
    public void rejectsOffSiteFailureUrl() throws Exception {
        for (String url : OFF_SITE_URLS) {
            assertRejected("", "failureUrl", url);
        }
    }

    @Test
    public void rejectsProtocolRelativeUrlNotFromRequestParameter() throws Exception {
        MockHttpServletRequest request = request("");
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            new LocalRedirectStrategy().sendRedirect(request, response, "//evil.example");
            fail("Expected redirect to be rejected");
        } catch (MalformedURLException e) {
            assertNull(response.getRedirectedUrl());
        }
    }

    @Test
    public void rejectsOffSiteUrlUnderContextPath() throws Exception {
        assertRejected("/store", "successUrl", "https://acme-store.example/other/account");
        assertRejected("/store", "successUrl", "https://acme-store.example/storefront/account");
        assertRejected("/store", "successUrl", "https://acme-store.example/store/../admin");
        assertRejected("/store", "successUrl", "https://acme-store.example/store/%2e%2e/admin");
        assertRejected("/store", "successUrl", "https://acme-store.example/store/%252e%252e/admin");
        assertRejected("/store", "successUrl", "/../admin");
        assertRejected("/store", "successUrl", "/./account");
    }

    @Test
    public void contextRelativeRedirectToContextRootStaysOnContextRoot() throws Exception {
        LocalRedirectStrategy strategy = new LocalRedirectStrategy();
        strategy.setContextRelative(true);
        MockHttpServletRequest request = request("/store");
        request.setParameter("successUrl", "https://acme-store.example/store");
        MockHttpServletResponse response = new MockHttpServletResponse();
        strategy.sendRedirect(request, response, "https://acme-store.example/store");
        assertEquals("/store/", response.getRedirectedUrl());
    }

    @Test
    public void allowsLocalRelativeUrl() throws Exception {
        assertEquals("/account", redirect("", "successUrl", "/account"));
        assertEquals("/store/account", redirect("/store", "successUrl", "/account"));
        assertEquals("/login?error=true&successUrl=/account", redirect("", "failureUrl", "/login?error=true&successUrl=/account"));
    }

    @Test
    public void allowsLocalAbsoluteUrl() throws Exception {
        assertEquals("https://acme-store.example/account",
                redirect("", "successUrl", "https://acme-store.example/account"));
        assertEquals("https://acme-store.example/store/account",
                redirect("/store", "successUrl", "https://acme-store.example/store/account"));
        assertEquals("https://ACME-STORE.example/store",
                redirect("/store", "successUrl", "https://ACME-STORE.example/store"));
    }

    @Test
    public void enforcesPortMatchWhenConfigured() throws Exception {
        LocalRedirectStrategy strategy = new LocalRedirectStrategy();
        strategy.setEnforcePortMatch(true);
        MockHttpServletRequest request = request("");
        request.setParameter("successUrl", "https://acme-store.example:8443/account");
        try {
            strategy.sendRedirect(request, new MockHttpServletResponse(), "https://acme-store.example:8443/account");
            fail("Expected redirect to be rejected");
        } catch (MalformedURLException e) {
            // expected
        }
        request.setParameter("successUrl", "https://acme-store.example:443/account");
        MockHttpServletResponse response = new MockHttpServletResponse();
        strategy.sendRedirect(request, response, "https://acme-store.example:443/account");
        assertEquals("https://acme-store.example:443/account", response.getRedirectedUrl());
    }

    @Test
    public void detectsUnsafeRedirectSyntax() {
        for (String url : new String[] {"//evil.example", "/\\evil.example", "/a\\b", "/\t/evil", " /account", "/a\u0085b",
                "/%2fevil.example", "/%5Cevil.example", "/%25252fevil.example", "/a/%2e%2E/b", "/a/..", "https://host/a/../b",
                "/a%0d%0aSet-Cookie:x"}) {
            assertEquals(url, true, LocalRedirectStrategy.hasUnsafeRedirectSyntax(url));
        }
        for (String url : new String[] {null, "", "/", "/account", "/login?error=true", "account", "/category/men%20shirts",
                "/search?q=a%2F..%2Fb", "/a/..b/c", "https://host//account", "/100%", "/account#/../x"}) {
            assertEquals(url, false, LocalRedirectStrategy.hasUnsafeRedirectSyntax(url));
        }
    }

    private String redirect(String contextPath, String parameter, String url) throws Exception {
        MockHttpServletRequest request = request(contextPath);
        request.setParameter(parameter, url);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new LocalRedirectStrategy().sendRedirect(request, response, url);
        return response.getRedirectedUrl();
    }

    private void assertRejected(String contextPath, String parameter, String url) throws Exception {
        MockHttpServletRequest request = request(contextPath);
        request.setParameter(parameter, url);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            new LocalRedirectStrategy().sendRedirect(request, response, url);
            fail("Expected redirect to be rejected: " + url + " (redirected to " + response.getRedirectedUrl() + ")");
        } catch (MalformedURLException e) {
            assertNull(response.getRedirectedUrl());
        }
    }

    private MockHttpServletRequest request(String contextPath) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", contextPath + "/login_post.htm");
        request.setScheme("https");
        request.setServerName("acme-store.example");
        request.setServerPort(443);
        request.setContextPath(contextPath);
        return request;
    }
}
