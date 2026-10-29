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

import org.apache.commons.io.FileUtils;
import org.broadleafcommerce.cms.field.type.StorageType;
import org.broadleafcommerce.cms.file.domain.StaticAsset;
import org.broadleafcommerce.cms.file.domain.StaticAssetImpl;
import org.broadleafcommerce.common.file.domain.FileWorkArea;
import org.broadleafcommerce.common.file.service.BroadleafFileService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StaticAssetStorageServiceImplTest {

    protected File root;
    protected File workArea;
    protected List<String> calls;
    protected StaticAssetStorageServiceImpl storageService;

    @Before
    public void setUp() throws IOException {
        root = Files.createTempDirectory("blc-asset-storage-test").toFile();
        workArea = new File(root, "base/ab/cd/1");
        calls = new ArrayList<>();
        storageService = new StaticAssetStorageServiceImpl();
        storageService.env = new MockEnvironment().withProperty("asset.server.max.uploadable.file.size", "1048576");
        storageService.broadleafFileService = (BroadleafFileService) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{BroadleafFileService.class},
                (proxy, method, args) -> {
                    calls.add(method.getName());
                    if ("initializeWorkArea".equals(method.getName())) {
                        workArea.mkdirs();
                        FileWorkArea fileWorkArea = new FileWorkArea();
                        fileWorkArea.setFilePathLocation(workArea.getAbsolutePath());
                        return fileWorkArea;
                    }
                    return null;
                });
    }

    @After
    public void tearDown() {
        FileUtils.deleteQuietly(root);
    }

    @Test
    public void testTraversalFullUrlIsNotWritten() {
        String fileName = "blc-traversal-test-" + UUID.randomUUID() + ".txt";
        File target = new File(root, fileName);
        StaticAsset asset = filesystemAsset("/product/1/../../../../../" + fileName);
        try {
            storageService.createStaticAssetStorage(
                    new ByteArrayInputStream("owned".getBytes(StandardCharsets.UTF_8)), asset);
            fail("Expected asset path outside of the work area to be rejected");
        } catch (IOException expected) {
            // expected
        }
        assertFalse(target.exists());
        assertFalse(calls.contains("addOrUpdateResource"));
        assertTrue(calls.contains("closeWorkArea"));
    }

    @Test
    public void testSiblingDirectoryWithSharedPrefixIsRejected() {
        StaticAsset asset = filesystemAsset("/../1-evil/x.txt");
        try {
            storageService.createStaticAssetStorage(new ByteArrayInputStream(new byte[]{1}), asset);
            fail("Expected sibling directory path to be rejected");
        } catch (IOException expected) {
            // expected
        }
        assertFalse(new File(workArea.getParentFile(), "1-evil/x.txt").exists());
    }

    @Test
    public void testPlainUploadIsWrittenInsideWorkArea() throws IOException {
        StaticAsset asset = filesystemAsset("/product/1/image.png");
        storageService.createStaticAssetStorage(
                new ByteArrayInputStream("png".getBytes(StandardCharsets.UTF_8)), asset);
        File written = new File(workArea, "product/1/image.png");
        assertTrue(written.exists());
        assertEquals("png", FileUtils.readFileToString(written, StandardCharsets.UTF_8));
        assertTrue(calls.contains("addOrUpdateResource"));
    }

    protected StaticAsset filesystemAsset(String fullUrl) {
        StaticAsset asset = new StaticAssetImpl();
        asset.setStorageType(StorageType.FILESYSTEM);
        asset.setFullUrl(fullUrl);
        asset.setFileSize(1L);
        return asset;
    }

}
