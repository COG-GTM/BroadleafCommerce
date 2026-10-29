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
package org.broadleafcommerce.openadmin.web.controller

import org.broadleafcommerce.common.exception.SecurityServiceException
import org.broadleafcommerce.common.i18n.domain.TranslatedEntity
import org.broadleafcommerce.common.i18n.domain.Translation
import org.broadleafcommerce.common.i18n.service.TranslationService
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType
import org.broadleafcommerce.openadmin.server.security.remote.SecurityVerifier
import org.broadleafcommerce.openadmin.web.form.TranslationForm
import org.broadleafcommerce.openadmin.web.form.entity.EntityForm
import org.broadleafcommerce.openadmin.web.form.entity.Field
import spock.lang.Specification

class AdminTranslationControllerSpec extends Specification {

    static final String PRODUCT = "org.broadleafcommerce.core.catalog.domain.Product"
    static final String CATEGORY = "org.broadleafcommerce.core.catalog.domain.Category"

    AdminTranslationController controller
    TranslationService translationService
    SecurityVerifier securityVerifier

    def setup() {
        translationService = Mock(TranslationService)
        securityVerifier = Mock(SecurityVerifier)
        controller = new AdminTranslationController()
        controller.translationService = translationService
        controller.adminRemoteSecurityService = securityVerifier
        translationService.getAssignableEntityType(PRODUCT) >> TranslatedEntity.PRODUCT
        translationService.getAssignableEntityType(CATEGORY) >> TranslatedEntity.CATEGORY
        translationService.getAssignableEntityType("com.example.Unknown") >> {
            throw new IllegalArgumentException("unknown")
        }
    }

    def translation(TranslatedEntity type, String entityId, String fieldName) {
        Translation translation = Mock(Translation)
        translation.getEntityType() >> type
        translation.getEntityId() >> entityId
        translation.getFieldName() >> fieldName
        translation
    }

    def form(String ceilingEntity, Long translationId, String entityId = null, String propertyName = null) {
        new TranslationForm(ceilingEntity: ceilingEntity, translationId: translationId,
                entityId: entityId, propertyName: propertyName)
    }

    def "a category translation cannot be read, updated or deleted by claiming the product ceiling entity"() {
        given:
        translationService.findTranslationById(42L) >> translation(TranslatedEntity.CATEGORY, "2001", "name")

        when:
        controller.checkTranslationAccess(form(PRODUCT, 42L), operation)

        then:
        thrown(SecurityServiceException)
        0 * securityVerifier.securityCheck(_ as String, _)

        where:
        operation << [EntityOperationType.FETCH, EntityOperationType.UPDATE]
    }

    def "the permission check runs against the entity type that owns the translation"() {
        given:
        Translation stored = translation(TranslatedEntity.CATEGORY, "2001", "name")
        translationService.findTranslationById(42L) >> stored

        when:
        def result = controller.checkTranslationAccess(form(CATEGORY, 42L, "2001", "name"), EntityOperationType.UPDATE)

        then:
        1 * securityVerifier.securityCheck(CATEGORY, EntityOperationType.UPDATE)
        result.is(stored)
    }

    def "a denied permission check on the owning entity is propagated"() {
        given:
        translationService.findTranslationById(42L) >> translation(TranslatedEntity.CATEGORY, "2001", "name")
        securityVerifier.securityCheck(CATEGORY, EntityOperationType.UPDATE) >> {
            throw new SecurityServiceException("denied")
        }

        when:
        controller.checkTranslationAccess(form(CATEGORY, 42L), EntityOperationType.UPDATE)

        then:
        thrown(SecurityServiceException)
    }

    def "a translation of a different record or property of the same entity type is rejected"() {
        given:
        translationService.findTranslationById(42L) >> translation(TranslatedEntity.CATEGORY, "2001", "name")

        when:
        controller.checkTranslationAccess(form(CATEGORY, 42L, entityId, propertyName), EntityOperationType.UPDATE)

        then:
        thrown(SecurityServiceException)
        0 * securityVerifier.securityCheck(_ as String, _)

        where:
        entityId | propertyName
        "9999"   | "name"
        "2001"   | "description"
    }

    def "unknown, missing or unidentified translations are rejected"() {
        given:
        translationService.findTranslationById(42L) >> translation(TranslatedEntity.CATEGORY, "2001", "name")
        translationService.findTranslationById(7L) >> null

        when:
        controller.checkTranslationAccess(form(ceilingEntity, translationId), EntityOperationType.UPDATE)

        then:
        thrown(SecurityServiceException)
        0 * securityVerifier.securityCheck(_ as String, _)

        where:
        ceilingEntity         | translationId
        CATEGORY              | 7L
        CATEGORY              | null
        null                  | 42L
        "com.example.Unknown" | 42L
    }

    def "an update cannot rewrite the identifying columns of the translation"() {
        given:
        Translation stored = translation(TranslatedEntity.CATEGORY, "2001", "name")
        EntityForm entityForm = new EntityForm()
        [entityType: TranslatedEntity.PRODUCT.friendlyType, entityId: "1", fieldName: "description"].each { k, v ->
            Field field = new Field()
            field.name = k
            field.value = v
            entityForm.fields.put(k, field)
        }

        when:
        controller.pinTranslationIdentity(entityForm, stored)

        then:
        entityForm.fields.get("entityType").value == TranslatedEntity.CATEGORY.friendlyType
        entityForm.fields.get("entityId").value == "2001"
        entityForm.fields.get("fieldName").value == "name"
    }
}
