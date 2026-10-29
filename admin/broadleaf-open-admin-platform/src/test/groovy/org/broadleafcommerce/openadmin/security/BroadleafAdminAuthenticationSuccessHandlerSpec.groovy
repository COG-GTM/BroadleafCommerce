/*-
 * #%L
 * BroadleafCommerce Open Admin Platform
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
package org.broadleafcommerce.openadmin.security

import org.broadleafcommerce.openadmin.server.security.remote.SecurityVerifier
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.web.savedrequest.SavedRequest
import org.springframework.security.web.savedrequest.RequestCache

import spock.lang.Specification
import spock.lang.Unroll

class BroadleafAdminAuthenticationSuccessHandlerSpec extends Specification {

    BroadleafAdminAuthenticationSuccessHandler handler
    RequestCache requestCache

    def setup() {
        handler = new BroadleafAdminAuthenticationSuccessHandler()
        handler.adminRemoteSecurityService = Mock(SecurityVerifier)
        handler.setDefaultTargetUrl("/admin")
        requestCache = Mock(RequestCache)
        handler.@requestCache = requestCache
    }

    String loginWithSavedRequest(String savedRedirectUrl) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/login_admin_post")
        request.setScheme("https")
        request.setServerName("admin.acme.com")
        request.setServerPort(443)
        SavedRequest savedRequest = Mock(SavedRequest)
        savedRequest.getRedirectUrl() >> savedRedirectUrl
        requestCache.getRequest(_, _) >> savedRequest
        MockHttpServletResponse response = new MockHttpServletResponse()
        handler.onAuthenticationSuccess(request, response, new TestingAuthenticationToken("admin", "x"))
        return response.getRedirectedUrl()
    }

    @Unroll
    def "embedded successUrl #successUrl pointing off-host falls back to the default target"() {
        expect:
        loginWithSavedRequest("https://admin.acme.com/admin/product?successUrl=" + successUrl) == "/admin"

        where:
        successUrl << [
                "https://evil.example/relogin",
                "http://evil.example",
                "//evil.example/relogin",
                "/\\evil.example",
                "\\\\evil.example",
                "%2F%2Fevil.example",
                "%252F%252Fevil.example",
                "https%3A%2F%2Fevil.example",
                "javascript:alert(1)",
                "evil.example",
                "/%5Cevil.example",
                "%09//evil.example",
                "https://admin.acme.com.evil.example/admin",
                "https://admin.acme.com@evil.example/admin"
        ]
    }

    @Unroll
    def "local successUrl #successUrl is still honoured"() {
        expect:
        loginWithSavedRequest("https://admin.acme.com/admin/product?successUrl=" + successUrl + "&x=1") == expected

        where:
        successUrl                                 || expected
        "/admin/order"                             || "/admin/order"
        "/admin/product/1?tab=general"             || "/admin/product/1?tab=general"
        "https://admin.acme.com/admin/customer"    || "https://admin.acme.com/admin/customer"
        "https://admin.acme.com:443/admin/offer"   || "https://admin.acme.com:443/admin/offer"
    }

    def "saved request without successUrl keeps redirecting to the saved request"() {
        expect:
        loginWithSavedRequest("https://admin.acme.com/admin/product?sessionTimeout=true") == "https://admin.acme.com/admin/product"
    }
}
