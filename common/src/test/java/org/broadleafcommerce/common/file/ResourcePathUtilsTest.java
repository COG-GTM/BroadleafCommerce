/*-
 * #%L
 * BroadleafCommerce Common Libraries
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
package org.broadleafcommerce.common.file;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ResourcePathUtilsTest {

    @Test
    public void detectsParentDirectorySegments() {
        assertTrue(ResourcePathUtils.containsParentDirectorySegment(".."));
        assertTrue(ResourcePathUtils.containsParentDirectorySegment("/.."));
        assertTrue(ResourcePathUtils.containsParentDirectorySegment("../x.png"));
        assertTrue(ResourcePathUtils.containsParentDirectorySegment("/a/b/c/../../../../application.properties"));
        assertTrue(ResourcePathUtils.containsParentDirectorySegment("\\a\\..\\x.png"));
        assertTrue(ResourcePathUtils.containsParentDirectorySegment("/a/..\\x.png"));
    }

    @Test
    public void allowsNamesWithoutParentDirectorySegments() {
        assertFalse(ResourcePathUtils.containsParentDirectorySegment(null));
        assertFalse(ResourcePathUtils.containsParentDirectorySegment(""));
        assertFalse(ResourcePathUtils.containsParentDirectorySegment("/product/image.png"));
        assertFalse(ResourcePathUtils.containsParentDirectorySegment("/product/my..image.png"));
        assertFalse(ResourcePathUtils.containsParentDirectorySegment("/product/.../image.png"));
        assertFalse(ResourcePathUtils.containsParentDirectorySegment("/product/./image.png"));
        assertFalse(ResourcePathUtils.containsParentDirectorySegment("/product/..image/x.png"));
    }

}
