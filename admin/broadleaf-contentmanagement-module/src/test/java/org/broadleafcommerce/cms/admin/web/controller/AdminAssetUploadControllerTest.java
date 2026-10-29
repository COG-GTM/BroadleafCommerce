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
import org.broadleafcommerce.openadmin.security.ClassNameRequestParamValidationService;
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType;
import org.broadleafcommerce.openadmin.server.security.remote.SecurityVerifier;
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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AdminAssetUploadControllerTest {

    protected static final String PRODUCT_CLASS = "org.broadleafcommerce.core.catalog.domain.ProductImpl";

    protected AdminAssetUploadController controller;
    protected Set<String> grants;
    protected List<String> serviceCalls;
    protected MockMultipartFile file;

    @Before
    public void setUp() {
        grants = new HashSet<>();
        serviceCalls = new ArrayList<>();
        file = new MockMultipartFile("file", "image.png", "image/png", new byte[]{1, 2, 3});
        controller = new AdminAssetUploadController();

        SecurityVerifier securityVerifier = proxy(SecurityVerifier.class, (method, args) -> {
            if ("securityCheck".equals(method) && args[0] instanceof String) {
                if (!grants.contains(args[0] + ":" + args[1])) {
                    throw new SecurityServiceException("denied");
                }
            }
            return null;
        });
        ClassNameRequestParamValidationService validationService = proxy(
                ClassNameRequestParamValidationService.class, (method, args) -> {
                    if ("getClassNameForSection".equals(method)) {
                        return "product".equals(args[0]) ? PRODUCT_CLASS : args[0];
                    }
                    return null;
                });
        StaticAssetService staticAssetService = proxy(StaticAssetService.class, (method, args) -> {
            serviceCalls.add(method);
            if ("createStaticAssetFromFile".equals(method)) {
                StaticAsset asset = new StaticAssetImpl();
                asset.setId(5L);
                asset.setFullUrl("/product/1/image.png");
                return asset;
            }
            if ("getStaticAssetUrlPrefix".equals(method)) {
                return "cmsstatic";
            }
            return null;
        });
        StaticAssetStorageService staticAssetStorageService = proxy(StaticAssetStorageService.class, (method, args) -> {
            serviceCalls.add(method);
            return null;
        });

        ReflectionTestUtils.setField(controller, "adminRemoteSecurityService", securityVerifier);
        ReflectionTestUtils.setField(controller, "validationService", validationService);
        ReflectionTestUtils.setField(controller, "staticAssetService", staticAssetService);
        ReflectionTestUtils.setField(controller, "staticAssetStorageService", staticAssetStorageService);
    }

    @Test
    public void testUploadWithoutPermissionsIsForbidden() throws Exception {
        ResponseEntity<Map<String, Object>> response = controller.upload(
                new MockHttpServletRequest(), file, "product", "1");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(serviceCalls.isEmpty());
    }

    @Test
    public void testUploadWithReadOnlyProductPermissionIsForbidden() throws Exception {
        grants.add(PRODUCT_CLASS + ":" + EntityOperationType.FETCH);
        ResponseEntity<Map<String, Object>> response = controller.upload(
                new MockHttpServletRequest(), file, "product", "1", "1");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(serviceCalls.isEmpty());
    }

    @Test
    public void testUploadWithAssetCreatePermissionIsAllowed() throws Exception {
        grants.add(StaticAssetImpl.class.getName() + ":" + EntityOperationType.ADD);
        ResponseEntity<Map<String, Object>> response = controller.upload(
                new MockHttpServletRequest(), file, "product", "1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("/cmsstatic/product/1/image.png", response.getBody().get("assetUrl"));
        assertTrue(serviceCalls.contains("createStaticAssetStorageFromFile"));
    }

    @Test
    public void testUploadWithOnlySectionUpdatePermissionIsForbidden() throws Exception {
        grants.add(PRODUCT_CLASS + ":" + EntityOperationType.UPDATE);
        ResponseEntity<Map<String, Object>> response = controller.upload(
                new MockHttpServletRequest(), file, "product", "1");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(serviceCalls.isEmpty());
    }

    @Test
    public void testAssetSectionUploadRequiresAssetCreatePermission() throws Exception {
        grants.add(StaticAssetImpl.class.getName() + ":" + EntityOperationType.UPDATE);
        ResponseEntity<Map<String, Object>> response = controller.upload(
                new MockHttpServletRequest(), file, StaticAssetImpl.class.getName(), "1");
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(serviceCalls.isEmpty());
    }

    @Test
    public void testAssetListUploadWithoutPermissionIsRejected() throws Exception {
        grants.add(PRODUCT_CLASS + ":" + EntityOperationType.UPDATE);
        try {
            controller.upload(new MockHttpServletRequest(), new MockHttpServletResponse(), new ExtendedModelMap(),
                    new HashMap<>(), file, new LinkedMultiValueMap<>());
            fail("Expected security exception");
        } catch (SecurityServiceException expected) {
            // expected
        }
        assertTrue(serviceCalls.isEmpty());
    }

    @Test
    public void testAssetListUploadWithAssetCreatePermissionIsAllowed() throws Exception {
        grants.add(StaticAssetImpl.class.getName() + ":" + EntityOperationType.ADD);
        String view = controller.upload(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new ExtendedModelMap(), new HashMap<>(), file, new LinkedMultiValueMap<>());
        assertEquals("redirect:/assets/5", view);
    }

    protected interface Handler {
        Object handle(String method, Object[] args) throws Exception;
    }

    @SuppressWarnings("unchecked")
    protected static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> handler.handle(method.getName(), args));
    }

}
