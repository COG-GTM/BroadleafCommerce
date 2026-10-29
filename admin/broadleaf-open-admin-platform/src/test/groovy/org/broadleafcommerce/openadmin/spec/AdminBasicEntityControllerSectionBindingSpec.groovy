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
import org.broadleafcommerce.openadmin.server.security.domain.AdminUserImpl
import org.broadleafcommerce.openadmin.server.security.domain.AdminPermission
import org.broadleafcommerce.openadmin.server.security.domain.AdminPermissionImpl
import org.broadleafcommerce.openadmin.web.controller.entity.AdminBasicEntityController
import org.broadleafcommerce.openadmin.web.form.entity.EntityForm
import spock.lang.Specification

class AdminBasicEntityControllerSectionBindingSpec extends Specification {

    AdminBasicEntityController controller = new AdminBasicEntityController()
    ClassMetadata sectionMetadata

    def setup() {
        sectionMetadata = new ClassMetadata()
        sectionMetadata.ceilingType = AdminPermission.name
        ClassTree tree = new ClassTree(AdminPermission.name)
        tree.children = [new ClassTree(AdminPermissionImpl.name)] as ClassTree[]
        sectionMetadata.polymorphicEntities = tree
    }

    def "posted ceiling and id are replaced by the URL section's values"() {
        given:
        EntityForm form = new EntityForm(ceilingEntityClassname: AdminUserImpl.name,
                entityType: AdminPermissionImpl.name, id: "1")

        when:
        controller.bindEntityFormToSection(form, sectionMetadata, "42")

        then:
        form.ceilingEntityClassname == AdminPermission.name
        form.entityType == AdminPermissionImpl.name
        form.id == "42"
    }

    def "an entity type outside the section's hierarchy is rejected"() {
        given:
        EntityForm form = new EntityForm(ceilingEntityClassname: AdminPermission.name,
                entityType: AdminUserImpl.name, id: "1")

        when:
        controller.bindEntityFormToSection(form, sectionMetadata, "1")

        then:
        thrown(SecurityServiceException)
    }

    def "a blank entity type is left for the ceiling default"() {
        given:
        EntityForm form = new EntityForm(ceilingEntityClassname: AdminUserImpl.name)

        when:
        controller.bindEntityFormToSection(form, sectionMetadata, null)

        then:
        form.ceilingEntityClassname == AdminPermission.name
        form.entityType == null
    }
}
