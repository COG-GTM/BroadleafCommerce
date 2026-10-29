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
import org.springframework.security.authentication.BadCredentialsException;

import java.net.MalformedURLException;

public class LocalRedirectStrategyTest {

    private static final String SERVER = "shop.example";

    @Test
    public void rejectsProtocolRelativeAndBackslashLocalTargets() throws Exception {
        String[] attacks = {
                "//evil.example",
                "//evil.example/account",
                "/\\evil.example",
                "\\\\evil.example",
                "\\/evil.example",
                "/\t/evil.example",
                "/\n/evil.example",
                "//3232235781"
        };
        for (String attack : attacks) {
            assertRejected("", attack, "successUrl");
            assertRejected("", attack, "failureUrl");
            assertRejected("", attack, null);
        }
    }

    @Test
    public void rejectsBackslashAuthorityConfusion() throws Exception {
        assertRejected("", "http://evil.example\\@" + SERVER + "/", "successUrl");
        assertRejected("", "https://evil.example\\@" + SERVER + "/account", "failureUrl");
    }

    @Test
    public void rejectsUserInfoAndForeignHosts() throws Exception {
        assertRejected("", "http://evil.example@" + SERVER + "/", "successUrl");
        assertRejected("", "http://" + SERVER + "@evil.example/", "successUrl");
        assertRejected("", "https://evil.example/account", "successUrl");
        assertRejected("", "javascript:alert(1)", "successUrl");
        assertRejected("/store", "https://" + SERVER + "/storefront/account", "successUrl");
        assertRejected("/store", "https://" + SERVER + "/other", "successUrl");
    }

    @Test
    public void rejectsProtocolRelativeTargetsWhenContextRelative() throws Exception {
        LocalRedirectStrategy strategy = new LocalRedirectStrategy();
        strategy.setContextRelative(true);
        MockHttpServletRequest request = request("/store", "//evil.example", "successUrl");
        try {
            strategy.sendRedirect(request, new MockHttpServletResponse(), "//evil.example");
            fail("Expected //evil.example to be rejected");
        } catch (MalformedURLException expected) {
            // expected
        }
    }

    @Test
    public void rejectsDotSegmentsEscapingTheContext() throws Exception {
        assertRejected("/store", "https://" + SERVER + "/store/../admin", "successUrl");
        assertRejected("/store", "https://" + SERVER + "/store/%2e%2e/admin", "successUrl");
        assertRejected("/store", "https://" + SERVER + "/store/./account", "failureUrl");
    }

    @Test
    public void contextRelativeRedirectToContextRootStaysOnContextRoot() throws Exception {
        LocalRedirectStrategy strategy = new LocalRedirectStrategy();
        strategy.setContextRelative(true);
        assertEquals("/store/", strategy.calculateRedirectUrl("/store", "https://" + SERVER + "/store"));
        assertEquals("/store/?tab=home", strategy.calculateRedirectUrl("/store", "https://" + SERVER + "/store?tab=home"));

        String url = "https://" + SERVER + "/store";
        MockHttpServletResponse response = new MockHttpServletResponse();
        strategy.sendRedirect(request("/store", url, "successUrl"), response, url);
        assertEquals("/store/", response.getRedirectedUrl());
    }

    @Test
    public void allowsLocalPaths() throws Exception {
        assertEquals("/account", redirect("", "/account", "successUrl"));
        assertEquals("/store/account", redirect("/store", "/account", "successUrl"));
        assertEquals("/store/account?tab=orders", redirect("/store", "/account?tab=orders", "failureUrl"));
    }

    @Test
    public void allowsSameHostAbsoluteUrls() throws Exception {
        assertEquals("https://" + SERVER + "/account", redirect("", "https://" + SERVER + "/account", "successUrl"));
        assertEquals("https://" + SERVER + "/store/account",
                redirect("/store", "https://" + SERVER + "/store/account", "successUrl"));
        assertEquals("http://SHOP.example:8080/account", redirect("", "http://SHOP.example:8080/account", "failureUrl"));
    }

    @Test
    public void enforcesPortWhenConfigured() throws Exception {
        LocalRedirectStrategy strategy = new LocalRedirectStrategy();
        strategy.setEnforcePortMatch(true);
        try {
            strategy.validateRedirectUrl("", "http://" + SERVER + ":9999/account", SERVER, 8080);
            fail("Expected port mismatch to be rejected");
        } catch (MalformedURLException expected) {
            // expected
        }
        strategy.validateRedirectUrl("", "http://" + SERVER + ":8080/account", SERVER, 8080);
    }

    @Test
    public void hasUnsafeRedirectSyntax() {
        assertTrue(LocalRedirectStrategy.hasUnsafeRedirectSyntax("//evil.example"));
        assertTrue(LocalRedirectStrategy.hasUnsafeRedirectSyntax("/\\evil.example"));
        assertTrue(LocalRedirectStrategy.hasUnsafeRedirectSyntax("http://evil.example\\@" + SERVER));
        assertTrue(LocalRedirectStrategy.hasUnsafeRedirectSyntax("/\t/evil.example"));
        assertFalse(LocalRedirectStrategy.hasUnsafeRedirectSyntax("/account"));
        assertFalse(LocalRedirectStrategy.hasUnsafeRedirectSyntax("/account?next=//x"));
        assertFalse(LocalRedirectStrategy.hasUnsafeRedirectSyntax("https://" + SERVER + "/account"));
        assertFalse(LocalRedirectStrategy.hasUnsafeRedirectSyntax(null));
    }

    @Test
    public void failureHandlerDropsProtocolRelativeParameters() throws Exception {
        BroadleafAuthenticationFailureHandler handler = new BroadleafAuthenticationFailureHandler("/login?error=true");
        handler.setRedirectStrategy(new LocalRedirectStrategy());
        assertNull(handler.validateUrlParam("//3232235781"));
        assertNull(handler.validateUrlParam("/\\3232235781"));
        assertEquals("/login", handler.validateUrlParam("/login"));

        MockHttpServletRequest request = request("", "//3232235781", "failureUrl");
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));
        assertEquals("/login?error=true", response.getRedirectedUrl());
    }

    private String redirect(String contextPath, String url, String parameter) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new LocalRedirectStrategy().sendRedirect(request(contextPath, url, parameter), response, url);
        return response.getRedirectedUrl();
    }

    private void assertRejected(String contextPath, String url, String parameter) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            new LocalRedirectStrategy().sendRedirect(request(contextPath, url, parameter), response, url);
            fail("Expected redirect to " + url + " to be rejected, got Location " + response.getRedirectedUrl());
        } catch (MalformedURLException expected) {
            assertNull(response.getRedirectedUrl());
        }
    }

    private MockHttpServletRequest request(String contextPath, String url, String parameter) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", contextPath + "/login_post.htm");
        request.setServerName(SERVER);
        request.setServerPort(8080);
        request.setContextPath(contextPath);
        if (parameter != null) {
            request.setParameter(parameter, url);
        }
        return request;
    }

}
