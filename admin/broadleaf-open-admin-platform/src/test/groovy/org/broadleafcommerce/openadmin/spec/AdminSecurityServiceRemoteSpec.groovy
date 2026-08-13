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

/**
 * Verifies that admin entity authorization is performed against the security ceiling of the entity actually being
 * operated on and cannot be satisfied by client supplied section crumbs.
 */
class AdminSecurityServiceRemoteSpec extends Specification {

    static final String TARGET_CEILING = "org.broadleafcommerce.openadmin.server.security.domain.AdminUser"
    static final String AUTHORIZED_CEILING = "org.broadleafcommerce.core.catalog.domain.Product"

    AdminSecurityService securityService
    AdminUser adminUser
    AdminSecurityServiceRemote remoteService

    def setup() {
        securityService = Mock(AdminSecurityService)
        adminUser = Mock(AdminUser)

        //a real manager with no registered handlers always reports NOT_HANDLED
        AdminSecurityCheckExtensionManager extensionManager = new AdminSecurityCheckExtensionManager()

        AdminUser currentUser = adminUser
        remoteService = new AdminSecurityServiceRemote() {
            @Override
            AdminUser getPersistentAdminUser() {
                return currentUser
            }
        }
        remoteService.securityService = securityService
        remoteService.securityCheckExtensionManager = extensionManager
        remoteService.rowLevelSecurityService = Mock(RowLevelSecurityService)
    }

    def "section crumbs cannot authorize an operation on an unrelated entity"() {
        given:
        PersistencePackage pkg = new PersistencePackage()
        pkg.setCeilingEntityFullyQualifiedClassname(TARGET_CEILING)
        pkg.setSectionCrumbs([crumb(AUTHORIZED_CEILING)] as SectionCrumb[])

        when:
        remoteService.securityCheck(pkg, EntityOperationType.FETCH)

        then:
        1 * securityService.isUserQualifiedForOperationOnCeilingEntity(adminUser, PermissionType.READ, TARGET_CEILING) >> false
        0 * securityService.isUserQualifiedForOperationOnCeilingEntity(adminUser, _, AUTHORIZED_CEILING)
        thrown(SecurityServiceException)
    }

    def "a permission on the target ceiling authorizes the operation"() {
        given:
        PersistencePackage pkg = new PersistencePackage()
        pkg.setCeilingEntityFullyQualifiedClassname(TARGET_CEILING)
        pkg.setSectionCrumbs([crumb(AUTHORIZED_CEILING)] as SectionCrumb[])

        when:
        remoteService.securityCheck(pkg, EntityOperationType.UPDATE)

        then:
        1 * securityService.isUserQualifiedForOperationOnCeilingEntity(adminUser, PermissionType.UPDATE, TARGET_CEILING) >> true
        notThrown(SecurityServiceException)
    }

    def "the explicit security ceiling takes precedence over the ceiling entity"() {
        given:
        PersistencePackage pkg = new PersistencePackage()
        pkg.setCeilingEntityFullyQualifiedClassname(AUTHORIZED_CEILING)
        pkg.setSecurityCeilingEntityFullyQualifiedClassname(TARGET_CEILING)

        when:
        remoteService.securityCheck(pkg, EntityOperationType.ADD)

        then:
        1 * securityService.isUserQualifiedForOperationOnCeilingEntity(adminUser, PermissionType.CREATE, TARGET_CEILING) >> false
        thrown(SecurityServiceException)
    }

    protected SectionCrumb crumb(String sectionIdentifier) {
        SectionCrumb crumb = new SectionCrumb()
        crumb.setSectionIdentifier(sectionIdentifier)
        crumb.setSectionId("1")
        return crumb
    }

}
