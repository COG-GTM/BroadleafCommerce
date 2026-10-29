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
package org.broadleafcommerce.openadmin.spec

import org.broadleafcommerce.common.exception.SecurityServiceException
import org.broadleafcommerce.openadmin.dto.Entity
import org.broadleafcommerce.openadmin.dto.PersistencePackage
import org.broadleafcommerce.openadmin.dto.Property
import org.broadleafcommerce.openadmin.dto.SectionCrumb
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser
import org.broadleafcommerce.openadmin.server.security.extension.AdminSecurityCheckExtensionManager
import org.broadleafcommerce.openadmin.server.security.remote.AdminSecurityServiceRemote
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType
import org.broadleafcommerce.openadmin.server.security.service.AdminSecurityService
import org.broadleafcommerce.openadmin.server.security.service.RowLevelSecurityService
import org.broadleafcommerce.openadmin.server.security.service.type.PermissionType
import spock.lang.Specification

class AdminSecurityServiceRemoteSpec extends Specification {

    static final String REVIEW = "com.example.ReviewImpl"
    static final String ADMIN_USER = "com.example.AdminUserImpl"
    static final String SKU = "com.example.SkuImpl"

    AdminUser adminUser = Mock(AdminUser)
    AdminSecurityService securityService = Mock(AdminSecurityService)
    AdminSecurityServiceRemote remote

    def setup() {
        AdminUser user = adminUser
        remote = new AdminSecurityServiceRemote() {
            @Override
            AdminUser getPersistentAdminUser() {
                return user
            }
        }
        remote.securityService = securityService
        remote.rowLevelSecurityService = Mock(RowLevelSecurityService)
        remote.securityCheckExtensionManager = new AdminSecurityCheckExtensionManager()
    }

    PersistencePackage pkg(String ceiling, String... crumbs) {
        PersistencePackage pp = new PersistencePackage()
        pp.setCeilingEntityFullyQualifiedClassname(ceiling)
        Entity entity = new Entity()
        entity.setType([ceiling] as String[])
        entity.setProperties([] as Property[])
        pp.setEntity(entity)
        pp.setSectionCrumbs(crumbs.collect { new SectionCrumb(sectionIdentifier: it, sectionId: "1") } as SectionCrumb[])
        return pp
    }

    def "a crumb the user is authorized for does not authorize a separately secured target (#operation)"() {
        given:
        securityService.doesOperationExistForCeilingEntity(_, ADMIN_USER) >> true
        securityService.isUserQualifiedForOperationOnCeilingEntity(adminUser, _, REVIEW) >> true
        securityService.isUserQualifiedForOperationOnCeilingEntity(adminUser, _, ADMIN_USER) >> false

        when:
        remote.securityCheck(pkg(ADMIN_USER, REVIEW), operation)

        then:
        SecurityServiceException e = thrown()
        e.message.contains(ADMIN_USER)

        where:
        operation << [EntityOperationType.UPDATE, EntityOperationType.ADD, EntityOperationType.REMOVE]
    }

    def "the target ceiling authorizes the operation on its own"() {
        given:
        securityService.isUserQualifiedForOperationOnCeilingEntity(adminUser, PermissionType.UPDATE, REVIEW) >> true

        when:
        remote.securityCheck(pkg(REVIEW, REVIEW), EntityOperationType.UPDATE)

        then:
        noExceptionThrown()
    }

    def "a target without its own permission inherits the parent section's permission"() {
        given:
        securityService.doesOperationExistForCeilingEntity(PermissionType.UPDATE, SKU) >> false
        securityService.isUserQualifiedForOperationOnCeilingEntity(adminUser, PermissionType.UPDATE, SKU) >> false
        securityService.isUserQualifiedForOperationOnCeilingEntity(adminUser, PermissionType.UPDATE, REVIEW) >> true

        when:
        remote.securityCheck(pkg(SKU, REVIEW), EntityOperationType.UPDATE)

        then:
        noExceptionThrown()
    }

    def "the explicit security ceiling is checked instead of the persistence ceiling"() {
        given:
        PersistencePackage pp = pkg(SKU, REVIEW)
        pp.setSecurityCeilingEntityFullyQualifiedClassname(ADMIN_USER)
        securityService.doesOperationExistForCeilingEntity(_, ADMIN_USER) >> true
        securityService.isUserQualifiedForOperationOnCeilingEntity(adminUser, _, ADMIN_USER) >> false
        securityService.isUserQualifiedForOperationOnCeilingEntity(adminUser, _, REVIEW) >> true

        when:
        remote.securityCheck(pp, EntityOperationType.UPDATE)

        then:
        thrown(SecurityServiceException)
    }
}
