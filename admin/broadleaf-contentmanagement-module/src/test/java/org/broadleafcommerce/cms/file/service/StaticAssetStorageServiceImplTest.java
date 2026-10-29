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

import org.broadleafcommerce.cms.field.type.StorageType;
import org.broadleafcommerce.cms.file.domain.StaticAsset;
import org.broadleafcommerce.cms.file.domain.StaticAssetImpl;
import org.broadleafcommerce.common.file.domain.FileWorkArea;
import org.broadleafcommerce.common.file.service.BroadleafFileService;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.mock.env.MockEnvironment;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StaticAssetStorageServiceImplTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private StaticAssetStorageServiceImpl storageService;
    private File workAreaDir;

    @Before
    public void setUp() throws IOException {
        workAreaDir = tempFolder.newFolder("parent", "workarea");
        FileWorkArea workArea = new FileWorkArea();
        workArea.setFilePathLocation(workAreaDir.getAbsolutePath() + File.separator);

        BroadleafFileService fileService = EasyMock.createNiceMock(BroadleafFileService.class);
        EasyMock.expect(fileService.initializeWorkArea()).andReturn(workArea).anyTimes();
        EasyMock.replay(fileService);

        storageService = new StaticAssetStorageServiceImpl();
        storageService.broadleafFileService = fileService;
        storageService.env = new MockEnvironment();
    }

    @Test
    public void testCreateStaticAssetStorageRejectsPathTraversal() throws IOException {
        File outside = new File(tempFolder.getRoot(), "evil.txt");
        assertRejected("/product/1/../../../../evil.txt");
        assertFalse(outside.exists());

        assertRejected("/product/1/..\\..\\..\\..\\evil.txt");
        assertFalse(outside.exists());
    }

    @Test
    public void testCreateStaticAssetStorageRejectsSiblingDirectoryWithSharedPrefix() throws IOException {
        File sibling = new File(workAreaDir.getParentFile(), "workarea-evil" + File.separator + "evil.txt");
        assertRejected("/../workarea-evil/evil.txt");
        assertFalse(sibling.exists());
    }

    @Test
    public void testCreateStaticAssetStorageRejectsMissingUrl() {
        assertRejected(null);
        assertRejected("/");
    }

    @Test
    public void testCreateStaticAssetStorageWritesInsideWorkArea() throws IOException {
        StaticAsset asset = filesystemAsset("/product/1/ok.txt");
        storageService.createStaticAssetStorage(new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)), asset);

        File written = new File(workAreaDir, "product" + File.separator + "1" + File.separator + "ok.txt");
        assertTrue(written.exists());
        assertEquals("ok", new String(Files.readAllBytes(written.toPath()), StandardCharsets.UTF_8));
    }

    private void assertRejected(String fullUrl) {
        try {
            storageService.createStaticAssetStorage(
                    new ByteArrayInputStream("evil".getBytes(StandardCharsets.UTF_8)), filesystemAsset(fullUrl));
            fail("Expected asset url to be rejected: " + fullUrl);
        } catch (IOException e) {
            // expected
        }
    }

    private StaticAsset filesystemAsset(String fullUrl) {
        StaticAsset asset = new StaticAssetImpl();
        asset.setFullUrl(fullUrl);
        asset.setStorageType(StorageType.FILESYSTEM);
        return asset;
    }

}
