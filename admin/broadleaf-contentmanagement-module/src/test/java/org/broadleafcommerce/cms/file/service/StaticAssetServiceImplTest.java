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
package org.broadleafcommerce.cms.file.service;

import org.broadleafcommerce.cms.file.domain.StaticAsset;
import org.broadleafcommerce.cms.file.domain.StaticAssetImpl;
import org.broadleafcommerce.common.file.service.StaticAssetPathServiceImpl;
import org.easymock.EasyMock;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * Created by bpolster.
 */
public class StaticAssetServiceImplTest {

    @Test
    public void testConvertURLProperties() throws Exception {
        StaticAssetPathServiceImpl staticAssetPathService = new StaticAssetPathServiceImpl();
        staticAssetPathService.setStaticAssetUrlPrefix("cmsstatic");
        staticAssetPathService.setStaticAssetEnvironmentUrlPrefix("http://images.mysite.com/myapp/cmsstatic");

        String url = staticAssetPathService.convertAssetPath("/cmsstatic/product.jpg", "myapp", false);
        assertTrue(url.equals("http://images.mysite.com/myapp/cmsstatic/product.jpg"));

        staticAssetPathService.setStaticAssetEnvironmentUrlPrefix("http://images.mysite.com");
        url = staticAssetPathService.convertAssetPath("/cmsstatic/product.jpg", "myapp", false);
        assertTrue(url.equals("http://images.mysite.com/product.jpg"));

        url = staticAssetPathService.convertAssetPath("/cmsstatic/product.jpg", "myapp", true);
        assertTrue(url.equals("https://images.mysite.com/product.jpg"));


        staticAssetPathService.setStaticAssetEnvironmentUrlPrefix(null);
        url = staticAssetPathService.convertAssetPath("/cmsstatic/product.jpg", "myapp", true);
        assertTrue(url.equals("/myapp/cmsstatic/product.jpg"));

        url = staticAssetPathService.convertAssetPath("cmsstatic/product.jpg", "myapp", true);
        assertTrue(url.equals("/myapp/cmsstatic/product.jpg"));

    }

