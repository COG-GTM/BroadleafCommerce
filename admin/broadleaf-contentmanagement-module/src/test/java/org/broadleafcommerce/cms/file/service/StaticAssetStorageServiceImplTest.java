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

import org.broadleafcommerce.common.file.domain.FileWorkArea;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

public class StaticAssetStorageServiceImplTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private FileWorkArea workArea;

    @Before
    public void setUp() throws IOException {
        workArea = new FileWorkArea();
        workArea.setFilePathLocation(temporaryFolder.newFolder("workArea").getAbsolutePath());
    }

    @Test
    public void testValidateFileSystemAssetUrl() throws IOException {
        StaticAssetStorageServiceImpl storageService = new StaticAssetStorageServiceImpl();
        assertEquals("/product/100/img.png", storageService.validateFileSystemAssetUrl("/product/100/img.png"));
    }

    @Test(expected = IOException.class)
    public void testValidateFileSystemAssetUrlRejectsTraversal() throws IOException {
        StaticAssetStorageServiceImpl storageService = new StaticAssetStorageServiceImpl();
        storageService.validateFileSystemAssetUrl("/product/100/../../../../etc/cron.d/backdoor");
    }

    @Test(expected = IOException.class)
    public void testValidateFileSystemAssetUrlRejectsWindowsTraversal() throws IOException {
        StaticAssetStorageServiceImpl storageService = new StaticAssetStorageServiceImpl();
        storageService.validateFileSystemAssetUrl("\\product\\100\\..\\..\\evil.png");
    }

    @Test(expected = IOException.class)
    public void testValidateFileSystemAssetUrlRejectsNull() throws IOException {
        StaticAssetStorageServiceImpl storageService = new StaticAssetStorageServiceImpl();
        storageService.validateFileSystemAssetUrl(null);
    }

    @Test
    public void testAssetIsStoredRelativeToTheWorkArea() throws IOException {
        File destFile = new StaticAssetStorageServiceImpl().getDestinationFile(workArea, "/product/1/image.jpg");

        assertEquals(
                new File(workArea.getFilePathLocation(), "product" + File.separator + "1" + File.separator + "image.jpg")
                        .getAbsolutePath(),
                destFile.getAbsolutePath()
        );
    }

    @Test
    public void testParentDirectorySegmentIsRejected() {
        assertRejected("/product/../../../../../../opt/tomcat/webapps/ROOT/shell.jsp");
        assertRejected("\\product\\..\\..\\..\\shell.jsp");
    }

    @Test
    public void testUrlEscapingThroughASymlinkedDirectoryIsRejected() throws IOException {
        File outsideDirectory = temporaryFolder.newFolder("outside");
        Files.createSymbolicLink(
                new File(workArea.getFilePathLocation(), "link").toPath(),
                outsideDirectory.toPath()
        );

        assertRejected("/link/shell.jsp");
    }

    @Test
    public void testEmptyUrlIsRejected() {
        assertRejected("   ");
        assertRejected("/");
    }

    private void assertRejected(String fullUrl) {
        try {
            File destFile = new StaticAssetStorageServiceImpl().getDestinationFile(workArea, fullUrl);
            fail("Expected the asset url " + fullUrl + " to be rejected, but it resolved to " + destFile);
        } catch (IOException e) {
            assertTrue(e.getMessage().startsWith("Unable to store an asset"));
        }
    }

}
