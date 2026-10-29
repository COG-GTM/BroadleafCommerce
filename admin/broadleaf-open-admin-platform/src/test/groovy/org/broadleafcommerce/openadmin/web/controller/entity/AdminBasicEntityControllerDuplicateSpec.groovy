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
package org.broadleafcommerce.openadmin.web.controller.entity

import org.broadleafcommerce.common.exception.SecurityServiceException
import org.broadleafcommerce.common.persistence.EntityDuplicator
import org.broadleafcommerce.common.service.GenericEntityService
import org.broadleafcommerce.openadmin.dto.ClassMetadata
import org.broadleafcommerce.openadmin.dto.DynamicResultSet
import org.broadleafcommerce.openadmin.dto.Entity
import org.broadleafcommerce.openadmin.server.dao.DynamicEntityDao
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType
import org.broadleafcommerce.openadmin.server.security.remote.SecurityVerifier
import org.broadleafcommerce.openadmin.server.security.service.RowLevelSecurityService
import org.broadleafcommerce.openadmin.server.service.persistence.PersistenceResponse
import org.broadleafcommerce.openadmin.web.form.entity.EntityForm
import org.broadleafcommerce.openadmin.server.service.AdminEntityService
import org.springframework.ui.ExtendedModelMap
import org.springframework.validation.BeanPropertyBindingResult
import spock.lang.Specification

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class AdminBasicEntityControllerDuplicateSpec extends Specification {

    static final String SECTION_KEY = "/offer"
    static final String CEILING = "org.broadleafcommerce.core.offer.domain.Offer"
    static final String SECURITY_CEILING = "org.broadleafcommerce.core.offer.domain.SecuredOffer"

    AdminBasicEntityController controller
    SecurityVerifier securityVerifier = Mock()
    RowLevelSecurityService rowLevelSecurityService = Mock()
    AdminEntityService adminEntityService = Mock()
    EntityDuplicator duplicator = Mock()
    DynamicEntityDao dynamicEntityDao = Mock()
    GenericEntityService genericEntityService = Mock()
    AdminUser adminUser = Mock()
    ClassMetadata cmd = new ClassMetadata()
    HttpServletRequest request = Mock()
    HttpServletResponse response = Mock()
    StringWriter body = new StringWriter()

    def setup() {
        controller = Spy(AdminBasicEntityController)
        controller.adminRemoteSecurityService = securityVerifier
        controller.rowLevelSecurityService = rowLevelSecurityService
        controller.service = adminEntityService
        controller.duplicator = duplicator
        controller.dynamicEntityDao = dynamicEntityDao
        controller.genericEntityService = genericEntityService

        controller.getSectionKey(_) >> SECTION_KEY
        controller.getClassNameForSection(SECTION_KEY) >> CEILING
        controller.getSectionCrumbs(_, _, _) >> []
        controller.getSectionPersistencePackageRequest(CEILING, _, _) >> null
        controller.getContextPath(_) >> "/admin"
        adminEntityService.getClassMetadata(_) >> new PersistenceResponse().withDynamicResultSet(new DynamicResultSet(cmd))
        adminEntityService.getRecord(_, "42", cmd, false) >> readable()
        securityVerifier.getPersistentAdminUser() >> adminUser
        dynamicEntityDao.getImplClass(CEILING) >> Object
        response.getWriter() >> new PrintWriter(body)
    }

    static PersistenceResponse readable() {
        new PersistenceResponse().withDynamicResultSet(new DynamicResultSet([new Entity()] as Entity[], 1))
    }

    def duplicate() {
        controller.duplicateEntity(request, response, new ExtendedModelMap(), [sectionKey: SECTION_KEY], "42",
                new EntityForm(), new BeanPropertyBindingResult(null, "entityForm"))
    }

    def "duplication is rejected when the user lacks the ADD permission on the section"() {
        given:
        securityVerifier.securityCheck(CEILING, EntityOperationType.ADD) >> { throw new SecurityServiceException("denied") }

        when:
        def view = duplicate()

        then:
        view == null
        1 * response.setStatus(HttpServletResponse.SC_FORBIDDEN)
        0 * duplicator.validate(_, _)
        0 * duplicator.copy(_, _)
        body.toString().contains("Duplication_Unauthorized")
    }

    def "duplication is rejected when row level security denies add"() {
        given:
        rowLevelSecurityService.canAdd(adminUser, CEILING, cmd) >> false

        when:
        def view = duplicate()

        then:
        view == null
        1 * response.setStatus(HttpServletResponse.SC_FORBIDDEN)
        0 * duplicator.copy(_, _)
        body.toString().contains("Duplication_Unauthorized")
    }

    def "duplication proceeds when the user may add to the section"() {
        given:
        def copy = new Object()
        rowLevelSecurityService.canAdd(adminUser, CEILING, cmd) >> true
        duplicator.validate(Object, 42L) >> true
        genericEntityService.getIdentifier(copy) >> 43L

        when:
        def view = duplicate()

        then:
        1 * securityVerifier.securityCheck(CEILING, EntityOperationType.ADD)
        1 * duplicator.copy(Object, 42L) >> copy
        0 * response.setStatus(_)
        view == "ajaxredirect:/admin" + SECTION_KEY + "/43"
    }

    def "duplication checks permissions against the section's security ceiling"() {
        given:
        cmd.setSecurityCeilingType(SECURITY_CEILING)
        securityVerifier.securityCheck(SECURITY_CEILING, EntityOperationType.ADD) >> { throw new SecurityServiceException("denied") }
        rowLevelSecurityService.canAdd(adminUser, CEILING, cmd) >> true

        when:
        def view = duplicate()

        then:
        view == null
        1 * response.setStatus(HttpServletResponse.SC_FORBIDDEN)
        0 * duplicator.copy(_, _)
        body.toString().contains("Duplication_Unauthorized")
    }

    def "duplication is rejected when the user cannot read the source record"() {
        given:
        rowLevelSecurityService.canAdd(adminUser, CEILING, cmd) >> true
        duplicator.validate(Object, 42L) >> true

        when:
        def view = duplicate()

        then:
        1 * adminEntityService.getRecord(_, "42", cmd, false) >> { throw new SecurityServiceException("no read") }
        view == null
        1 * response.setStatus(HttpServletResponse.SC_FORBIDDEN)
        0 * duplicator.copy(_, _)
        body.toString().contains("Duplication_Unauthorized")
    }
}
