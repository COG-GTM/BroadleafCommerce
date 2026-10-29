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
package org.broadleafcommerce.openadmin.web.compatibility

import org.springframework.security.web.firewall.FirewalledRequest
import spock.lang.Specification

import jakarta.servlet.http.HttpServletRequest

class JSCompatibilityRequestWrapperSpec extends Specification {

    def "Path accessors are passed through unchanged so '____' cannot become '..'"() {
        setup:
        HttpServletRequest request = Mock(HttpServletRequest)
        request.getContextPath() >> "/admin__x"
        request.getServletPath() >> "/preview/____/____/x"
        request.getPathTranslated() >> "/srv/____/x"
        request.getRequestURI() >> "/admin__x/preview/____/____/x"
        request.getRequestURL() >> new StringBuffer("http://localhost/admin__x/preview/____/____/x")
        request.getQueryString() >> "path=____/____&a__b=1"

        when:
        JSCompatibilityRequestWrapper wrapper = new JSCompatibilityRequestWrapper(request)

        then:
        wrapper.getContextPath() == "/admin__x"
        wrapper.getServletPath() == "/preview/____/____/x"
        wrapper.getPathTranslated() == "/srv/____/x"
        wrapper.getRequestURI() == "/admin__x/preview/____/____/x"
        wrapper.getRequestURL().toString() == "http://localhost/admin__x/preview/____/____/x"
        wrapper.getQueryString() == "path=____/____&a__b=1"
        !(wrapper instanceof FirewalledRequest)
    }

    def "Encoded parameter names are still converted for the admin JavaScript"() {
        setup:
        HttpServletRequest request = Mock(HttpServletRequest)
        request.getParameterNames() >> { Collections.enumeration(["fields['defaultSku__name'].value"]) }
        request.getParameterMap() >> ["fields['defaultSku__name'].value": ["Hat"] as String[]]
        request.getParameterValues("fields['defaultSku__name'].value") >> (["Hat"] as String[])
        Map<String, Object> attributes = [:]
        request.getAttribute(_ as String) >> { String name -> attributes[name] }
        request.setAttribute(_ as String, _) >> { String name, Object value -> attributes[name] = value }

        when:
        JSCompatibilityRequestWrapper wrapper = new JSCompatibilityRequestWrapper(request)

        then:
        Collections.list(wrapper.getParameterNames()) == ["fields['defaultSku.name'].value"]
        wrapper.getParameterMap().keySet() == ["fields['defaultSku.name'].value"] as Set
        wrapper.getParameterValues("fields['defaultSku.name'].value") == ["Hat"] as String[]
    }

}
