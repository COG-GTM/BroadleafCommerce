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

import org.broadleafcommerce.common.presentation.client.OperationType
import org.broadleafcommerce.openadmin.dto.Entity
import org.broadleafcommerce.openadmin.dto.OperationTypes
import org.broadleafcommerce.openadmin.dto.PersistencePackage
import org.broadleafcommerce.openadmin.dto.PersistencePerspective
import org.broadleafcommerce.openadmin.dto.Property
import org.broadleafcommerce.openadmin.server.dao.DynamicEntityDao
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType
import org.broadleafcommerce.openadmin.server.security.remote.SecurityVerifier
import org.broadleafcommerce.openadmin.server.service.handler.CustomPersistenceHandler
import org.broadleafcommerce.openadmin.server.service.persistence.PersistenceManagerImpl
import org.broadleafcommerce.openadmin.server.service.persistence.module.PersistenceModule
import org.broadleafcommerce.openadmin.server.service.persistence.module.RecordHelper
import org.broadleafcommerce.common.exception.SecurityServiceException
import spock.lang.Specification

class PersistenceManagerSubPackageSecuritySpec extends Specification {

    interface RecordHelperModule extends PersistenceModule, RecordHelper {}

    PersistenceManagerImpl manager
    SecurityVerifier securityVerifier
    RecordHelperModule module
    CustomPersistenceHandler subHandler
    PersistencePackage root
    PersistencePackage sub
    boolean subHandlerAccepts = false
    boolean subHandlerHandlesSecurity = false

    def setup() {
        securityVerifier = Mock(SecurityVerifier)
        module = Mock(RecordHelperModule)
        module.isCompatible(_) >> true
        module.add(_) >> savedEntity()
        module.update(_) >> savedEntity()
        subHandler = Mock(CustomPersistenceHandler)
        subHandler.canHandleAdd(_) >> { PersistencePackage pp -> pp.is(sub) && subHandlerAccepts }
        subHandler.canHandleUpdate(_) >> { PersistencePackage pp -> pp.is(sub) && subHandlerAccepts }
        subHandler.willHandleSecurity(_) >> { subHandlerHandlesSecurity }

        manager = new PersistenceManagerImpl()
        manager.adminRemoteSecurityService = securityVerifier
        manager.dynamicEntityDao = Mock(DynamicEntityDao) {
            getIdMetadata(_) >> [name: 'id']
        }
        manager.modules = [module] as PersistenceModule[]
        manager.persistenceManagerEventHandlers = []
        manager.customPersistenceHandlers = [subHandler]

        root = newPackage('com.example.Product', 'com.example.Product', null)
        sub = newPackage('com.example.AdminPermission', 'com.example.AdminPermission',
                ['createNewPermission', null] as String[])
        root.subPackages.put('x', sub)
    }

    def "sub-package update handled by a custom handler is rejected when the security check fails"() {
        given:
        subHandlerAccepts = true
        securityVerifier.securityCheck(sub, EntityOperationType.UPDATE) >> {
            throw new SecurityServiceException('denied')
        }

        when:
        manager.update(root)

        then:
        thrown(SecurityServiceException)
        0 * subHandler.update(*_)
    }

    def "sub-package add handled by a custom handler is rejected when the security check fails"() {
        given:
        subHandlerAccepts = true
        securityVerifier.securityCheck(sub, EntityOperationType.ADD) >> {
            throw new SecurityServiceException('denied')
        }

        when:
        manager.add(root)

        then:
        thrown(SecurityServiceException)
        0 * subHandler.add(*_)
    }

    def "sub-package update handled by a custom handler runs after the security check passes"() {
        given:
        subHandlerAccepts = true

        when:
        manager.update(root)

        then:
        1 * securityVerifier.securityCheck(root, EntityOperationType.UPDATE)
        1 * securityVerifier.securityCheck(sub, EntityOperationType.UPDATE)
        1 * subHandler.update(sub, _, _) >> savedEntity()
    }

    def "custom handlers that handle their own security are not double checked"() {
        given:
        subHandlerAccepts = true
        subHandlerHandlesSecurity = true

        when:
        manager.update(root)

        then:
        1 * securityVerifier.securityCheck(root, EntityOperationType.UPDATE)
        0 * securityVerifier.securityCheck(sub, _)
        1 * subHandler.update(sub, _, _) >> savedEntity()
    }

    def "sub-package update without a custom handler is security checked and persisted as the sub-package"() {
        when:
        manager.update(root)

        then:
        1 * securityVerifier.securityCheck(root, EntityOperationType.UPDATE)
        1 * securityVerifier.securityCheck(sub, EntityOperationType.UPDATE)
        1 * module.update(root) >> savedEntity()
        1 * module.update(sub) >> savedEntity()
    }

    def "sub-package add without a custom handler is security checked and persisted as the sub-package"() {
        when:
        manager.add(root)

        then:
        1 * securityVerifier.securityCheck(root, EntityOperationType.ADD)
        1 * securityVerifier.securityCheck(sub, EntityOperationType.ADD)
        1 * module.add(root) >> savedEntity()
        1 * module.add(sub) >> savedEntity()
    }

    private static PersistencePackage newPackage(String ceiling, String securityCeiling, String[] criteria) {
        PersistencePackage pp = new PersistencePackage()
        pp.ceilingEntityFullyQualifiedClassname = ceiling
        pp.securityCeilingEntityFullyQualifiedClassname = securityCeiling
        pp.customCriteria = criteria
        pp.persistencePerspective = new PersistencePerspective()
        pp.persistencePerspective.operationTypes = new OperationTypes(
                OperationType.BASIC, OperationType.BASIC, OperationType.BASIC, OperationType.BASIC, OperationType.BASIC)
        pp.entity = new Entity()
        return pp
    }

    private static Entity savedEntity() {
        Entity entity = new Entity()
        entity.type = ['java.lang.Object'] as String[]
        Property id = new Property()
        id.name = 'id'
        id.value = '1'
        entity.properties = [id] as Property[]
        return entity
    }
}
