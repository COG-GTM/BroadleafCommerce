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

import org.broadleafcommerce.openadmin.web.controller.AdminAbstractController
import org.broadleafcommerce.openadmin.web.form.entity.EntityForm
import org.springframework.beans.MutablePropertyValues
import org.springframework.web.bind.WebDataBinder
import spock.lang.Specification

class DynamicFormBindingSpec extends Specification {

    static final String PERMISSION_CLASS = 'org.broadleafcommerce.openadmin.server.security.domain.AdminPermissionImpl'

    def "dynamic form metadata cannot be bound from request parameters"() {
        given:
        EntityForm form = new EntityForm()
        WebDataBinder binder = new WebDataBinder(form, 'entityForm')
        new AdminAbstractController() {}.initDynamicFormBinder(binder)

        when:
        binder.bind(attackParams())

        then:
        form.dynamicFormInfos.isEmpty()
        form.dynamicForms.isEmpty()
        binder.bindingResult.suppressedFields.toList().containsAll([
                'dynamicFormInfos[x].ceilingClassName',
                'dynamicFormInfos[x].customCriteriaOverride[0]',
                'dynamicForms[x].ceilingEntityClassname'
        ])
    }

    def "without the restriction the same parameters would populate the dynamic form metadata"() {
        given:
        EntityForm form = new EntityForm()
        WebDataBinder binder = new WebDataBinder(form, 'entityForm')

        when:
        binder.bind(attackParams())

        then:
        form.dynamicFormInfos['x'].ceilingClassName == PERMISSION_CLASS
        form.dynamicFormInfos['x'].customCriteriaOverride[0] == 'createNewPermission'
    }

    def "existing disallowed fields are preserved"() {
        given:
        WebDataBinder binder = new WebDataBinder(new EntityForm(), 'entityForm')
        binder.setDisallowedFields('id')

        when:
        new AdminAbstractController() {}.initDynamicFormBinder(binder)

        then:
        binder.disallowedFields.toList().containsAll(['id', 'dynamicforminfos*', 'dynamicforms*'])
    }

    private static MutablePropertyValues attackParams() {
        MutablePropertyValues pvs = new MutablePropertyValues()
        pvs.add('dynamicFormInfos[x].ceilingClassName', PERMISSION_CLASS)
        pvs.add('dynamicFormInfos[x].customCriteriaOverride[0]', 'createNewPermission')
        pvs.add('dynamicForms[x].ceilingEntityClassname', PERMISSION_CLASS)
        return pvs
    }
}
