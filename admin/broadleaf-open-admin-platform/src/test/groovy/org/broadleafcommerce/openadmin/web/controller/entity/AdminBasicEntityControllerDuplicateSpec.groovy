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
import org.broadleafcommerce.openadmin.dto.SectionCrumb
import org.broadleafcommerce.openadmin.exception.EntityNotFoundException
import org.broadleafcommerce.openadmin.server.dao.DynamicEntityDao
import org.broadleafcommerce.openadmin.server.domain.PersistencePackageRequest
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType
import org.broadleafcommerce.openadmin.server.security.remote.SecurityVerifier
import org.broadleafcommerce.openadmin.server.security.service.RowLevelSecurityService
import org.broadleafcommerce.openadmin.server.service.AdminEntityService
import org.broadleafcommerce.openadmin.server.service.persistence.PersistenceResponse
import org.broadleafcommerce.openadmin.web.form.entity.EntityForm
import org.springframework.ui.ExtendedModelMap
import org.springframework.validation.BindingResult

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import spock.lang.Specification

class AdminBasicEntityControllerDuplicateSpec extends Specification {

    static final String SECTION_KEY = "/offer"
    static final String OFFER_CLASS = "org.broadleafcommerce.core.offer.domain.Offer"
    static final String OFFER_IMPL = "org.broadleafcommerce.core.offer.domain.OfferImpl"

    AdminEntityService service = Mock()
    SecurityVerifier securityVerifier = Mock()
    RowLevelSecurityService rowLevelSecurityService = Mock()
    EntityDuplicator duplicator = Mock()
    DynamicEntityDao dynamicEntityDao = Mock()
    GenericEntityService genericEntityService = Mock()

    HttpServletRequest request = Mock()
    HttpServletResponse response = Mock()
    StringWriter body = new StringWriter()

    AdminUser adminUser = Mock()
    ClassMetadata cmd = new ClassMetadata()
    PersistencePackageRequest ppr = new PersistencePackageRequest()

    AdminBasicEntityController controller

    def setup() {
        controller = new AdminBasicEntityController() {
            @Override
            protected String getSectionKey(Map<String, String> pathVars) {
                return SECTION_KEY
            }

            @Override
            protected String getClassNameForSection(String sectionKey) {
                return OFFER_CLASS
            }

            @Override
            protected List<SectionCrumb> getSectionCrumbs(HttpServletRequest req, String section, String id) {
                return []
            }

            @Override
            protected PersistencePackageRequest getSectionPersistencePackageRequest(
                    String sectionClassName, List<SectionCrumb> crumbs, Map<String, String> pathVars) {
                return ppr
            }

            @Override
            protected String getContextPath(HttpServletRequest req) {
                return "/admin"
            }
        }
        controller.service = service
        controller.adminRemoteSecurityService = securityVerifier
        controller.rowLevelSecurityService = rowLevelSecurityService
        controller.duplicator = duplicator
        controller.dynamicEntityDao = dynamicEntityDao
        controller.genericEntityService = genericEntityService

        response.getWriter() >> new PrintWriter(body)
        securityVerifier.getPersistentAdminUser() >> adminUser
        service.getClassMetadata(ppr) >> new PersistenceResponse()
                .withDynamicResultSet(new DynamicResultSet(cmd))
        dynamicEntityDao.getImplClass(OFFER_CLASS) >> Class.forName("java.lang.Object")
    }

    def duplicate(String id = "15") {
        return controller.duplicateEntity(request, response, new ExtendedModelMap(), [:], id,
                new EntityForm(), Mock(BindingResult))
    }

    void givenSourceRecordReadable() {
        service.getRecord(ppr, "15", cmd, false) >> new PersistenceResponse()
                .withDynamicResultSet(new DynamicResultSet([new Entity()] as Entity[], 1))
    }

    def "an admin with ADD, FETCH and row-level add rights can duplicate"() {
        given:
        rowLevelSecurityService.canAdd(adminUser, OFFER_CLASS, cmd) >> true
        givenSourceRecordReadable()
        def copy = new Object()

        when:
        def view = duplicate()

        then:
        1 * securityVerifier.securityCheck(OFFER_CLASS, EntityOperationType.ADD)
        1 * securityVerifier.securityCheck(OFFER_CLASS, EntityOperationType.FETCH)
        1 * duplicator.validate(Object, 15L) >> true
        1 * duplicator.copy(Object, 15L) >> copy
        1 * genericEntityService.getIdentifier(copy) >> 16L
        view == "ajaxredirect:/admin/offer/16"
        0 * response.setStatus(_)
    }

    def "missing ADD permission is rejected with 403 before anything is copied"() {
        given:
        securityVerifier.securityCheck(OFFER_CLASS, EntityOperationType.ADD) >> {
            throw new SecurityServiceException("no add")
        }

        when:
        def view = duplicate()

        then:
        view == null
        1 * response.setStatus(HttpServletResponse.SC_FORBIDDEN)
        0 * duplicator.validate(*_)
        0 * duplicator.copy(*_)
        body.toString().contains("Duplication_Unauthorized")
    }

    def "missing FETCH permission on the source is rejected"() {
        given:
        securityVerifier.securityCheck(OFFER_CLASS, EntityOperationType.FETCH) >> {
            throw new SecurityServiceException("no fetch")
        }

        when:
        duplicate()

        then:
        1 * response.setStatus(HttpServletResponse.SC_FORBIDDEN)
        0 * duplicator.copy(*_)
    }

    def "failing the row-level add check is rejected"() {
        given:
        rowLevelSecurityService.canAdd(adminUser, OFFER_CLASS, cmd) >> false

        when:
        duplicate()

        then:
        1 * response.setStatus(HttpServletResponse.SC_FORBIDDEN)
        0 * service.getRecord(*_)
        0 * duplicator.copy(*_)
    }

    def "a source record hidden by row-level fetch restrictions is rejected"() {
        given:
        rowLevelSecurityService.canAdd(adminUser, OFFER_CLASS, cmd) >> true
        service.getRecord(ppr, "15", cmd, false) >> { throw new EntityNotFoundException() }

        when:
        duplicate()

        then:
        1 * response.setStatus(HttpServletResponse.SC_FORBIDDEN)
        0 * duplicator.copy(*_)
    }

    def "the security ceiling type is used for the permission checks when present"() {
        given:
        cmd.setSecurityCeilingType(OFFER_IMPL)
        rowLevelSecurityService.canAdd(adminUser, OFFER_IMPL, cmd) >> true
        givenSourceRecordReadable()
        duplicator.validate(Object, 15L) >> true
        duplicator.copy(Object, 15L) >> new Object()

        when:
        duplicate()

        then:
        1 * securityVerifier.securityCheck(OFFER_IMPL, EntityOperationType.ADD)
        1 * securityVerifier.securityCheck(OFFER_IMPL, EntityOperationType.FETCH)
        0 * securityVerifier.securityCheck(OFFER_CLASS, _)
    }
}
