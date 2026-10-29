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

import org.broadleafcommerce.cms.common.AssetNotFoundException;
import org.broadleafcommerce.cms.file.domain.StaticAsset;
import org.broadleafcommerce.cms.file.domain.StaticAssetImpl;
import org.junit.Test;

import java.util.HashMap;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StaticAssetStorageServiceImplTest {

    @Test
    public void getCacheFileModelRejectsRequestedUrlWithParentDirectorySegment() throws Exception {
        final int[] lookups = {0};
        StaticAssetStorageServiceImpl storageService = new StaticAssetStorageServiceImpl() {
            @Override
            protected StaticAsset findStaticAsset(String fullUrl) {
                lookups[0]++;
                return null;
            }
        };

        assertAssetNotFound(storageService, "/a/b/c/../../../../application.properties");
        assertAssetNotFound(storageService, "\\a\\..\\..\\x.png");
        assertEquals(0, lookups[0]);
    }

    @Test
    public void getCacheFileModelRejectsStoredFullUrlWithParentDirectorySegment() throws Exception {
        final StaticAsset asset = new StaticAssetImpl();
        asset.setFullUrl("/a/b/c/../../../../application.properties");
        asset.setMimeType("text/plain");
        StaticAssetStorageServiceImpl storageService = new StaticAssetStorageServiceImpl() {
            @Override
            protected StaticAsset findStaticAsset(String fullUrl) {
                return asset;
            }
        };

        assertAssetNotFound(storageService, "/a/b/c/%2e%2e/%2e%2e/%2e%2e/%2e%2e/application.properties");
    }

    @Test
    public void fullUrlValidationRejectsParentDirectorySegments() {
        Pattern pattern = Pattern.compile(StaticAssetImpl.FULL_URL_REGEX);
        assertTrue(pattern.matcher("/product/image.png").matches());
        assertTrue(pattern.matcher("/product/my..image.png").matches());
        assertTrue(pattern.matcher("/product/.../image.png").matches());
        assertFalse(pattern.matcher("/a/b/c/../../../../application.properties").matches());
        assertFalse(pattern.matcher("../image.png").matches());
        assertFalse(pattern.matcher("/product/..").matches());
        assertFalse(pattern.matcher("\\product\\..\\image.png").matches());
        assertFalse(pattern.matcher("/product/x\n/../image.png").matches());
    }

    private void assertAssetNotFound(StaticAssetStorageServiceImpl storageService, String fullUrl) throws Exception {
        try {
            storageService.getCacheFileModel(fullUrl, new HashMap<>());
            fail("Expected AssetNotFoundException for " + fullUrl);
        } catch (AssetNotFoundException e) {
            // expected
        }
    }

}
