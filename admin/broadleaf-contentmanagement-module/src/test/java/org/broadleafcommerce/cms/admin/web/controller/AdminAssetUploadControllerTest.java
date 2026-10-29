/*-
 * #%L
 * BroadleafCommerce CMS Module
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
package org.broadleafcommerce.cms.admin.web.controller;

import org.broadleafcommerce.cms.file.domain.StaticAsset;
import org.broadleafcommerce.cms.file.domain.StaticAssetImpl;
import org.broadleafcommerce.cms.file.service.StaticAssetService;
import org.broadleafcommerce.cms.file.service.StaticAssetStorageService;
import org.broadleafcommerce.common.exception.SecurityServiceException;
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser;
import org.broadleafcommerce.openadmin.server.security.domain.AdminUserImpl;
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType;
import org.broadleafcommerce.openadmin.server.security.remote.SecurityVerifier;
import org.broadleafcommerce.openadmin.server.security.service.RowLevelSecurityService;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.util.LinkedMultiValueMap;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AdminAssetUploadControllerTest {

    private static final String CEILING = StaticAsset.class.getName();

    private AdminAssetUploadController controller;
    private SecurityVerifier securityVerifier;
    private RowLevelSecurityService rowLevelSecurityService;
    private StaticAssetService staticAssetService;
    private StaticAssetStorageService staticAssetStorageService;
    private AdminUser adminUser;
    private MockMultipartFile file;

    @Before
    public void setUp() {
        securityVerifier = EasyMock.createMock(SecurityVerifier.class);
        rowLevelSecurityService = EasyMock.createMock(RowLevelSecurityService.class);
        staticAssetService = EasyMock.createStrictMock(StaticAssetService.class);
        staticAssetStorageService = EasyMock.createStrictMock(StaticAssetStorageService.class);
        adminUser = new AdminUserImpl();
        file = new MockMultipartFile("file", "evil.html", "text/html", "<script>alert(1)</script>".getBytes());

        controller = new AdminAssetUploadController();
        ReflectionTestUtils.setField(controller, "adminRemoteSecurityService", securityVerifier);
        ReflectionTestUtils.setField(controller, "rowLevelSecurityService", rowLevelSecurityService);
        ReflectionTestUtils.setField(controller, "staticAssetService", staticAssetService);
        ReflectionTestUtils.setField(controller, "staticAssetStorageService", staticAssetStorageService);
    }

    private void denyAdd() throws Exception {
        securityVerifier.securityCheck(CEILING, EntityOperationType.ADD);
        EasyMock.expectLastCall().andThrow(new SecurityServiceException("denied"));
    }

    private void allowAdd(boolean rowLevelAllowed) throws Exception {
        securityVerifier.securityCheck(CEILING, EntityOperationType.ADD);
        EasyMock.expectLastCall();
        EasyMock.expect(securityVerifier.getPersistentAdminUser()).andReturn(adminUser);
        EasyMock.expect(rowLevelSecurityService.canAdd(adminUser, CEILING, null)).andReturn(rowLevelAllowed);
    }

    private void replayAll() {
        EasyMock.replay(securityVerifier, rowLevelSecurityService, staticAssetService, staticAssetStorageService);
    }

    private void verifyAll() {
        EasyMock.verify(securityVerifier, rowLevelSecurityService, staticAssetService, staticAssetStorageService);
    }

    @Test
    public void entityUploadWithoutAddPermissionIsRejectedBeforeAssetIsCreated() throws Exception {
        denyAdd();
        replayAll();
        try {
            controller.upload(new MockHttpServletRequest(), file, "product", "1");
            fail("expected SecurityServiceException");
        } catch (SecurityServiceException expected) {
            // expected
        }
        verifyAll();
    }

    @Test
    public void nestedEntityUploadWithoutAddPermissionIsRejected() throws Exception {
        denyAdd();
        replayAll();
        try {
            controller.upload(new MockHttpServletRequest(), file, "product", "skus", "1");
            fail("expected SecurityServiceException");
        } catch (SecurityServiceException expected) {
            // expected
        }
        verifyAll();
    }

    @Test
    public void listViewUploadWithoutAddPermissionIsRejected() throws Exception {
        denyAdd();
        replayAll();
        try {
            controller.upload(new MockHttpServletRequest(), new MockHttpServletResponse(), new ExtendedModelMap(),
                    new HashMap<String, String>(), file, new LinkedMultiValueMap<String, String>());
            fail("expected SecurityServiceException");
        } catch (SecurityServiceException expected) {
            // expected
        }
        verifyAll();
    }

    @Test
    public void uploadDeniedByRowLevelSecurityIsRejected() throws Exception {
        allowAdd(false);
        replayAll();
        try {
            controller.upload(new MockHttpServletRequest(), file, "product", "1");
            fail("expected SecurityServiceException");
        } catch (SecurityServiceException expected) {
            // expected
        }
        verifyAll();
    }

    @Test
    public void listViewUploadWithAddPermissionCreatesAsset() throws Exception {
        allowAdd(true);
        StaticAssetImpl asset = new StaticAssetImpl();
        asset.setId(42L);
        EasyMock.expect(staticAssetService.createStaticAssetFromFile(file, null)).andReturn(asset);
        staticAssetStorageService.createStaticAssetStorageFromFile(file, asset);
        EasyMock.expect(staticAssetService.getStaticAssetUrlPrefix()).andReturn("cmsstatic");
        replayAll();

        String view = controller.upload(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new ExtendedModelMap(), new HashMap<String, String>(), file, new LinkedMultiValueMap<String, String>());

        assertEquals("redirect:/assets/42", view);
        verifyAll();
    }

    @Test
    public void entityUploadWithAddPermissionCreatesAsset() throws Exception {
        allowAdd(true);
        StaticAssetImpl asset = new StaticAssetImpl();
        asset.setId(7L);
        asset.setFullUrl("/product/1/evil.html");
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("entityType", "product");
        properties.put("entityId", "1");
        EasyMock.expect(staticAssetService.createStaticAssetFromFile(file, properties)).andReturn(asset);
        staticAssetStorageService.createStaticAssetStorageFromFile(file, asset);
        EasyMock.expect(staticAssetService.getStaticAssetUrlPrefix()).andReturn("cmsstatic");
        replayAll();

        ResponseEntity<Map<String, Object>> response =
                controller.upload(new MockHttpServletRequest(), file, "product", "1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("/cmsstatic/product/1/evil.html", response.getBody().get("assetUrl"));
        verifyAll();
    }

    @Test
    public void chooseAssetWithoutFetchPermissionIsRejected() throws Exception {
        securityVerifier.securityCheck(CEILING, EntityOperationType.FETCH);
        EasyMock.expectLastCall().andThrow(new SecurityServiceException("denied"));
        replayAll();
        try {
            controller.chooseMediaForMapKey(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    new ExtendedModelMap(), "product", "1", new LinkedMultiValueMap<String, String>());
            fail("expected SecurityServiceException");
        } catch (SecurityServiceException expected) {
            // expected
        }
        verifyAll();
    }

    @Test
    public void securityExceptionsMapToForbidden() {
        ResponseEntity<Map<String, Object>> response =
                controller.handleSecurityServiceException(new SecurityServiceException("denied"));
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
