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
package org.broadleafcommerce.openadmin.server.security.handler

import org.broadleafcommerce.common.exception.SecurityServiceException
import org.broadleafcommerce.common.persistence.Status
import org.broadleafcommerce.common.presentation.client.OperationType
import org.broadleafcommerce.openadmin.dto.Entity
import org.broadleafcommerce.openadmin.dto.OperationTypes
import org.broadleafcommerce.openadmin.dto.PersistencePackage
import org.broadleafcommerce.openadmin.dto.PersistencePerspective
import org.broadleafcommerce.openadmin.dto.Property
import org.broadleafcommerce.openadmin.server.dao.DynamicEntityDao
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser
import org.broadleafcommerce.openadmin.server.security.domain.AdminUserImpl
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType
import org.broadleafcommerce.openadmin.server.security.remote.SecurityVerifier
import org.broadleafcommerce.openadmin.server.security.service.AdminSecurityService
import org.broadleafcommerce.openadmin.server.service.ValidationException
import org.broadleafcommerce.openadmin.server.service.persistence.module.PersistenceModule
import org.broadleafcommerce.openadmin.server.service.persistence.module.RecordHelper
import spock.lang.Specification

class AdminUserCustomPersistenceHandlerSpec extends Specification {

    static class ArchivableAdminUser extends AdminUserImpl implements Status {
        Character archived = 'N'

        @Override
        boolean isActive() {
            archived != 'Y' as Character
        }
    }

    AdminUserCustomPersistenceHandler handler
    AdminSecurityService adminSecurityService = Mock()
    SecurityVerifier securityVerifier = Mock()
    RecordHelper helper = Mock()
    PersistenceModule basicModule = Mock()
    DynamicEntityDao dynamicEntityDao = Mock()

    def setup() {
        handler = new AdminUserCustomPersistenceHandler()
        handler.adminSecurityService = adminSecurityService
        handler.adminRemoteSecurityService = securityVerifier
    }

    def "remove without REMOVE permission is rejected before any user is loaded or changed"() {
        given:
        PersistencePackage pkg = removePackage("2", "superadmin")

        when:
        handler.remove(pkg, dynamicEntityDao, helper)

        then:
        1 * securityVerifier.securityCheck(pkg, EntityOperationType.REMOVE) >> {
            throw new SecurityServiceException("Security Check Failed for entity operation: REMOVE")
        }
        thrown(SecurityServiceException)
        0 * adminSecurityService._
        0 * helper._
    }

    def "remove with REMOVE permission archives the target user when Status is woven in"() {
        given:
        PersistencePackage pkg = removePackage("2", "superadmin")
        ArchivableAdminUser target = (ArchivableAdminUser) user(2L, "superadmin", new ArchivableAdminUser())

        when:
        handler.remove(pkg, dynamicEntityDao, helper)

        then:
        1 * securityVerifier.securityCheck(pkg, EntityOperationType.REMOVE)
        securityVerifier.getPersistentAdminUser() >> user(1L, "editor")
        1 * adminSecurityService.readAdminUserById(2L) >> target
        1 * adminSecurityService.saveAdminUser(target)
        target.archived == 'Y' as Character
        0 * helper._
    }

    def "remove with REMOVE permission delegates to the basic module when Status is not woven in"() {
        given:
        PersistencePackage pkg = removePackage("2", "superadmin")

        when:
        handler.remove(pkg, dynamicEntityDao, helper)

        then:
        1 * securityVerifier.securityCheck(pkg, EntityOperationType.REMOVE)
        securityVerifier.getPersistentAdminUser() >> user(1L, "editor")
        1 * adminSecurityService.readAdminUserById(2L) >> user(2L, "superadmin")
        1 * helper.getCompatibleModule(OperationType.BASIC) >> basicModule
        1 * basicModule.remove(pkg)
    }

    def "current user cannot remove themselves even when the login field is omitted or spoofed"() {
        given:
        PersistencePackage pkg = removePackage("1", submittedLogin)

        when:
        handler.remove(pkg, dynamicEntityDao, helper)

        then:
        1 * securityVerifier.securityCheck(pkg, EntityOperationType.REMOVE)
        securityVerifier.getPersistentAdminUser() >> user(1L, "editor")
        adminSecurityService.readAdminUserById(1L) >> user(1L, "editor", new ArchivableAdminUser())
        thrown(ValidationException)
        0 * adminSecurityService.saveAdminUser(_)
        0 * helper._

        where:
        submittedLogin << [null, "editor", "someone-else"]
    }

    private static PersistencePackage removePackage(String id, String login) {
        Entity entity = new Entity()
        entity.setType([AdminUserImpl.name] as String[])
        List<Property> properties = [new Property("id", id)]
        if (login != null) {
            properties << new Property("login", login)
        }
        entity.setProperties(properties as Property[])

        PersistencePerspective perspective = new PersistencePerspective()
        perspective.setOperationTypes(new OperationTypes(
                OperationType.BASIC, OperationType.BASIC, OperationType.BASIC, OperationType.BASIC, OperationType.BASIC
        ))

        PersistencePackage pkg = new PersistencePackage()
        pkg.setCeilingEntityFullyQualifiedClassname(AdminUser.name)
        pkg.setEntity(entity)
        pkg.setPersistencePerspective(perspective)
        return pkg
    }

    private static AdminUser user(Long id, String login, AdminUser instance = new AdminUserImpl()) {
        instance.setId(id)
        instance.setLogin(login)
        return instance
    }
}
