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
import org.broadleafcommerce.openadmin.dto.Entity
import org.broadleafcommerce.openadmin.dto.PersistencePackage
import org.broadleafcommerce.openadmin.dto.PersistencePerspective
import org.broadleafcommerce.openadmin.dto.SectionCrumb
import org.broadleafcommerce.openadmin.server.security.extension.AdminSecurityCheckExtensionManager
import org.broadleafcommerce.openadmin.server.security.remote.AdminSecurityServiceRemote
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType
import org.broadleafcommerce.openadmin.server.security.service.AdminSecurityService
import org.broadleafcommerce.openadmin.server.security.service.RowLevelSecurityService
import org.broadleafcommerce.openadmin.server.security.service.type.PermissionType
import org.broadleafcommerce.openadmin.server.service.persistence.validation.GlobalValidationResult
import spock.lang.Specification

class AdminSecurityServiceRemoteSpec extends Specification {

    static final String TARGET_ENTITY = "org.broadleafcommerce.core.catalog.domain.Product"
    static final String CRUMB_ENTITY = "org.broadleafcommerce.core.catalog.domain.Category"

    AdminSecurityServiceRemote remoteService
    AdminSecurityService securityService

    def setup() {
        remoteService = new AdminSecurityServiceRemote()
        securityService = Mock(AdminSecurityService)
        remoteService.securityService = securityService
        remoteService.securityCheckExtensionManager = new AdminSecurityCheckExtensionManager()
        remoteService.rowLevelSecurityService = Mock(RowLevelSecurityService) {
            validateUpdateRequest(*_) >> new GlobalValidationResult(true)
        }
    }

    PersistencePackage buildPersistencePackage(String... crumbIdentifiers) {
        PersistencePackage pkg = new PersistencePackage()
        pkg.setCeilingEntityFullyQualifiedClassname(TARGET_ENTITY)
        pkg.setPersistencePerspective(new PersistencePerspective())
        pkg.setEntity(new Entity())
        SectionCrumb[] crumbs = crumbIdentifiers.collect { identifier ->
            SectionCrumb crumb = new SectionCrumb()
            crumb.setSectionIdentifier(identifier)
            crumb.setSectionId("1")
            crumb
        } as SectionCrumb[]
        pkg.setSectionCrumbs(crumbs)
        return pkg
    }

    def "permission on a section crumb entity does not authorize an operation on the target entity"() {
        given: "a user qualified only on the crumb entity, not the target entity"
        securityService.isUserQualifiedForOperationOnCeilingEntity(_, PermissionType.UPDATE, TARGET_ENTITY) >> false
        securityService.isUserQualifiedForOperationOnCeilingEntity(_, PermissionType.UPDATE, CRUMB_ENTITY) >> true
        securityService.doesOperationExistForCeilingEntity(*_) >> true

        when:
        remoteService.securityCheck(buildPersistencePackage(CRUMB_ENTITY), EntityOperationType.UPDATE)

        then:
        thrown(SecurityServiceException)
    }

    def "permission on the target security ceiling entity authorizes the operation"() {
        given:
        securityService.isUserQualifiedForOperationOnCeilingEntity(_, PermissionType.UPDATE, TARGET_ENTITY) >> true

        when:
        remoteService.securityCheck(buildPersistencePackage(CRUMB_ENTITY), EntityOperationType.UPDATE)

        then:
        noExceptionThrown()
    }
}
