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
package org.broadleafcommerce.openadmin.server.security.dao

import org.broadleafcommerce.common.service.PersistenceService
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser
import org.broadleafcommerce.openadmin.server.security.service.type.PermissionType
import spock.lang.Specification

import jakarta.persistence.EntityManager
import jakarta.persistence.Query

class CeilingTripwireState {
    static volatile boolean initialized = false
}

class CeilingTripwire implements Serializable {
    static {
        CeilingTripwireState.initialized = true
    }
}

class AdminPermissionDaoImplSpec extends Specification {

    AdminPermissionDaoImpl dao = new AdminPermissionDaoImpl()
    PersistenceService persistenceService = Mock()
    EntityManager em = Mock()
    Query query = Mock()

    def setup() {
        CeilingTripwireState.initialized = false
        dao.persistenceService = persistenceService
        dao.em = em
        em.createNamedQuery(_) >> query
        query.getSingleResult() >> 0L
    }

    def "non-whitelisted ceiling names are tested literally without loading the class"() {
        given:
        String name = CeilingTripwire.name
        persistenceService.validateEntityTypeName(name) >> false

        when:
        List<String> testClasses = dao.buildCeilingEntityTestClasses(name)

        then:
        testClasses == [name]
        !CeilingTripwireState.initialized
    }

    def "unknown class names fail the permission check instead of throwing"() {
        given:
        AdminUser adminUser = Mock()
        persistenceService.validateEntityTypeName(_) >> false

        when:
        boolean qualified = dao.isUserQualifiedForOperationOnCeilingEntity(
                adminUser, PermissionType.UPDATE, "java.lang.System")

        then:
        !qualified
        1 * query.setParameter("ceilingEntity", "java.lang.System")
        0 * query.setParameter("ceilingEntity", { it != "java.lang.System" })
        notThrown(RuntimeException)
    }

    def "missing classes are not resolved, so the response does not reveal classpath contents"() {
        given:
        persistenceService.validateEntityTypeName(_) >> false

        expect:
        !dao.doesOperationExistForCeilingEntity(PermissionType.READ, "com.example.DoesNotExist")
        !dao.isUserQualifiedForOperationOnCeilingEntityViaDefaultPermissions("com.example.DoesNotExist")
    }

    def "whitelisted entity types still expand to their interfaces without static initialization"() {
        given:
        String name = CeilingTripwire.name
        persistenceService.validateEntityTypeName(name) >> true

        when:
        List<String> testClasses = dao.buildCeilingEntityTestClasses(name)

        then:
        testClasses[0] == name
        testClasses.contains(Serializable.name)
        !CeilingTripwireState.initialized
    }
}
