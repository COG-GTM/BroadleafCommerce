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

import org.springframework.security.authentication.BadCredentialsException

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import spock.lang.Specification
import spock.lang.Unroll

class BroadleafAdminAuthenticationFailureHandlerSpec extends Specification {

    BroadleafAdminAuthenticationFailureHandler handler = new BroadleafAdminAuthenticationFailureHandler("/login?error=true")

    String redirectFor(Map<String, String> params, String referer = null) {
        HttpServletRequest request = Stub(HttpServletRequest) {
            getParameter(_ as String) >> { String name -> params[name] }
            getHeader("referer") >> referer
            getServerName() >> "admin.acme.com"
            getServerPort() >> 443
            getScheme() >> "https"
            getContextPath() >> "/admin"
            getSession(_) >> Stub(HttpSession)
            getSession() >> Stub(HttpSession)
        }
        String location = null
        HttpServletResponse response = Mock(HttpServletResponse) {
            encodeRedirectURL(_ as String) >> { String url -> url }
            sendRedirect(_ as String) >> { String url -> location = url }
        }
        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"))
        return location
    }

    @Unroll
    def "off-host failureUrl #failureUrl falls back to the default failure url"() {
        expect:
        redirectFor([failureUrl: failureUrl]) == "/admin/login?error=true"

        where:
        failureUrl << [
                "https://evil.example/login",
                "https%3A%2F%2Fevil.example%2Flogin",
                "//evil.example/login",
                "/\\evil.example/login",
                "\\\\evil.example",
                "javascript:alert(1)",
                "evil.example/login",
                "%252F%252Fevil.example",
                "/%2F%2Fevil.example/login".replace("%2F", "%252F")
        ]
    }

    def "a local failureUrl is still honored"() {
        expect:
        redirectFor([failureUrl: "/login?error=locked"]) == "/admin/login?error=locked"
    }

    @Unroll
    def "off-host successUrl #successUrl is dropped"() {
        expect:
        redirectFor([failureUrl: "/login", successUrl: successUrl]) == "/admin/login"

        where:
        successUrl << [
                "https://evil.example/",
                "//evil.example/",
                "https://admin.acme.com@evil.example/",
                "/\\evil.example",
                "%252F%252Fevil.example"
        ]
    }

    def "off-host referer is not carried as successUrl"() {
        expect:
        redirectFor([:], "https://evil.example/phish") == "/admin/login?error=true"
        redirectFor([:], "https://evil.example/x?successUrl=https://evil.example/") == "/admin/login?error=true"
        redirectFor([:], "https://evil.example/?d=50%25") == "/admin/login?error=true"
        redirectFor([:], "https://admin.acme.com.evil.example/") == "/admin/login?error=true"
        redirectFor([:], "https://admin.acme.com:x@evil.example/") == "/admin/login?error=true"
        redirectFor([:], "https://evil.example#@admin.acme.com") == "/admin/login?error=true"
    }

    def "local and same-host success urls are preserved"() {
        expect:
        redirectFor([failureUrl: "/login", successUrl: "/admin/product"]) == "/admin/login?successUrl=/admin/product"
        redirectFor([:], "https://admin.acme.com/admin/login?successUrl=%2Fadmin%2Fproduct") ==
                "/admin/login?error=true&successUrl=%2Fadmin%2Fproduct"
        redirectFor([:], "https://admin.acme.com/admin/order") ==
                "/admin/login?error=true&successUrl=https://admin.acme.com/admin/order"
    }

    def "literal percent and plus signs in success urls are preserved"() {
        expect:
        redirectFor([:], "https://admin.acme.com/admin/orders?discount=50%25") ==
                "/admin/login?error=true&successUrl=https://admin.acme.com/admin/orders?discount=50%25"
        redirectFor([:], "https://admin.acme.com/admin/search?q=a+b") ==
                "/admin/login?error=true&successUrl=https://admin.acme.com/admin/search?q=a+b"
        redirectFor([failureUrl: "/login", successUrl: "/admin/orders?d=50%2525"]) ==
                "/admin/login?successUrl=/admin/orders?d=50%25"
    }

}
