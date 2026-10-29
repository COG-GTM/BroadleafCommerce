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
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AdminCatalogActionsControllerTest {

    private static final Long PRODUCT_ID = 42L;

    private SecurityVerifier securityVerifier;
    private AdminEntityService adminEntityService;
    private RowLevelSecurityService rowLevelSecurityService;
    private AdminCatalogService adminCatalogService;
    private MessageSource messageSource;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private AdminUser adminUser;
    private Entity productRecord;
    private ClassMetadata productMetadata;
    private AdminCatalogActionsController controller;

    @Before
    public void setUp() throws Exception {
        securityVerifier = EasyMock.createMock(SecurityVerifier.class);
        adminEntityService = EasyMock.createMock(AdminEntityService.class);
        rowLevelSecurityService = EasyMock.createMock(RowLevelSecurityService.class);
        adminCatalogService = EasyMock.createMock(AdminCatalogService.class);
        messageSource = EasyMock.createNiceMock(MessageSource.class);
        request = EasyMock.createNiceMock(HttpServletRequest.class);
        response = EasyMock.createMock(HttpServletResponse.class);
        adminUser = EasyMock.createNiceMock(AdminUser.class);
        productRecord = new Entity();
        productMetadata = new ClassMetadata();

        EasyMock.expect(messageSource.getMessage(EasyMock.eq(AdminCatalogActionsController.SKU_GENERATION_FORBIDDEN_KEY),
                EasyMock.isNull(), EasyMock.anyString(), EasyMock.anyObject(Locale.class))).andStubReturn("forbidden");
        EasyMock.expect(request.getRequestURL())
                .andStubReturn(new StringBuffer("http://localhost/admin/product/42/additionalSkus/generate-skus"));
        EasyMock.expect(securityVerifier.getPersistentAdminUser()).andStubReturn(adminUser);

        controller = new AdminCatalogActionsController() {
            {
                adminRemoteSecurityService = securityVerifier;
                service = adminEntityService;
            }
        };
        controller.adminCatalogService = adminCatalogService;
        controller.rowLevelSecurityService = rowLevelSecurityService;
        controller.messageSource = messageSource;
    }

    @Test
    public void generateSkusAcceptsOnlyPost() throws Exception {
        RequestMapping mapping = AdminCatalogActionsController.class.getMethod("generateSkus", HttpServletRequest.class,
                HttpServletResponse.class, Model.class, Long.class, String.class).getAnnotation(RequestMapping.class);
        Assert.assertArrayEquals(new RequestMethod[] { RequestMethod.POST }, mapping.method());
    }

    @Test
    public void deniesUserWithoutProductUpdatePermission() throws Exception {
        securityVerifier.securityCheck(Product.class.getName(), EntityOperationType.UPDATE);
        EasyMock.expectLastCall().andThrow(new SecurityServiceException("denied"));
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        assertForbidden(invoke());
    }

    @Test
    public void deniesUserWithoutSkuAddPermission() throws Exception {
        securityVerifier.securityCheck(Product.class.getName(), EntityOperationType.UPDATE);
        securityVerifier.securityCheck(Sku.class.getName(), EntityOperationType.ADD);
        EasyMock.expectLastCall().andThrow(new SecurityServiceException("denied"));
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        assertForbidden(invoke());
    }

    @Test
    public void deniesWhenRowLevelSecurityBlocksProductUpdate() throws Exception {
        expectEntityChecksPass();
        expectProductLookupReturns(productRecord);
        EasyMock.expect(rowLevelSecurityService.canUpdate(adminUser, productRecord)).andReturn(false);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        assertForbidden(invoke());
    }

    @Test
    public void deniesWhenProductIsNotVisibleToUser() throws Exception {
        expectEntityChecksPass();
        expectProductLookupReturns();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        assertForbidden(invoke());
    }

    @Test
    public void deniesWhenProductLookupFindsNothing() throws Exception {
        expectEntityChecksPass();
        expectProductMetadata();
        EasyMock.expect(adminEntityService.getRecord(EasyMock.anyObject(PersistencePackageRequest.class),
                EasyMock.eq("42"), EasyMock.same(productMetadata), EasyMock.eq(false)))
                .andThrow(new EntityNotFoundException("missing"));
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        assertForbidden(invoke());
    }

    @Test
    public void generatesSkusForAuthorizedUser() throws Exception {
        expectEntityChecksPass();
        expectProductLookupReturns(productRecord);
        EasyMock.expect(rowLevelSecurityService.canUpdate(adminUser, productRecord)).andReturn(true);
        Map<String, Object> generated = new HashMap<>();
        generated.put("skusGenerated", 4);
        EasyMock.expect(adminCatalogService.generateSkus(PRODUCT_ID)).andReturn(generated);

        Map<String, Object> body = invoke();

        Assert.assertEquals(4, body.get("skusGenerated"));
        Assert.assertEquals("http://localhost/admin/product/42/additionalSkus", body.get("listGridUrl"));
        verifyStrictMocks();
    }

    private void expectEntityChecksPass() throws Exception {
        securityVerifier.securityCheck(Product.class.getName(), EntityOperationType.UPDATE);
        securityVerifier.securityCheck(Sku.class.getName(), EntityOperationType.ADD);
    }

    private void expectProductMetadata() throws Exception {
        EasyMock.expect(adminEntityService.getClassMetadata(EasyMock.anyObject(PersistencePackageRequest.class)))
                .andReturn(new PersistenceResponse().withDynamicResultSet(new DynamicResultSet(productMetadata)));
    }

    private void expectProductLookupReturns(Entity... records) throws Exception {
        expectProductMetadata();
        EasyMock.expect(adminEntityService.getRecord(EasyMock.anyObject(PersistencePackageRequest.class),
                EasyMock.eq("42"), EasyMock.same(productMetadata), EasyMock.eq(false)))
                .andReturn(new PersistenceResponse().withDynamicResultSet(new DynamicResultSet(records, records.length)));
    }

    private Map<String, Object> invoke() throws Exception {
        EasyMock.replay(securityVerifier, adminEntityService, rowLevelSecurityService, adminCatalogService,
                messageSource, request, response, adminUser);
        return controller.generateSkus(request, response, null, PRODUCT_ID, "additionalSkus");
    }

    private void assertForbidden(Map<String, Object> body) {
        Assert.assertEquals("forbidden", body.get("error"));
        Assert.assertEquals("forbidden", body.get("message"));
        Assert.assertFalse(body.containsKey("listGridUrl"));
        // adminCatalogService has no expectations, so verify() fails if Skus were generated
        verifyStrictMocks();
    }

    private void verifyStrictMocks() {
        EasyMock.verify(securityVerifier, adminEntityService, rowLevelSecurityService, adminCatalogService, response);
    }
}
