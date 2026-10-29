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
import org.broadleafcommerce.openadmin.dto.PersistencePackage
import org.broadleafcommerce.openadmin.dto.SectionCrumb
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser
import org.broadleafcommerce.openadmin.server.security.extension.AdminSecurityCheckExtensionManager
import org.broadleafcommerce.openadmin.server.security.remote.AdminSecurityServiceRemote
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType
import org.broadleafcommerce.openadmin.server.security.service.AdminSecurityService
import org.broadleafcommerce.openadmin.server.security.service.RowLevelSecurityService
import org.broadleafcommerce.openadmin.server.security.service.type.PermissionType
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Request-supplied section crumbs must never act as an alternate permission source for admin entity operations.
 */
class AdminSecurityServiceRemoteSpec extends Specification {

    static final String ADMIN_USER = "org.broadleafcommerce.openadmin.server.security.domain.AdminUser"
    static final String PRODUCT = "org.broadleafcommerce.core.catalog.domain.Product"

    AdminSecurityService securityService = Mock()
    RowLevelSecurityService rowLevelSecurityService = Mock()
    AdminUser adminUser = Mock()
    AdminSecurityServiceRemote remoteService

    def setup() {
        AdminUser currentUser = adminUser
        remoteService = new AdminSecurityServiceRemote() {
            @Override
            AdminUser getPersistentAdminUser() {
                return currentUser
            }
        }
        remoteService.securityService = securityService
        remoteService.rowLevelSecurityService = rowLevelSecurityService
        remoteService.securityCheckExtensionManager = new AdminSecurityCheckExtensionManager()
    }

    @Unroll
    def "a crumb for a permitted section does not authorize #operationType on an unpermitted entity"() {
        given:
        PersistencePackage pkg = pkg(ADMIN_USER, crumb(PRODUCT))

        when:
        remoteService.securityCheck(pkg, operationType)

        then:
        1 * securityService.isUserQualifiedForOperationOnCeilingEntity(adminUser, permissionType, ADMIN_USER) >> false
        0 * securityService.isUserQualifiedForOperationOnCeilingEntity(_, _, PRODUCT)
        SecurityServiceException e = thrown()
        e.message.contains(ADMIN_USER)

        where:
        operationType              | permissionType
        EntityOperationType.FETCH  | PermissionType.READ
        EntityOperationType.ADD    | PermissionType.CREATE
        EntityOperationType.UPDATE | PermissionType.UPDATE
        EntityOperationType.REMOVE | PermissionType.DELETE
    }

    def "a permission on the security ceiling still authorizes the operation when crumbs are present"() {
        given:
        PersistencePackage pkg = pkg(ADMIN_USER, crumb(PRODUCT))

        when:
        remoteService.securityCheck(pkg, EntityOperationType.UPDATE)

        then:
        1 * securityService.isUserQualifiedForOperationOnCeilingEntity(adminUser, PermissionType.UPDATE, ADMIN_USER) >> true
        noExceptionThrown()
    }

    def "an explicit security ceiling is checked instead of the ceiling entity"() {
        given:
        PersistencePackage pkg = pkg(PRODUCT)
        pkg.setSecurityCeilingEntityFullyQualifiedClassname(ADMIN_USER)

        when:
        remoteService.securityCheck(pkg, EntityOperationType.FETCH)

        then:
        1 * securityService.isUserQualifiedForOperationOnCeilingEntity(adminUser, PermissionType.READ, ADMIN_USER) >> false
        0 * securityService.isUserQualifiedForOperationOnCeilingEntity(_, _, PRODUCT)
        thrown(SecurityServiceException)
    }

    def "a multi-ceiling check requires every ceiling to qualify"() {
        when:
        remoteService.securityCheck([PRODUCT, ADMIN_USER] as String[], EntityOperationType.FETCH)

        then:
        1 * securityService.isUserQualifiedForOperationOnCeilingEntity(adminUser, PermissionType.READ, PRODUCT) >> true
        1 * securityService.isUserQualifiedForOperationOnCeilingEntity(adminUser, PermissionType.READ, ADMIN_USER) >> false
        SecurityServiceException e = thrown()
        e.message.contains(ADMIN_USER)
    }

    protected PersistencePackage pkg(String ceilingEntity, SectionCrumb... crumbs) {
        PersistencePackage pkg = new PersistencePackage()
        pkg.setCeilingEntityFullyQualifiedClassname(ceilingEntity)
        pkg.setSectionCrumbs(crumbs)
        return pkg
    }

    protected SectionCrumb crumb(String sectionIdentifier) {
        SectionCrumb crumb = new SectionCrumb()
        crumb.setSectionIdentifier(sectionIdentifier)
        crumb.setSectionId("1")
        return crumb
    }

}
