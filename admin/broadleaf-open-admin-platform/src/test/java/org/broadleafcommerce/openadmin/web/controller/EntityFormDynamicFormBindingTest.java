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
package org.broadleafcommerce.openadmin.web.controller;

import org.broadleafcommerce.common.exception.SecurityServiceException;
import org.broadleafcommerce.openadmin.server.service.AdminEntityServiceImpl;
import org.broadleafcommerce.openadmin.web.filter.BroadleafAdminRequestFilter;
import org.broadleafcommerce.openadmin.web.form.entity.DynamicEntityFormInfo;
import org.broadleafcommerce.openadmin.web.form.entity.EntityForm;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.web.bind.WebDataBinder;

public class EntityFormDynamicFormBindingTest {

    protected static final String ADMIN_USER = "org.broadleafcommerce.openadmin.server.security.domain.AdminUser";
    protected static final String PRODUCT = "org.broadleafcommerce.core.catalog.domain.Product";

    @Test
    public void requestCannotBindDynamicFormsOrTheirSecurityCeiling() {
        EntityForm entityForm = new EntityForm();
        WebDataBinder binder = new WebDataBinder(entityForm, "entityForm");
        new AdminAbstractController() {
        }.initEntityFormBinder(binder);

        MutablePropertyValues values = new MutablePropertyValues();
        values.add("id", "100");
        values.add("dynamicForms[x].ceilingEntityClassname", ADMIN_USER);
        values.add("dynamicForms[x].id", "1");
        values.add("dynamicFormInfos[x].securityCeilingClassName", PRODUCT);
        values.add("dynamicFormInfos[x].propertyName", "x");
        values.add("dynamicFormInfos[x].customCriteriaOverride", "x");
        values.add("DynamicFormInfos[y].securityCeilingClassName", PRODUCT);
        values.add("sectionCrumbsImpl[0].sectionIdentifier", PRODUCT);
        values.add("attributes[foo]", "bar");
        binder.bind(values);

        Assert.assertEquals("100", entityForm.getId());
        Assert.assertTrue(entityForm.getDynamicForms().isEmpty());
        Assert.assertTrue(entityForm.getDynamicFormInfos().isEmpty());
        Assert.assertTrue(entityForm.getSectionCrumbsImpl().isEmpty());
        Assert.assertTrue(entityForm.getAttributes().isEmpty());
        Assert.assertTrue(binder.getBindingResult().getSuppressedFields().length >= 8);
    }

    @Test(expected = SecurityServiceException.class)
    public void dynamicFormWithoutServerRegisteredInfoIsRejected() throws Exception {
        EntityForm entityForm = new EntityForm();
        EntityForm dynamicForm = new EntityForm();
        dynamicForm.setCeilingEntityClassname(ADMIN_USER);
        entityForm.putDynamicForm("x", dynamicForm);

        new TestAdminEntityService().validate(entityForm, "x");
    }

    @Test(expected = SecurityServiceException.class)
    public void dynamicFormWhoseCeilingDiffersFromRegisteredInfoIsRejected() throws Exception {
        EntityForm entityForm = new EntityForm();
        EntityForm dynamicForm = new EntityForm();
        dynamicForm.setCeilingEntityClassname(ADMIN_USER);
        entityForm.putDynamicForm("pageTemplate", dynamicForm);
        entityForm.putDynamicFormInfo("pageTemplate", new DynamicEntityFormInfo()
                .withCeilingClassName("org.broadleafcommerce.cms.page.domain.PageTemplate")
                .withSecurityCeilingClassName("org.broadleafcommerce.cms.page.domain.Page")
                .withPropertyName("pageTemplate"));

        new TestAdminEntityService().validate(entityForm, "pageTemplate");
    }

    @Test
    public void serverRegisteredDynamicFormIsAccepted() throws Exception {
        EntityForm entityForm = new EntityForm();
        EntityForm dynamicForm = new EntityForm();
        dynamicForm.setCeilingEntityClassname("org.broadleafcommerce.cms.page.domain.PageTemplate");
        entityForm.putDynamicForm("pageTemplate", dynamicForm);
        DynamicEntityFormInfo info = new DynamicEntityFormInfo()
                .withCeilingClassName("org.broadleafcommerce.cms.page.domain.PageTemplate")
                .withSecurityCeilingClassName("org.broadleafcommerce.cms.page.domain.Page")
                .withPropertyName("pageTemplate");
        entityForm.putDynamicFormInfo("pageTemplate", info);

        Assert.assertSame(info, new TestAdminEntityService().validate(entityForm, "pageTemplate"));
    }

    @Test
    public void nestedClassNameParamsAreWhitelistValidated() {
        TestRequestFilter filter = new TestRequestFilter();
        Assert.assertTrue(filter.isNested("dynamicForms[x].ceilingEntityClassname"));
        Assert.assertTrue(filter.isNested("dynamicFormInfos[x].securityCeilingClassName"));
        Assert.assertTrue(filter.isNested("dynamicFormInfos[x].ceilingClassName"));
        Assert.assertTrue(filter.isNested("ceilingEntityClassname"));
        Assert.assertFalse(filter.isNested("fields['name'].value"));
        Assert.assertFalse(filter.isNested("id"));
        Assert.assertTrue(filter.isNested("dynamicForms[x].entityType"));
        Assert.assertFalse(filter.isNestedOutsideEntityTypeScope("dynamicForms[x].entityType"));
    }

    protected static class TestAdminEntityService extends AdminEntityServiceImpl {
        DynamicEntityFormInfo validate(EntityForm entityForm, String name) throws SecurityServiceException {
            return getValidatedDynamicFormInfo(entityForm, name, entityForm.getDynamicForm(name));
        }
    }

    protected static class TestRequestFilter extends BroadleafAdminRequestFilter {
        boolean isNested(String paramName) {
            return isNestedClassNameParam(paramName, true);
        }

        boolean isNestedOutsideEntityTypeScope(String paramName) {
            return isNestedClassNameParam(paramName, false);
        }
    }
}
