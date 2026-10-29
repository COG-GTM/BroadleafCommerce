/*-
 * #%L
 * BroadleafCommerce Admin Module
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
package org.broadleafcommerce.admin.web.controller.action;

import org.broadleafcommerce.admin.server.service.AdminCatalogService;
import org.broadleafcommerce.common.exception.SecurityServiceException;
import org.broadleafcommerce.common.exception.ServiceException;
import org.broadleafcommerce.core.catalog.domain.Product;
import org.broadleafcommerce.core.catalog.domain.Sku;
import org.broadleafcommerce.openadmin.dto.ClassMetadata;
import org.broadleafcommerce.openadmin.dto.DynamicResultSet;
import org.broadleafcommerce.openadmin.dto.Entity;
import org.broadleafcommerce.openadmin.exception.EntityNotFoundException;
import org.broadleafcommerce.openadmin.server.domain.PersistencePackageRequest;
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser;
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType;
import org.broadleafcommerce.openadmin.server.security.remote.SecurityVerifier;
import org.broadleafcommerce.openadmin.server.security.service.RowLevelSecurityService;
import org.broadleafcommerce.openadmin.server.service.AdminEntityService;
import org.broadleafcommerce.openadmin.server.service.persistence.PersistenceResponse;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AdminCatalogActionsControllerTest {

    private static final Long PRODUCT_ID = 42L;
    private static final String GENERATE_URL = "http://localhost/admin/product/42/additionalSkus/generate-skus";

    private SecurityVerifier securityVerifier;
    private AdminEntityService adminEntityService;
    private RowLevelSecurityService rowLevelSecurityService;
    private AdminCatalogService adminCatalogService;
    private MessageSource messageSource;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private AdminUser adminUser;
    private Entity productEntity;
    private TestableController controller;

    @Before
    public void setUp() {
        securityVerifier = EasyMock.createMock(SecurityVerifier.class);
        adminEntityService = EasyMock.createMock(AdminEntityService.class);
        rowLevelSecurityService = EasyMock.createMock(RowLevelSecurityService.class);
        adminCatalogService = EasyMock.createMock(AdminCatalogService.class);
        messageSource = EasyMock.createNiceMock(MessageSource.class);
        request = EasyMock.createNiceMock(HttpServletRequest.class);
        response = EasyMock.createMock(HttpServletResponse.class);
        adminUser = EasyMock.createNiceMock(AdminUser.class);
        productEntity = new Entity();

        EasyMock.expect(messageSource.getMessage(EasyMock.eq(AdminCatalogActionsController.SKU_GENERATION_NOT_PERMITTED_KEY),
                EasyMock.isNull(), EasyMock.anyString(), EasyMock.anyObject(Locale.class)))
                .andStubReturn("not permitted");
        EasyMock.expect(request.getRequestURL()).andStubReturn(new StringBuffer(GENERATE_URL));
        EasyMock.expect(securityVerifier.getPersistentAdminUser()).andStubReturn(adminUser);

        controller = new TestableController(securityVerifier, adminEntityService);
        controller.adminCatalogService = adminCatalogService;
        controller.rowLevelSecurityService = rowLevelSecurityService;
        controller.messageSource = messageSource;
    }

    @Test
    public void generateSkusIsOnlyMappedToPost() throws Exception {
        Method method = AdminCatalogActionsController.class.getMethod("generateSkus", HttpServletRequest.class,
                HttpServletResponse.class, org.springframework.ui.Model.class, Long.class, String.class);
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        Assert.assertArrayEquals(new RequestMethod[] { RequestMethod.POST }, mapping.method());
    }

    @Test
    public void rejectsUserWithoutProductUpdatePermission() throws Exception {
        securityVerifier.securityCheck(Product.class.getName(), EntityOperationType.UPDATE);
        EasyMock.expectLastCall().andThrow(new SecurityServiceException("denied"));
        expectForbidden();

        assertForbidden(invoke());
    }

    @Test
    public void rejectsUserWithoutSkuAddPermission() throws Exception {
        securityVerifier.securityCheck(Product.class.getName(), EntityOperationType.UPDATE);
        securityVerifier.securityCheck(Sku.class.getName(), EntityOperationType.ADD);
        EasyMock.expectLastCall().andThrow(new SecurityServiceException("denied"));
        expectForbidden();

        assertForbidden(invoke());
    }

    @Test
    public void rejectsUserDeniedByRowLevelSecurity() throws Exception {
        expectEntityPermissions();
        expectProductRecord(new Entity[] { productEntity });
        EasyMock.expect(rowLevelSecurityService.canUpdate(adminUser, productEntity)).andReturn(false);
        expectForbidden();

        assertForbidden(invoke());
    }

    @Test
    public void rejectsProductNotVisibleToUser() throws Exception {
        expectEntityPermissions();
        expectProductRecord(new Entity[0]);
        expectForbidden();

        assertForbidden(invoke());
    }

    @Test
    public void rejectsProductHiddenByFetchRestrictions() throws Exception {
        expectEntityPermissions();
        ClassMetadata cmd = new ClassMetadata();
        EasyMock.expect(adminEntityService.getClassMetadata(EasyMock.anyObject(PersistencePackageRequest.class)))
                .andReturn(new PersistenceResponse().withDynamicResultSet(new DynamicResultSet(cmd)));
        EasyMock.expect(adminEntityService.getRecord(EasyMock.anyObject(PersistencePackageRequest.class),
                EasyMock.eq(String.valueOf(PRODUCT_ID)), EasyMock.same(cmd), EasyMock.eq(false)))
                .andThrow(new EntityNotFoundException("not found"));
        expectForbidden();

        assertForbidden(invoke());
    }

    @Test
    public void generatesSkusForAuthorizedUser() throws Exception {
        expectEntityPermissions();
        expectProductRecord(new Entity[] { productEntity });
        EasyMock.expect(rowLevelSecurityService.canUpdate(adminUser, productEntity)).andReturn(true);
        Map<String, Object> serviceResult = new HashMap<>();
        serviceResult.put("skusGenerated", 2);
        EasyMock.expect(adminCatalogService.generateSkus(PRODUCT_ID)).andReturn(serviceResult);

        Map<String, Object> body = invoke();

        Assert.assertEquals(2, body.get("skusGenerated"));
        Assert.assertEquals("http://localhost/admin/product/42/additionalSkus", body.get("listGridUrl"));
        EasyMock.verify(securityVerifier, adminEntityService, rowLevelSecurityService, adminCatalogService, response);
    }

    private void expectEntityPermissions() throws ServiceException {
        securityVerifier.securityCheck(Product.class.getName(), EntityOperationType.UPDATE);
        securityVerifier.securityCheck(Sku.class.getName(), EntityOperationType.ADD);
    }

    private void expectProductRecord(Entity[] records) throws ServiceException {
        ClassMetadata cmd = new ClassMetadata();
        EasyMock.expect(adminEntityService.getClassMetadata(EasyMock.anyObject(PersistencePackageRequest.class)))
                .andReturn(new PersistenceResponse().withDynamicResultSet(new DynamicResultSet(cmd)));
        EasyMock.expect(adminEntityService.getRecord(EasyMock.anyObject(PersistencePackageRequest.class),
                EasyMock.eq(String.valueOf(PRODUCT_ID)), EasyMock.same(cmd), EasyMock.eq(false)))
                .andReturn(new PersistenceResponse().withDynamicResultSet(new DynamicResultSet(records, records.length)));
    }

    private void expectForbidden() {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    private Map<String, Object> invoke() throws ServiceException {
        EasyMock.replay(securityVerifier, adminEntityService, rowLevelSecurityService, adminCatalogService,
                messageSource, request, response, adminUser);
        return controller.generateSkus(request, response, null, PRODUCT_ID, "additionalSkus");
    }

    private void assertForbidden(Map<String, Object> body) {
        Assert.assertEquals(Boolean.TRUE, body.get("error"));
        Assert.assertEquals("not permitted", body.get("message"));
        Assert.assertFalse(body.containsKey("listGridUrl"));
        // adminCatalogService is a strict mock with no expectations, so verify() fails if generateSkus was called
        EasyMock.verify(securityVerifier, adminEntityService, rowLevelSecurityService, adminCatalogService, response);
    }

    private static class TestableController extends AdminCatalogActionsController {
        TestableController(SecurityVerifier securityVerifier, AdminEntityService adminEntityService) {
            this.adminRemoteSecurityService = securityVerifier;
            this.service = adminEntityService;
        }
    }
}
