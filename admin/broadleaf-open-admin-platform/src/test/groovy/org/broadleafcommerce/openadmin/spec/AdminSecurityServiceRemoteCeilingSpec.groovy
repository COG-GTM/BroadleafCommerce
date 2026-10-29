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
package org.broadleafcommerce.openadmin.spec

import org.broadleafcommerce.common.exception.SecurityServiceException
import org.broadleafcommerce.common.exception.ServiceException
import org.broadleafcommerce.openadmin.dto.Entity
import org.broadleafcommerce.openadmin.dto.PersistencePackage
import org.broadleafcommerce.openadmin.dto.SectionCrumb
import org.broadleafcommerce.openadmin.server.security.remote.AdminSecurityServiceRemote
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType
import org.broadleafcommerce.openadmin.server.security.service.AdminSecurityService
import org.broadleafcommerce.openadmin.server.security.service.RowLevelSecurityService
import org.broadleafcommerce.openadmin.server.security.service.type.PermissionType
import spock.lang.Specification

class AdminSecurityServiceRemoteCeilingSpec extends Specification {

    static final String ADMIN_USER = "org.broadleafcommerce.openadmin.server.security.domain.AdminUser"
    static final String CATEGORY = "org.broadleafcommerce.core.catalog.domain.Category"
    static final String XREF = "org.broadleafcommerce.core.catalog.domain.CategoryProductXref"

    AdminSecurityService securityService = Mock()
    List<String> checkedCeilings
    AdminSecurityServiceRemote remote

    def setup() {
        remote = new AdminSecurityServiceRemote() {
            @Override
            protected void securityCheck(String[] ceilingNames, EntityOperationType operationType) throws ServiceException {
                checkedCeilings = ceilingNames as List
            }
        }
        remote.securityService = securityService
        remote.rowLevelSecurityService = Stub(RowLevelSecurityService)
    }

    PersistencePackage pkg(String ceiling, String... crumbs) {
        PersistencePackage pp = new PersistencePackage()
        pp.ceilingEntityFullyQualifiedClassname = ceiling
        pp.entity = new Entity(type: [ceiling] as String[])
        pp.sectionCrumbs = crumbs.collect { new SectionCrumb(sectionIdentifier: it, sectionId: "1") } as SectionCrumb[]
        pp
    }

    def "a secured ceiling cannot be authorized by a request supplied section crumb"() {
        given:
        securityService.doesOperationExistForCeilingEntity(PermissionType.UPDATE, ADMIN_USER) >> true

        when:
        remote.securityCheck(pkg(ADMIN_USER, CATEGORY), EntityOperationType.UPDATE)

        then:
        checkedCeilings == [ADMIN_USER]
    }

    def "a secured ceiling cannot be removed on the strength of a section crumb"() {
        given:
        securityService.doesOperationExistForCeilingEntity(PermissionType.DELETE, ADMIN_USER) >> true

        when:
        remote.securityCheck(pkg(ADMIN_USER, CATEGORY), EntityOperationType.REMOVE)

        then:
        checkedCeilings == [ADMIN_USER]
    }

    def "an unsecured collection member still inherits its parent section's permission"() {
        given:
        securityService.doesOperationExistForCeilingEntity(PermissionType.UPDATE, XREF) >> false

        when:
        remote.securityCheck(pkg(XREF, CATEGORY), EntityOperationType.UPDATE)

        then:
        checkedCeilings == [XREF, CATEGORY]
    }

    def "the target ceiling is always checked first and not duplicated"() {
        given:
        securityService.doesOperationExistForCeilingEntity(_, _) >> false

        expect:
        remote.getAuthorizingCeilingNames(CATEGORY, [CATEGORY, XREF] as LinkedHashSet, EntityOperationType.ADD) as List ==
                [CATEGORY, XREF]
    }

    def "a blank target ceiling is rejected"() {
        when:
        remote.getAuthorizingCeilingNames(null, [CATEGORY] as LinkedHashSet, EntityOperationType.UPDATE)

        then:
        thrown(SecurityServiceException)
    }
}
