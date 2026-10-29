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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

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
    public void testTraversalUploadFileNameIsRejected() {
        File target = new File(System.getProperty("java.io.tmpdir"), "blc-traversal-test.txt");
        target.delete();
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../../../tmp/blc-traversal-test.txt", "text/plain", "owned".getBytes());
        Map<String, String> properties = new HashMap<>();
        properties.put("entityType", "product");
        properties.put("entityId", "1");
        try {
            assetService.createStaticAssetFromFile(file, properties);
            fail("Expected traversal file name to be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        assertFalse(target.exists());
        assertFalse(new File("/tmp/blc-traversal-test.txt").exists());
    }

    @Test
    public void testUnsafeUploadFileNamesAreRejected() {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        String[] unsafeNames = {
                null, "", "   ", "..", ".", "../image.png", "a/../../image.png", "..\\..\\image.png",
                "a\\..\\image.png", ".htaccess", "dir/.hidden", "image\u0000.png", "image\n.png"
        };
        for (String name : unsafeNames) {
            try {
                assetService.getSafeUploadFileName(name);
                fail("Expected file name to be rejected: " + name);
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void testUploadFileNameIsReducedToLastSegment() {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assertEquals("image.png", assetService.getSafeUploadFileName("image.png"));
        assertEquals("image.png", assetService.getSafeUploadFileName("C:\\Users\\me\\image.png"));
        assertEquals("image.png", assetService.getSafeUploadFileName("photos/image.png"));
        assertEquals("my..image.png", assetService.getSafeUploadFileName("my..image.png"));
    }

    @Test
    public void testPlainUploadBuildsUrlUnderEntityPath() {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        Map<String, String> properties = new HashMap<>();
        properties.put("entityType", "product");
        properties.put("entityId", "1");
        String url = assetService.buildAssetURL(properties, assetService.getSafeUploadFileName("image.png"));
        assertEquals("/product/1/image.png", url);
        assetService.validateAssetUrl(url);
    }

    @Test
    public void testCreateStaticAssetRejectsTraversalInFileNameProperty() {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        String[] unsafeFileNames = {"../../../../etc/cron.d/x", "/a/../../x.png", "..\\..\\x.png", "a/./b.png"};
        for (String unsafe : unsafeFileNames) {
            Map<String, String> properties = new HashMap<>();
            properties.put("fileName", unsafe);
            try {
                assetService.createStaticAsset(new ByteArrayInputStream(new byte[0]), "x.png", 0, properties);
                fail("Expected asset URL to be rejected: " + unsafe);
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void testCreateStaticAssetRejectsTraversalInPassedFileName() {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        Map<String, String> properties = new HashMap<>();
        properties.put("entityType", "product");
        try {
            assetService.createStaticAsset(new ByteArrayInputStream(new byte[0]), "../../../x.jsp", 0, properties);
            fail("Expected asset URL to be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

}
