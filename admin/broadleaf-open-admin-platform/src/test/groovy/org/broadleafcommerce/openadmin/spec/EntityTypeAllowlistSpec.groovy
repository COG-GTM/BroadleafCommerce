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
import org.broadleafcommerce.openadmin.dto.ClassMetadata
import org.broadleafcommerce.openadmin.dto.ClassTree
import org.broadleafcommerce.openadmin.server.security.domain.AdminRole
import org.broadleafcommerce.openadmin.server.security.domain.AdminRoleImpl
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser
import org.broadleafcommerce.openadmin.server.security.domain.AdminUserImpl
import org.broadleafcommerce.openadmin.server.service.AdminEntityServiceImpl
import org.broadleafcommerce.openadmin.web.controller.entity.AdminBasicEntityController
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import spock.lang.Specification
import spock.lang.Unroll

class EntityTypeAllowlistSpec extends Specification {

    static final String PARENT_TYPE = 'org.example.ParentImpl'
    static final String CHILD_TYPE = 'org.example.ChildImpl'

    AdminBasicEntityController controller = new AdminBasicEntityController()
    AdminEntityServiceImpl service = new AdminEntityServiceImpl()

    ClassMetadata metadataWithChildren() {
        ClassTree root = new ClassTree(PARENT_TYPE)
        root.setChildren([new ClassTree(CHILD_TYPE)] as ClassTree[])
        ClassMetadata cmd = new ClassMetadata()
        cmd.setCeilingType('org.example.Parent')
        cmd.setPolymorphicEntities(root)
        return cmd
    }

    @Unroll
    def "requested entity type #requested from the polymorphic tree is accepted"() {
        expect:
        controller.determineEntityType(requested, metadataWithChildren()) == expected

        where:
        requested                                | expected
        PARENT_TYPE                              | PARENT_TYPE
        CHILD_TYPE                               | CHILD_TYPE
        'org.example.ChildImpl'.replace('.', '%2E') | CHILD_TYPE
    }

    def "a blank entity type still resolves to the single polymorphic type"() {
        given:
        ClassMetadata cmd = new ClassMetadata()
        cmd.setPolymorphicEntities(new ClassTree(PARENT_TYPE))

        expect:
        controller.determineEntityType(null, cmd) == PARENT_TYPE
        controller.determineEntityType('', cmd) == PARENT_TYPE
    }

    @Unroll
    def "requested entity type #requested outside the polymorphic tree is rejected with 400"() {
        when:
        controller.determineEntityType(requested, metadataWithChildren())

        then:
        ResponseStatusException e = thrown()
        e.statusCode.value() == HttpStatus.BAD_REQUEST.value()

        where:
        requested << [
                'org.broadleafcommerce.profile.core.domain.CustomerImpl',
                'org.broadleafcommerce.profile.core.domain.CustomerImpl'.replace('.', '%2E'),
                'org.example.Parent',
                'org.example.ChildImpl%24'
        ]
    }

    static ClassTree tree(Class<?>... types) {
        ClassTree root = new ClassTree(types[0].name)
        root.setChildren(types.drop(1).collect { new ClassTree(it.name) } as ClassTree[])
        return root
    }

    def "a collection item type that is the ceiling or an allowed polymorphic subtype is accepted on save"() {
        when:
        service.validateSubCollectionEntityType(AdminUserImpl.name, AdminUser.name, tree(AdminUserImpl))
        service.validateSubCollectionEntityType(AdminUser.name, AdminUser.name, null as ClassTree)

        then:
        noExceptionThrown()
    }

    @Unroll
    def "a collection item type #entityType under ceiling #ceiling is rejected on save"() {
        when:
        service.validateSubCollectionEntityType(entityType as String, ceiling as String, allowedTypes as ClassTree)

        then:
        thrown(SecurityServiceException)

        where:
        entityType                                               | ceiling        | allowedTypes
        null                                                     | AdminUser.name | tree(AdminUserImpl)
        ''                                                       | AdminUser.name | tree(AdminUserImpl)
        AdminUserImpl.name                                       | AdminUser.name | tree(AdminRoleImpl)
        AdminUserImpl.name                                       | AdminUser.name | null
        AdminRoleImpl.name                                       | AdminUser.name | tree(AdminUserImpl, AdminRoleImpl)
        AdminUserImpl.name                                       | AdminRole.name | tree(AdminUserImpl)
        'org.broadleafcommerce.profile.core.domain.CustomerImpl' | AdminUser.name | new ClassTree('org.broadleafcommerce.profile.core.domain.CustomerImpl')
        AdminUserImpl.name                                       | null           | tree(AdminUserImpl)
    }
}
