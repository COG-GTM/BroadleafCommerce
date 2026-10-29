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
import org.broadleafcommerce.openadmin.server.security.domain.AdminPermission
import org.broadleafcommerce.openadmin.server.security.domain.AdminPermissionImpl
import org.broadleafcommerce.openadmin.server.security.domain.AdminUserImpl
import org.broadleafcommerce.openadmin.server.service.persistence.module.BasicPersistenceModule
import spock.lang.Specification

class BasicPersistenceModuleEntityTypeSpec extends Specification {

    BasicPersistenceModule module = new BasicPersistenceModule()

    PersistencePackage pkg(String ceiling, String type) {
        PersistencePackage pp = new PersistencePackage()
        pp.ceilingEntityFullyQualifiedClassname = ceiling
        pp.entity = new Entity(type: type == null ? null : [type] as String[])
        pp
    }

    def "entity types inside the ceiling hierarchy are accepted"() {
        when:
        module.validateEntityTypeForCeiling(pkg(ceiling, type), [AdminPermissionImpl] as Class[])

        then:
        noExceptionThrown()

        where:
        ceiling                  | type
        AdminPermission.name     | AdminPermission.name
        AdminPermission.name     | AdminPermissionImpl.name
        AdminPermissionImpl.name | AdminPermissionImpl.name
    }

    def "an entity type outside the ceiling hierarchy is rejected"() {
        when:
        module.validateEntityTypeForCeiling(pkg(AdminPermission.name, AdminUserImpl.name), [AdminPermissionImpl] as Class[])

        then:
        thrown(SecurityServiceException)
    }

    def "missing or unknown entity types are rejected"() {
        when:
        module.validateEntityTypeForCeiling(pkg(AdminPermission.name, type), [AdminPermissionImpl] as Class[])

        then:
        thrown(SecurityServiceException)

        where:
        type << [null, "", "com.example.DoesNotExist"]
    }
}
