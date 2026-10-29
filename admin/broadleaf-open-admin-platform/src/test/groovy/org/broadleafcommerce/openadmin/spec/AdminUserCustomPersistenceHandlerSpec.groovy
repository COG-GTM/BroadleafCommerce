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
import org.broadleafcommerce.common.persistence.Status
import org.broadleafcommerce.common.presentation.client.OperationType
import org.broadleafcommerce.openadmin.dto.Entity
import org.broadleafcommerce.openadmin.dto.OperationTypes
import org.broadleafcommerce.openadmin.dto.PersistencePackage
import org.broadleafcommerce.openadmin.dto.PersistencePerspective
import org.broadleafcommerce.openadmin.dto.Property
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser
import org.broadleafcommerce.openadmin.server.security.handler.AdminUserCustomPersistenceHandler
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType
import org.broadleafcommerce.openadmin.server.security.remote.SecurityVerifier
import org.broadleafcommerce.openadmin.server.security.service.AdminSecurityService
import org.broadleafcommerce.openadmin.server.service.ValidationException
import org.broadleafcommerce.openadmin.server.service.persistence.module.PersistenceModule
import org.broadleafcommerce.openadmin.server.service.persistence.module.RecordHelper
import spock.lang.Specification

class AdminUserCustomPersistenceHandlerSpec extends Specification {

    AdminUserCustomPersistenceHandler handler
    SecurityVerifier securityVerifier = Mock()
    AdminSecurityService adminSecurityService = Mock()
    RecordHelper helper = Mock()
    PersistenceModule basicModule = Mock()
    AdminUser currentUser = Mock()

    def setup() {
        handler = new AdminUserCustomPersistenceHandler()
        handler.adminRemoteSecurityService = securityVerifier
        handler.adminSecurityService = adminSecurityService
        currentUser.getId() >> 1L
        currentUser.getLogin() >> "mallory"
        securityVerifier.getPersistentAdminUser() >> currentUser
        helper.getCompatibleModule(_) >> basicModule
    }

    def "remove runs the REMOVE security check before touching the target user"() {
        given:
        PersistencePackage pkg = removePackage("2", "alice")
        securityVerifier.securityCheck(pkg, EntityOperationType.REMOVE) >> {
            throw new SecurityServiceException("Security Check Failed")
        }

        when:
        handler.remove(pkg, null, helper)

        then:
        thrown(SecurityServiceException)
        0 * adminSecurityService.readAdminUserById(_)
        0 * adminSecurityService.saveAdminUser(_)
        0 * basicModule.remove(_)
    }

    def "remove of another user proceeds after the security check passes"() {
        given:
        PersistencePackage pkg = removePackage("2", "alice")
        AdminUser target = Mock()
        target.getId() >> 2L
        target.getLogin() >> "alice"

        when:
        handler.remove(pkg, null, helper)

        then:
        1 * securityVerifier.securityCheck(pkg, EntityOperationType.REMOVE)

        then:
        1 * adminSecurityService.readAdminUserById(2L) >> target
        1 * basicModule.remove(pkg)
    }

    def "remove archives the target when Status is weaved in, after the security check"() {
        given:
        PersistencePackage pkg = removePackage("2", "alice")
        AdminUser target = Mock(AdminUser, additionalInterfaces: [Status])
        target.getId() >> 2L
        target.getLogin() >> "alice"

        when:
        handler.remove(pkg, null, helper)

        then:
        1 * securityVerifier.securityCheck(pkg, EntityOperationType.REMOVE)

        then:
        1 * adminSecurityService.readAdminUserById(2L) >> target
        1 * ((Status) target).setArchived('Y' as char)
        1 * adminSecurityService.saveAdminUser(target)
        0 * basicModule.remove(_)
    }

    def "current user cannot remove themselves even when the login field is omitted or spoofed"() {
        given:
        PersistencePackage pkg = removePackage("1", login)
        AdminUser self = Mock()
        self.getId() >> 1L
        self.getLogin() >> "mallory"
        adminSecurityService.readAdminUserById(1L) >> self

        when:
        handler.remove(pkg, null, helper)

        then:
        1 * securityVerifier.securityCheck(pkg, EntityOperationType.REMOVE)
        thrown(ValidationException)
        0 * adminSecurityService.saveAdminUser(_)
        0 * basicModule.remove(_)

        where:
        login << [null, "someone-else", "mallory"]
    }

    private static PersistencePackage removePackage(String id, String login) {
        Entity entity = new Entity()
        List<Property> props = [new Property("id", id)]
        if (login != null) {
            props << new Property("login", login)
        }
        entity.setProperties(props as Property[])
        entity.setType([AdminUser.class.getName()] as String[])

        PersistencePerspective perspective = new PersistencePerspective()
        perspective.setOperationTypes(new OperationTypes(OperationType.BASIC, OperationType.BASIC,
                OperationType.BASIC, OperationType.BASIC, OperationType.BASIC))

        PersistencePackage pkg = new PersistencePackage()
        pkg.setCeilingEntityFullyQualifiedClassname(AdminUser.class.getName())
        pkg.setEntity(entity)
        pkg.setPersistencePerspective(perspective)
        return pkg
    }
}
