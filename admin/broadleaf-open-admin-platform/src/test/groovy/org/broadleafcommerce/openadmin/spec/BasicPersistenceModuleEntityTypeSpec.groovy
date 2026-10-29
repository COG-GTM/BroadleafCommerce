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
import org.broadleafcommerce.openadmin.server.service.persistence.module.BasicPersistenceModule
import spock.lang.Specification

class BasicPersistenceModuleEntityTypeSpec extends Specification {

    BasicPersistenceModule module = new BasicPersistenceModule()

    PersistencePackage pkg(String ceiling, String type) {
        PersistencePackage pp = new PersistencePackage()
        pp.setCeilingEntityFullyQualifiedClassname(ceiling)
        Entity entity = new Entity()
        entity.setType([type] as String[])
        pp.setEntity(entity)
        return pp
    }

    def "an entity type inside the ceiling hierarchy is accepted"() {
        when:
        module.validateEntityTypeForCeiling(pkg(ceiling, type), [] as Class[])

        then:
        noExceptionThrown()

        where:
        ceiling             | type
        "java.lang.Number"  | "java.lang.Number"
        "java.lang.Number"  | "java.lang.Integer"
    }

    def "a listed polymorphic entity is accepted"() {
        when:
        module.validateEntityTypeForCeiling(pkg("java.lang.Number", "java.lang.Long"), [Long] as Class[])

        then:
        noExceptionThrown()
    }

    def "an entity type outside the ceiling hierarchy is rejected"() {
        when:
        module.validateEntityTypeForCeiling(pkg("java.lang.Number", "java.lang.String"), [Integer] as Class[])

        then:
        thrown(SecurityServiceException)
    }
}
