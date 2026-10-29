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

import org.broadleafcommerce.common.security.service.ExploitProtectionServiceImpl
import org.broadleafcommerce.common.web.BroadleafRequestContext
import org.broadleafcommerce.openadmin.dto.Entity
import org.broadleafcommerce.openadmin.dto.PersistencePackage
import org.broadleafcommerce.openadmin.dto.Property
import org.broadleafcommerce.openadmin.server.service.DynamicEntityRemoteService
import spock.lang.Specification

class DynamicEntityRemoteServiceCleaningSpec extends Specification {

    static final String SCRIPT = "<p>hello</p><script>alert(1)</script>"
    static final String PAGE = "org.broadleafcommerce.cms.page.domain.Page"
    static final String PAGE_TEMPLATE_FIELD = "org.broadleafcommerce.cms.page.domain.PageTemplateFieldGroupImpl"

    TestableDynamicEntityRemoteService service

    def setup() {
        BroadleafRequestContext.setBroadleafRequestContext(new BroadleafRequestContext())
        ExploitProtectionServiceImpl exploitProtectionService = new ExploitProtectionServiceImpl()
        exploitProtectionService.setXssProtectionEnabled(true)
        service = new TestableDynamicEntityRemoteService(exploitProtectionService: exploitProtectionService)
    }

    def cleanup() {
        BroadleafRequestContext.setBroadleafRequestContext(null)
    }

    def "script in a dynamic form sub-package field fails AntiSamy validation"() {
        given:
        PersistencePackage root = pkg(PAGE, entity("description", "About us"))
        PersistencePackage dynamicForm = pkg(PAGE_TEMPLATE_FIELD, entity("body", SCRIPT))
        root.getSubPackages().put("pageTemplate", dynamicForm)

        when:
        service.clean(root)

        then:
        !root.getEntity().isValidationFailure()
        dynamicForm.getEntity().isValidationFailure()
        dynamicForm.getEntity().getPropertyValidationErrors().containsKey("body")
        dynamicForm.getEntity().findProperty("body").getRawValue() == SCRIPT
    }

    def "nested sub-packages are cleaned recursively"() {
        given:
        PersistencePackage root = pkg(PAGE, entity("description", "About us"))
        PersistencePackage child = pkg(PAGE_TEMPLATE_FIELD, entity("title", "Safe"))
        PersistencePackage grandChild = pkg(PAGE_TEMPLATE_FIELD, entity("body", SCRIPT))
        child.getSubPackages().put("nested", grandChild)
        root.getSubPackages().put("pageTemplate", child)

        when:
        service.clean(root)

        then:
        !child.getEntity().isValidationFailure()
        grandChild.getEntity().getPropertyValidationErrors().containsKey("body")
    }

    def "safe sub-package HTML is kept and passes validation"() {
        given:
        PersistencePackage root = pkg(PAGE, entity("description", "About us"))
        PersistencePackage dynamicForm = pkg(PAGE_TEMPLATE_FIELD, entity("body", "<p>Spring sale</p>"))
        root.getSubPackages().put("pageTemplate", dynamicForm)

        when:
        service.clean(root)

        then:
        !dynamicForm.getEntity().isValidationFailure()
        dynamicForm.getEntity().findProperty("body").getValue() == "<p>Spring sale</p>"
    }

    def "sub-packages are not cleaned when the root ceiling entity is in the ignore list"() {
        given:
        service.entitiesIgnoreList = [PAGE] as Set
        PersistencePackage root = pkg(PAGE, entity("description", SCRIPT))
        PersistencePackage dynamicForm = pkg(PAGE_TEMPLATE_FIELD, entity("body", SCRIPT))
        root.getSubPackages().put("pageTemplate", dynamicForm)

        when:
        service.clean(root)

        then:
        !root.getEntity().isValidationFailure()
        !dynamicForm.getEntity().isValidationFailure()
    }

    def "a sub-package whose ceiling entity is in the ignore list is skipped"() {
        given:
        service.entitiesIgnoreList = [PAGE_TEMPLATE_FIELD] as Set
        PersistencePackage root = pkg(PAGE, entity("description", "About us"))
        PersistencePackage dynamicForm = pkg(PAGE_TEMPLATE_FIELD, entity("body", SCRIPT))
        root.getSubPackages().put("pageTemplate", dynamicForm)

        when:
        service.clean(root)

        then:
        !dynamicForm.getEntity().isValidationFailure()
    }

    def "nothing is cleaned when IGNORE_ENTITY_CLEANING is set on the request"() {
        given:
        BroadleafRequestContext.getBroadleafRequestContext().getAdditionalProperties().put("IGNORE_ENTITY_CLEANING", Boolean.TRUE)
        PersistencePackage root = pkg(PAGE, entity("description", "About us"))
        PersistencePackage dynamicForm = pkg(PAGE_TEMPLATE_FIELD, entity("body", SCRIPT))
        root.getSubPackages().put("pageTemplate", dynamicForm)

        when:
        service.clean(root)

        then:
        !dynamicForm.getEntity().isValidationFailure()
    }

    static PersistencePackage pkg(String ceiling, Entity entity) {
        PersistencePackage pp = new PersistencePackage()
        pp.setCeilingEntityFullyQualifiedClassname(ceiling)
        pp.setEntity(entity)
        return pp
    }

    static Entity entity(String name, String value) {
        Property property = new Property()
        property.setName(name)
        property.setValue(value)
        Entity entity = new Entity()
        entity.setProperties([property] as Property[])
        return entity
    }

    static class TestableDynamicEntityRemoteService extends DynamicEntityRemoteService {
        void clean(PersistencePackage persistencePackage) {
            cleanPersistencePackage(persistencePackage)
        }
    }
}
