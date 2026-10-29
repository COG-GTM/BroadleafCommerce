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
import org.broadleafcommerce.openadmin.dto.Entity
import org.broadleafcommerce.openadmin.dto.PersistencePackage
import org.broadleafcommerce.openadmin.dto.Property
import org.broadleafcommerce.openadmin.server.dao.DynamicEntityDao
import org.broadleafcommerce.openadmin.server.security.domain.AdminPermission
import org.broadleafcommerce.openadmin.server.security.domain.AdminPermissionImpl
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType
import org.broadleafcommerce.openadmin.server.security.remote.SecurityVerifier
import org.broadleafcommerce.openadmin.server.service.persistence.module.RecordHelper
import spock.lang.Specification

class AdminPermissionCustomPersistenceHandlerSpec extends Specification {

    SecurityVerifier securityVerifier = Mock()
    DynamicEntityDao dynamicEntityDao = Mock()
    RecordHelper helper = Mock()
    AdminPermissionCustomPersistenceHandler handler

    def setup() {
        handler = new AdminPermissionCustomPersistenceHandler()
        handler.adminRemoteSecurityService = securityVerifier
    }

    def "add carrying an existing id is rejected when the caller lacks UPDATE"() {
        given:
        PersistencePackage pkg = permissionPackage("42")

        when:
        handler.add(pkg, dynamicEntityDao, helper)

        then:
        1 * securityVerifier.securityCheck(pkg, EntityOperationType.UPDATE) >> {
            throw new SecurityServiceException("Security Check Failed for entity operation: UPDATE")
        }
        0 * dynamicEntityDao.retrieve(*_)
        0 * dynamicEntityDao.merge(_)
        thrown(SecurityServiceException)
    }

    def "add carrying an existing id updates the record when the caller has UPDATE"() {
        given:
        PersistencePackage pkg = permissionPackage("42")

        when:
        handler.add(pkg, dynamicEntityDao, helper)

        then:
        1 * securityVerifier.securityCheck(pkg, EntityOperationType.UPDATE)

        then:
        1 * dynamicEntityDao.retrieve(AdminPermissionImpl, _)
        1 * dynamicEntityDao.merge(_)
    }

    def "add without an id creates a record without requiring UPDATE"() {
        given:
        PersistencePackage pkg = permissionPackage(null)

        when:
        handler.add(pkg, dynamicEntityDao, helper)

        then:
        0 * securityVerifier.securityCheck(*_)
        0 * dynamicEntityDao.retrieve(*_)
        1 * dynamicEntityDao.merge(_)
    }

    def "add with a blank id is treated as a create"() {
        given:
        PersistencePackage pkg = permissionPackage("")

        when:
        handler.add(pkg, dynamicEntityDao, helper)

        then:
        0 * securityVerifier.securityCheck(*_)
        0 * dynamicEntityDao.retrieve(*_)
        1 * dynamicEntityDao.merge(_)
    }

    PersistencePackage permissionPackage(String id) {
        List<Property> properties = [property("name", "PERMISSION_ALL_PRODUCT"), property("type", "ALL")]
        if (id != null) {
            properties << property("id", id)
        }
        Entity entity = new Entity()
        entity.type = [AdminPermissionImpl.name] as String[]
        entity.properties = properties as Property[]

        PersistencePackage pkg = new PersistencePackage()
        pkg.ceilingEntityFullyQualifiedClassname = AdminPermission.name
        pkg.customCriteria = ["createNewPermission"] as String[]
        pkg.entity = entity
        return pkg
    }

    Property property(String name, String value) {
        Property property = new Property()
        property.name = name
        property.value = value
        return property
    }
}
