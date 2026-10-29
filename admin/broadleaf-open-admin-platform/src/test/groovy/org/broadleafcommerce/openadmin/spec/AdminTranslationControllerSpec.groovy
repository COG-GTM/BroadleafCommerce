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
import org.broadleafcommerce.common.i18n.domain.TranslatedEntity
import org.broadleafcommerce.common.i18n.domain.Translation
import org.broadleafcommerce.common.i18n.service.TranslationService
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType
import org.broadleafcommerce.openadmin.server.security.remote.SecurityVerifier
import org.broadleafcommerce.openadmin.web.controller.AdminTranslationController
import org.broadleafcommerce.openadmin.web.form.TranslationForm
import spock.lang.Specification

class AdminTranslationControllerSpec extends Specification {

    static final String PRODUCT = TranslatedEntity.PRODUCT.type
    static final String PAGE = TranslatedEntity.PAGE.type

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
        translationService.getAssignableEntityType(PAGE) >> TranslatedEntity.PAGE
        translationService.getAssignableEntityType("com.example.Unknown") >> {
            throw new IllegalArgumentException("unknown")
        }
    }

    def translationOf(TranslatedEntity type) {
        Translation translation = Mock(Translation)
        translation.getEntityType() >> type
        translation
    }

    def form(String ceilingEntity, Long translationId) {
        new TranslationForm(ceilingEntity: ceilingEntity, translationId: translationId)
    }

    def "a page translation cannot be changed by claiming the product ceiling entity"() {
        given:
        translationService.findTranslationById(42L) >> translationOf(TranslatedEntity.PAGE)

        when:
        controller.checkTranslationAccess(form(PRODUCT, 42L), operation)

        then:
        thrown(SecurityServiceException)
        0 * securityVerifier.securityCheck(_ as String, _)

        where:
        operation << [EntityOperationType.FETCH, EntityOperationType.UPDATE]
    }

    def "the permission check runs against the ceiling entity that owns the translation"() {
        given:
        Translation translation = translationOf(TranslatedEntity.PAGE)
        translationService.findTranslationById(42L) >> translation

        when:
        def result = controller.checkTranslationAccess(form(PAGE, 42L), EntityOperationType.UPDATE)

        then:
        1 * securityVerifier.securityCheck(PAGE, EntityOperationType.UPDATE)
        result.is(translation)
    }

    def "a denied permission check on the owning entity is propagated"() {
        given:
        translationService.findTranslationById(42L) >> translationOf(TranslatedEntity.PAGE)
        securityVerifier.securityCheck(PAGE, EntityOperationType.UPDATE) >> {
            throw new SecurityServiceException("denied")
        }

        when:
        controller.checkTranslationAccess(form(PAGE, 42L), EntityOperationType.UPDATE)

        then:
        thrown(SecurityServiceException)
    }

    def "unknown, missing or unidentified translations are rejected"() {
        given:
        translationService.findTranslationById(42L) >> translationOf(TranslatedEntity.PAGE)
        translationService.findTranslationById(7L) >> null

        when:
        controller.checkTranslationAccess(form(ceilingEntity, translationId), EntityOperationType.UPDATE)

        then:
        thrown(SecurityServiceException)
        0 * securityVerifier.securityCheck(_ as String, _)

        where:
        ceilingEntity          | translationId
        PAGE                   | 7L
        PAGE                   | null
        null                   | 42L
        "com.example.Unknown"  | 42L
    }
}
