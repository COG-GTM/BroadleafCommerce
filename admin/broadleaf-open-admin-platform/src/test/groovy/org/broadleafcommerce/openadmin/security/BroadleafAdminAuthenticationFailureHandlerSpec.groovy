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

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.BadCredentialsException

import spock.lang.Specification
import spock.lang.Unroll

class BroadleafAdminAuthenticationFailureHandlerSpec extends Specification {

    BroadleafAdminAuthenticationFailureHandler handler = new BroadleafAdminAuthenticationFailureHandler("/login?error=true")

    MockHttpServletRequest request
    MockHttpServletResponse response = new MockHttpServletResponse()

    def setup() {
        request = new MockHttpServletRequest("POST", "/admin/login_admin_post")
        request.serverName = "admin.acme.test"
        request.serverPort = 443
        request.scheme = "https"
        request.contextPath = "/admin"
    }

    String fail() {
        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"))
        response.redirectedUrl
    }

    @Unroll
    def "non-local failureUrl #failureUrl falls back to the default failure page"() {
        given:
        request.addParameter("failureUrl", failureUrl)

        expect:
        fail() == "/admin/login?error=true"

        where:
        failureUrl << [
                "https://acme-admin-login.example",
                "http://acme-admin-login.example/login",
                "//acme-admin-login.example",
                "/\\acme-admin-login.example",
                "\\\\acme-admin-login.example",
                "/\t/acme-admin-login.example",
                "%2F%2Facme-admin-login.example",
                "javascript:alert(1)",
                "acme-admin-login.example",
                "/login?next=https://acme-admin-login.example"
        ]
    }

    def "failureUrl stays external-proof when a successUrl is appended"() {
        given:
        request.addParameter("failureUrl", "https://acme-admin-login.example")
        request.addParameter("successUrl", "/admin/product")

        expect:
        fail() == "/admin/login?error=true&successUrl=/admin/product"
    }

    def "local failureUrl is honored"() {
        given:
        request.addParameter("failureUrl", "/login?error=locked")
        request.addParameter("successUrl", "/admin/order")

        expect:
        fail() == "/admin/login?error=locked&successUrl=/admin/order"
    }

    @Unroll
    def "non-local successUrl #successUrl is dropped"() {
        given:
        request.addParameter("successUrl", successUrl)

        expect:
        fail() == "/admin/login?error=true"

        where:
        successUrl << ["https://evil.example", "//evil.example", "/\\evil.example"]
    }

    def "same-host referer is preserved as the successUrl"() {
        given:
        request.addHeader("referer", "https://admin.acme.test/admin/product/1")

        expect:
        fail() == "/admin/login?error=true&successUrl=https://admin.acme.test/admin/product/1"
    }

    def "successUrl carried in a same-host referer is preserved"() {
        given:
        request.addHeader("referer", "https://admin.acme.test/admin/login?successUrl=/admin/offer&foo=bar")

        expect:
        fail() == "/admin/login?error=true&successUrl=/admin/offer"
    }

    @Unroll
    def "encoded characters in a referer successUrl #successUrl are preserved"() {
        given:
        request.addHeader("referer", "https://admin.acme.test/admin/login?successUrl=" + successUrl + "&foo=bar")

        expect:
        fail() == "/admin/login?error=true&successUrl=" + successUrl

        where:
        successUrl << ["/admin/search?q=red%26blue", "/admin/search?q=red+blue", "%2Fadmin%2Fproduct"]
    }

    @Unroll
    def "referer #referer is ignored"() {
        given:
        request.addHeader("referer", referer)

        expect:
        fail() == "/admin/login?error=true"

        where:
        referer << [
                "https://evil.example/admin/login",
                "https://admin.acme.test.evil.example/admin",
                "https://admin.acme.test/admin/login?successUrl=https://evil.example",
                "https://admin.acme.test/admin/login?successUrl=//evil.example",
                "https://admin.acme.test/admin/login?successUrl=%2F%2Fevil.example",
                "https://admin.acme.test/admin/login?successUrl=https%3A%2F%2Fevil.example"
        ]
    }

    def "session timeout still redirects to the login page"() {
        given:
        request.setAttribute("sessionTimeout", Boolean.TRUE)
        request.addParameter("failureUrl", "https://evil.example")

        expect:
        fail() == "/admin?sessionTimeout=true"
    }
}
