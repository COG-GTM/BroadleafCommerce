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
package org.broadleafcommerce.openadmin.spec.security

import org.broadleafcommerce.openadmin.security.BroadleafAdminAuthenticationSuccessHandler
import org.broadleafcommerce.openadmin.server.security.remote.SecurityVerifier
import org.springframework.security.core.Authentication
import org.springframework.security.web.savedrequest.SavedRequest

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import spock.lang.Specification
import spock.lang.Unroll

class BroadleafAdminAuthenticationSuccessHandlerSpec extends Specification {

    static final String SAVED_REQUEST_ATTR = "SPRING_SECURITY_SAVED_REQUEST"
    static final String ORIGIN = "https://admin.acme.example"

    BroadleafAdminAuthenticationSuccessHandler handler
    HttpServletRequest request
    HttpServletResponse response
    HttpSession session
    String savedRedirectUrl
    String redirectedTo
    Integer errorStatus

    def setup() {
        handler = new BroadleafAdminAuthenticationSuccessHandler()
        handler.adminRemoteSecurityService = Stub(SecurityVerifier)

        SavedRequest savedRequest = Stub(SavedRequest) {
            getRedirectUrl() >> { savedRedirectUrl }
        }
        session = Stub(HttpSession) {
            getAttribute(SAVED_REQUEST_ATTR) >> savedRequest
        }
        request = Stub(HttpServletRequest) {
            getSession() >> session
            getSession(_) >> session
            getScheme() >> "https"
            getServerName() >> "admin.acme.example"
            getServerPort() >> 443
            getContextPath() >> "/admin"
            getParameter(_) >> null
        }
        response = Stub(HttpServletResponse) {
            encodeRedirectURL(_) >> { String url -> url }
            sendRedirect(_) >> { String url -> redirectedTo = url }
            sendError(_) >> { int status -> errorStatus = status }
        }
    }

    @Unroll
    def "post-login redirect to an off-site successUrl falls back to the default target: #successUrl"() {
        given:
        savedRedirectUrl = ORIGIN + "/admin/?successUrl=" + successUrl

        when:
        handler.onAuthenticationSuccess(request, response, Stub(Authentication))

        then:
        errorStatus == null
        redirectedTo == "/admin/"

        where:
        successUrl << [
                "https://acme-admin.evil.example",
                "http://acme-admin.evil.example/admin",
                "//acme-admin.evil.example",
                "https://admin.acme.example.evil.example/admin",
                "https://admin.acme.example@evil.example/admin"
        ]
    }

    @Unroll
    def "post-login redirect never leaves the admin host for an obfuscated successUrl: #successUrl"() {
        given:
        savedRedirectUrl = ORIGIN + "/admin/?successUrl=" + successUrl

        when:
        handler.onAuthenticationSuccess(request, response, Stub(Authentication))

        then: "either the saved request is rejected outright or the default target is used"
        (errorStatus == 403 && redirectedTo == null) || (errorStatus == null && redirectedTo == "/admin/")

        where:
        successUrl << [
                "/\\acme-admin.evil.example",
                "https%3A%2F%2Facme-admin.evil.example",
                "%2F%2Facme-admin.evil.example",
                "/%2Facme-admin.evil.example",
                "/%252Facme-admin.evil.example",
                "/%5Cacme-admin.evil.example",
                "/%09/acme-admin.evil.example",
                "javascript:alert(1)"
        ]
    }

    @Unroll
    def "post-login redirect to a local target is preserved: #saved"() {
        given:
        savedRedirectUrl = saved

        when:
        handler.onAuthenticationSuccess(request, response, Stub(Authentication))

        then:
        errorStatus == null
        redirectedTo == expected

        where:
        saved                                                                 | expected
        ORIGIN + "/admin/product/123"                                         | ORIGIN + "/admin/product/123"
        ORIGIN + "/admin/product?name=a+b&sort=asc"                           | ORIGIN + "/admin/product?name=a+b&sort=asc"
        ORIGIN + "/admin/search?q=50%25"                                      | ORIGIN + "/admin/search?q=50%25"
        ORIGIN + "/admin/?successUrl=/product/123"                            | "/admin/product/123"
        ORIGIN + "/admin/?successUrl=/order&sessionTimeout=true"              | "/admin/order"
        ORIGIN + "/admin/?successUrl=" + ORIGIN + "/admin/product/123"        | ORIGIN + "/admin/product/123"
        ORIGIN + "/admin/login"                                               | ORIGIN + "/admin"
    }

    def "saved request outside the admin redirect allowlist is still rejected"() {
        given:
        savedRedirectUrl = "https://evil.example/phish"

        when:
        handler.onAuthenticationSuccess(request, response, Stub(Authentication))

        then:
        errorStatus == 403
        redirectedTo == null
    }

    @Unroll
    def "isSameOriginRedirectUrl(#url) == #expected"() {
        expect:
        handler.isSameOriginRedirectUrl(url, request) == expected

        where:
        url                                      | expected
        ""                                       | true
        "/"                                      | true
        "/admin/product/1"                       | true
        "/admin/product?q=a%20b"                 | true
        "/admin/product?ref=https://example.org" | true
        "/admin/search?q=50%25"                  | true
        "/admin/search?q=%zz"                    | true
        "/admin/50%25off"                        | true
        ORIGIN                                   | true
        ORIGIN + "/admin"                        | true
        ORIGIN + ":443/admin"                    | true
        "HTTPS://ADMIN.ACME.EXAMPLE/admin"       | true
        null                                     | false
        "admin/product"                          | false
        "//example.org"                          | false
        "/\\example.org"                         | false
        "/admin\\..\\example.org"                | false
        "/ /example.org"                         | false
        "/%2F/example.org"                       | false
        "/%5Cexample.org/"                       | false
        "/%255Cexample.org/"                     | false
        "/%25%32%46example.org"                  | false
        "/admin/https://example.org"             | false
        "https://example.org"                    | false
        "http://admin.acme.example/admin"        | false
        ORIGIN + ":8443/admin"                   | false
        ORIGIN + ".evil.example/admin"           | false
        ORIGIN + "@evil.example/admin"           | false
        "/%zz"                                   | false
    }
}
