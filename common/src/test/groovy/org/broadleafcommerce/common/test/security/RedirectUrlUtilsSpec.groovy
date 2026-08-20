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
package org.broadleafcommerce.common.test.security

import jakarta.servlet.http.HttpServletRequest
import org.broadleafcommerce.common.security.util.RedirectUrlUtils
import spock.lang.Specification
import spock.lang.Unroll

class RedirectUrlUtilsSpec extends Specification {

    @Unroll
    def "'#url' is local: #expected"() {
        expect:
            RedirectUrlUtils.isLocalRedirectUrl(url) == expected

        where:
            url                             || expected
            "/admin/login?error=true"       || true
            "?sessionTimeout=true"          || true
            "#section"                      || true
            "login?error=true"              || true
            null                            || false
            ""                              || false
            "//evil.com"                    || false
            "/\\evil.com"                   || false
            "\\\\evil.com"                  || false
            "https://evil.com"              || false
            "javascript:alert(1)"           || false
    }

    def "absolute urls are only local when the host matches the request"() {
        given:
            def request = Mock(HttpServletRequest)
            request.getServerName() >> "admin.mysite.com"

        expect:
            RedirectUrlUtils.isLocalRedirectUrl("https://admin.mysite.com/admin/login", request)
            !RedirectUrlUtils.isLocalRedirectUrl("https://evil.com/admin/login", request)
            !RedirectUrlUtils.isLocalRedirectUrl("//evil.com", request)
    }

    def "sanitize strips external targets"() {
        expect:
            RedirectUrlUtils.sanitizeRedirectUrl("/admin/login") == "/admin/login"
            RedirectUrlUtils.sanitizeRedirectUrl("//evil.com") == null
    }

}
