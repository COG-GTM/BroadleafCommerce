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

import org.broadleafcommerce.common.exception.ServiceException
import org.broadleafcommerce.common.persistence.Status
import org.broadleafcommerce.openadmin.dto.Entity
import org.broadleafcommerce.openadmin.dto.PersistencePackage
import org.broadleafcommerce.openadmin.dto.Property
import org.broadleafcommerce.openadmin.server.dao.DynamicEntityDao
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser
import org.broadleafcommerce.openadmin.server.security.handler.AdminUserCustomPersistenceHandler
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType
import org.broadleafcommerce.openadmin.server.security.remote.SecurityVerifier
import org.broadleafcommerce.openadmin.server.security.service.AdminSecurityService
import org.broadleafcommerce.openadmin.server.service.persistence.module.RecordHelper

import spock.lang.Specification

/**
 * Verifies that {@link AdminUserCustomPersistenceHandler#remove} enforces the REMOVE
 * authorization check before archiving/deleting an admin user. Because the handler
 * overrides {@code willHandleSecurity()} to return true, the framework skips its own
 * securityCheck and the handler is solely responsible for enforcing DELETE permission.
 */
class AdminUserCustomPersistenceHandlerSpec extends Specification {

    AdminUserCustomPersistenceHandler handler
    AdminSecurityService adminSecurityService
    SecurityVerifier adminRemoteSecurityService
    DynamicEntityDao dynamicEntityDao
    RecordHelper helper

    def setup() {
        handler = new AdminUserCustomPersistenceHandler()
        adminSecurityService = Mock(AdminSecurityService)
        adminRemoteSecurityService = Mock(SecurityVerifier)
        dynamicEntityDao = Mock(DynamicEntityDao)
        helper = Mock(RecordHelper)
        handler.adminSecurityService = adminSecurityService
        handler.adminRemoteSecurityService = adminRemoteSecurityService
    }

    private static PersistencePackage packageForTarget(String id, String login) {
        Entity entity = new Entity()
        entity.setProperties([new Property("id", id), new Property("login", login)] as Property[])
        PersistencePackage pkg = new PersistencePackage()
        pkg.setEntity(entity)
        return pkg
    }

    def "remove without DELETE permission is blocked before any deletion occurs"() {
        given: "a request to delete another admin user"
        PersistencePackage pkg = packageForTarget("5", "target-admin")

        when: "the caller lacks REMOVE permission on AdminUser"
        handler.remove(pkg, dynamicEntityDao, helper)

        then: "the framework security check is invoked and rejects the operation"
        1 * adminRemoteSecurityService.securityCheck(pkg, EntityOperationType.REMOVE) >> {
            throw new ServiceException("Security check failed")
        }

        and: "the exception propagates and no archive/delete is performed"
        thrown(ServiceException)
        0 * adminSecurityService.readAdminUserById(_)
        0 * adminSecurityService.saveAdminUser(_)
    }

    def "remove with DELETE permission archives the target admin user"() {
        given: "a request to delete another admin user"
        PersistencePackage pkg = packageForTarget("5", "target-admin")

        and: "the target admin instance supports soft-delete via Status"
        AdminUser adminInstance = Mock(AdminUser, additionalInterfaces: [Status])
        AdminUser currentUser = Mock(AdminUser)

        when:
        handler.remove(pkg, dynamicEntityDao, helper)

        then: "authorization is checked first"
        1 * adminRemoteSecurityService.securityCheck(pkg, EntityOperationType.REMOVE)

        and: "the caller is not deleting themselves"
        1 * adminRemoteSecurityService.getPersistentAdminUser() >> currentUser
        currentUser.getLogin() >> "current-admin"

        and: "the target is soft-deleted"
        1 * adminSecurityService.readAdminUserById(5L) >> adminInstance
        1 * ((Status) adminInstance).setArchived('Y' as Character)
        1 * adminSecurityService.saveAdminUser(adminInstance)
    }
}