    @Test
    public void testWhenEmptyFileExtensions() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        MockMultipartFile file = new MockMultipartFile("text.txt", this.getClass().getResourceAsStream("/testfile/text-file.txt"));
        assetService.validateFileExtension(file);
    }

    @Test(expected = IOException.class)
    public void testThrowDisabledFileExtensions() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setDisabledFileExtensions("txt");
        MockMultipartFile file = new MockMultipartFile("text.txt", this.getClass().getResourceAsStream("/testfile/text-file.txt"));
        assetService.validateFileExtension(file);
    }

    @Test
    public void testOkWhenMultipleDisableFileExtensions() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setDisabledFileExtensions("pdf,png");
        MockMultipartFile file = new MockMultipartFile("text.txt", this.getClass().getResourceAsStream("/testfile/text-file.txt"));
        assetService.validateFileExtension(file);
    }

    @Test
    public void testWhitelistFirstDisableFileExtensions() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setDisabledFileExtensions("pdf,png");
        assetService.setAllowedFileExtensions("txt,png");
        InputStream resourceAsStream = this.getClass().getResourceAsStream("/testfile/text-file.txt");
        MockMultipartFile file = new MockMultipartFile("text.txt", resourceAsStream);
        assetService.validateFileExtension(file);
        file = new MockMultipartFile("img.png", this.getClass().getResourceAsStream("/testfile/img.png"));
        assetService.validateFileExtension(file);
    }

    @Test(expected = IOException.class)
    public void testWhitelistFileExtensions() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setAllowedFileExtensions("txt");
        MockMultipartFile file = new MockMultipartFile("text.txt", this.getClass().getResourceAsStream("/testfile/text-file.txt"));
        assetService.validateFileExtension(file);
        file = new MockMultipartFile("img.png", this.getClass().getResourceAsStream("/testfile/img.png"));
        assetService.validateFileExtension(file);
    }

    @Test
    public void testCreateStaticAssetFromFileStripsPathTraversal() {
        StaticAssetServiceImpl assetService = new CapturingStaticAssetService();
        assetService.staticAssetStorageService = EasyMock.createNiceMock(StaticAssetStorageService.class);
        EasyMock.replay(assetService.staticAssetStorageService);

        MockMultipartFile file = new MockMultipartFile("file", "../../evil.txt", "text/plain", "evil".getBytes());
        StaticAsset asset = assetService.createStaticAssetFromFile(file, new HashMap<>());
        assertEquals("/evil.txt", asset.getFullUrl());

        Map<String, String> properties = new HashMap<>();
        properties.put("entityType", "product");
        properties.put("entityId", "1");
        file = new MockMultipartFile("file", "..\\..\\..\\evil.TXT", "text/plain", "evil".getBytes());
        asset = assetService.createStaticAssetFromFile(file, properties);
        assertEquals("/product/1/evil.txt", asset.getFullUrl());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateStaticAssetFromFileRejectsDotDotFileName() {
        StaticAssetServiceImpl assetService = new CapturingStaticAssetService();
        assetService.staticAssetStorageService = EasyMock.createNiceMock(StaticAssetStorageService.class);
        EasyMock.replay(assetService.staticAssetStorageService);

        MockMultipartFile file = new MockMultipartFile("file", "../..", "text/plain", "evil".getBytes());
        assetService.createStaticAssetFromFile(file, new HashMap<>());
    }

    @Test
    public void testSanitizeFileName() {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assertEquals("evil.txt", assetService.sanitizeFileName("../../evil.txt"));
        assertEquals("evil.txt", assetService.sanitizeFileName("..\\..\\evil.txt"));
        assertEquals("authorized_keys", assetService.sanitizeFileName("/home/acme/.ssh/authorized_keys"));
        assertEquals("product.jpg", assetService.sanitizeFileName("product.jpg"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSanitizeFileNameRejectsEmptyName() {
        new StaticAssetServiceImpl().sanitizeFileName("../");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildAssetURLRejectsTraversalInEntityId() {
        Map<String, String> properties = new HashMap<>();
        properties.put("entityType", "product");
        properties.put("entityId", "..");
        new StaticAssetServiceImpl().buildAssetURL(properties, "evil.txt");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildAssetURLRejectsSeparatorInEntityType() {
        Map<String, String> properties = new HashMap<>();
        properties.put("entityType", "../../etc");
        new StaticAssetServiceImpl().buildAssetURL(properties, "evil.txt");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildAssetURLRejectsTraversalInFileNameProperty() {
        Map<String, String> properties = new HashMap<>();
        properties.put("fileName", "/images/../../../evil.txt");
        new StaticAssetServiceImpl().buildAssetURL(properties, "evil.txt");
    }

    @Test
    public void testBuildAssetURLAllowsNestedFileNameProperty() {
        Map<String, String> properties = new HashMap<>();
        properties.put("fileName", "/images/products/shirt.jpg");
        assertEquals("/images/products/shirt.jpg", new StaticAssetServiceImpl().buildAssetURL(properties, "x.jpg"));

        properties = new HashMap<>();
        properties.put("entityType", "product");
        properties.put("entityId", "100");
        assertEquals("/product/100/shirt.jpg", new StaticAssetServiceImpl().buildAssetURL(properties, "shirt.jpg"));
    }

    private static class CapturingStaticAssetService extends StaticAssetServiceImpl {
        @Override
        public StaticAsset createStaticAsset(InputStream inputStream, String fileName, long fileSizeInBytes,
                                             Map<String, String> properties) {
            StaticAsset asset = new StaticAssetImpl();
            asset.setName(fileName);
            asset.setFullUrl(buildAssetURL(properties, sanitizeFileName(fileName)));
            return asset;
        }
    }

}
