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
import org.broadleafcommerce.common.exception.ServiceException
import org.broadleafcommerce.common.presentation.client.PersistencePerspectiveItemType
import org.broadleafcommerce.common.presentation.client.SupportedFieldType
import org.broadleafcommerce.openadmin.dto.AdornedTargetList
import org.broadleafcommerce.openadmin.dto.BasicFieldMetadata
import org.broadleafcommerce.openadmin.dto.Entity
import org.broadleafcommerce.openadmin.dto.PersistencePackage
import org.broadleafcommerce.openadmin.dto.PersistencePerspective
import org.broadleafcommerce.openadmin.dto.Property
import org.broadleafcommerce.openadmin.server.dao.DynamicEntityDao
import org.broadleafcommerce.openadmin.server.service.persistence.EntityTypeValidator
import org.broadleafcommerce.openadmin.server.service.persistence.PersistenceManager
import org.broadleafcommerce.openadmin.server.service.persistence.module.BasicPersistenceModule

import spock.lang.Specification

class EntityTypeValidatorSpec extends Specification {

    PersistencePackage buildPackage(String ceiling, String entityType) {
        Property id = new Property()
        id.setName("id")
        id.setValue("42")
        Entity entity = new Entity()
        entity.setType([entityType] as String[])
        entity.setProperties([id] as Property[])
        PersistencePackage persistencePackage = new PersistencePackage()
        persistencePackage.setCeilingEntityFullyQualifiedClassname(ceiling)
        persistencePackage.setEntity(entity)
        persistencePackage.setPersistencePerspective(new PersistencePerspective())
        persistencePackage
    }

    def "entity type equal to the ceiling entity is allowed"() {
        expect:
        EntityTypeValidator.resolveEntityClass(buildPackage(Number.name, Number.name)) == Number
    }

    def "entity type that is a subtype of the ceiling entity is allowed"() {
        expect:
        EntityTypeValidator.resolveEntityClass(buildPackage(Number.name, Long.name)) == Long
    }

    def "adorned target entity unrelated to the ceiling entity is rejected"() {
        given:
        PersistencePackage persistencePackage = buildPackage(Number.name, String.name)
        AdornedTargetList adornedTargetList = new AdornedTargetList()
        adornedTargetList.setAdornedTargetEntityClassname(CharSequence.name)
        persistencePackage.getPersistencePerspective()
                .addPersistencePerspectiveItem(PersistencePerspectiveItemType.ADORNEDTARGETLIST, adornedTargetList)

        when:
        EntityTypeValidator.resolveEntityClass(persistencePackage)

        then:
        thrown(SecurityServiceException)
    }

    def "entity type unrelated to the ceiling entity is rejected"() {
        when:
        EntityTypeValidator.resolveEntityClass(buildPackage(Number.name, String.name))

        then:
        thrown(SecurityServiceException)
    }

    def "unknown entity type is rejected"() {
        when:
        EntityTypeValidator.validate(buildPackage(Number.name, "com.example.DoesNotExist"))

        then:
        thrown(SecurityServiceException)
    }

    def "BasicPersistenceModule remove rejects an unrelated entity type before retrieving the record"() {
        given:
        BasicFieldMetadata idMetadata = new BasicFieldMetadata()
        idMetadata.setFieldType(SupportedFieldType.ID)
        idMetadata.setSecondaryType(SupportedFieldType.INTEGER)
        DynamicEntityDao dynamicEntityDao = Mock(DynamicEntityDao)
        PersistenceManager persistenceManager = Mock(PersistenceManager)
        persistenceManager.getDynamicEntityDao() >> dynamicEntityDao
        persistenceManager.getPolymorphicEntities(_) >> ([Number] as Class[])
        dynamicEntityDao.getMergedProperties(*_) >> [id: idMetadata]
        BasicPersistenceModule module = new BasicPersistenceModule()
        module.persistenceManager = persistenceManager

        when:
        module.remove(buildPackage(Number.name, String.name))

        then:
        ServiceException e = thrown()
        e.getCause() instanceof SecurityServiceException
        0 * dynamicEntityDao.retrieve(_, _)
        0 * dynamicEntityDao.remove(_)
    }
}
