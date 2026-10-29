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
package org.broadleafcommerce.openadmin.web.filter

import org.broadleafcommerce.common.service.PersistenceService
import org.broadleafcommerce.openadmin.security.ClassNameRequestParamValidationServiceImpl
import spock.lang.Specification

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class BroadleafAdminRequestFilterSpec extends Specification {

    static final String CEILING_FIELD = "fields['ceilingEntity'].value"
    static final String VALID_ENTITY = "org.broadleafcommerce.common.site.domain.SiteImpl"

    BroadleafAdminRequestFilter filter = new BroadleafAdminRequestFilter()
    PersistenceService persistenceService = Mock()

    def setup() {
        persistenceService.validateEntityClassName(VALID_ENTITY) >> true
        persistenceService.validateEntityClassName(_) >> false
        filter.validationService = new ClassNameRequestParamValidationServiceImpl(persistenceService: persistenceService)
    }

    HttpServletRequest request(String servletPath, Map<String, String> params) {
        HttpServletRequest request = Mock()
        request.getServletPath() >> servletPath
        request.getParameter(_ as String) >> { String name -> params[name] }
        return request
    }

    def "translation form ceiling entity outside the entity whitelist is rejected with 404"() {
        given:
        HttpServletResponse response = Mock()
        FilterChain chain = Mock()

        when:
        filter.doFilterInternalUnlessIgnored(
                request(path, [(CEILING_FIELD): "java.lang.System"]), response, chain)

        then:
        1 * response.sendError(HttpServletResponse.SC_NOT_FOUND)
        0 * chain.doFilter(_, _)

        where:
        path << ["/translation", "/translation/add", "/translation/update"]
    }

    def "translation form ceiling entity on the entity whitelist passes validation"() {
        expect:
        filter.validateClassNameParams(request("/translation/add", [(CEILING_FIELD): VALID_ENTITY]))
        filter.validateClassNameParams(request("/translation/add", [:]))
    }

    def "ceilingEntity fields on non-translation forms are not treated as class names"() {
        expect:
        filter.validateClassNameParams(request("/adminSection/1",
                [(CEILING_FIELD): "org.broadleafcommerce.core.catalog.domain.Product"]))
    }
}
