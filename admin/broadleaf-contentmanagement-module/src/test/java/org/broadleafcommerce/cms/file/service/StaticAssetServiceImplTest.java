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

import org.broadleafcommerce.common.file.service.StaticAssetPathServiceImpl;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

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
    public void testBuildAssetURL() {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assertEquals("/product/100/img.png", assetService.buildAssetURL(assetProperties("product", "100"), "img.png"));
    }

    @Test
    public void testBuildAssetURLStripsPathFromFileName() {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        Map<String, String> properties = assetProperties("product", "100");

        assertEquals("/product/100/passwd", assetService.buildAssetURL(properties, "../../../../etc/passwd"));
        assertEquals("/product/100/evil.png", assetService.buildAssetURL(properties, "..\\..\\evil.png"));
        assertEquals("/product/100/img.png", assetService.buildAssetURL(properties, "/etc/img.png"));
        assertEquals("/product/100/img.png", assetService.buildAssetURL(properties, "..\u0000/img.png"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildAssetURLRejectsTraversalOnlyFileName() {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.buildAssetURL(assetProperties("product", "100"), "../..");
    }

    @Test
    public void testBuildAssetURLSanitizesEntitySegments() {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        Map<String, String> properties = assetProperties("../../product", "../100");

        assertEquals("/product/100/img.png", assetService.buildAssetURL(properties, "img.png"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildAssetURLRejectsTraversalOnlyEntityType() {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.buildAssetURL(assetProperties("..", "100"), "img.png");
    }

    @Test
    public void testBuildAssetURLKeepsExplicitFileNameProperty() {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        Map<String, String> properties = assetProperties("product", "100");
        properties.put("fileName", "folder/img.png");

        assertEquals("/product/100/folder/img.png", assetService.buildAssetURL(properties, "other.png"));
    }

    @Test
    public void testBuildAssetURLStripsProtocolFromFileNameProperty() {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        Map<String, String> properties = assetProperties("product", "100");
        properties.put("fileName", "http://images.mysite.com/folder/img.png");

        assertEquals("/product/100/images.mysite.com/folder/img.png", assetService.buildAssetURL(properties, "other.png"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildAssetURLRejectsTraversalInFileNameProperty() {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        Map<String, String> properties = assetProperties("product", "100");
        properties.put("fileName", "folder/../../../etc/passwd");

        assetService.buildAssetURL(properties, "img.png");
    }

    private Map<String, String> assetProperties(String entityType, String entityId) {
        Map<String, String> properties = new HashMap<>();
        properties.put("entityType", entityType);
        properties.put("entityId", entityId);
        return properties;
    }

}
