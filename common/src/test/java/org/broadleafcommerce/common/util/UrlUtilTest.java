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
package org.broadleafcommerce.common.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UrlUtilTest {

    @Test
    public void testLocalPathsAreSafeRedirects() {
        assertTrue(UrlUtil.isSafeRelativeRedirectUrl("/"));
        assertTrue(UrlUtil.isSafeRelativeRedirectUrl("/account"));
        assertTrue(UrlUtil.isSafeRelativeRedirectUrl("/store/checkout?step=2#payment"));
        assertTrue(UrlUtil.isSafeRelativeRedirectUrl("/search?q=%2F%2Fexample.org"));
    }

    @Test
    public void testOffHostTargetsAreNotSafeRedirects() {
        assertFalse(UrlUtil.isSafeRelativeRedirectUrl(null));
        assertFalse(UrlUtil.isSafeRelativeRedirectUrl(""));
        assertFalse(UrlUtil.isSafeRelativeRedirectUrl("//example.org"));
        assertFalse(UrlUtil.isSafeRelativeRedirectUrl("///example.org"));
        assertFalse(UrlUtil.isSafeRelativeRedirectUrl("/\\example.org"));
        assertFalse(UrlUtil.isSafeRelativeRedirectUrl("\\\\example.org"));
        assertFalse(UrlUtil.isSafeRelativeRedirectUrl("/\t/example.org"));
        assertFalse(UrlUtil.isSafeRelativeRedirectUrl("/\n/example.org"));
        assertFalse(UrlUtil.isSafeRelativeRedirectUrl(" //example.org"));
        assertFalse(UrlUtil.isSafeRelativeRedirectUrl("/ /example.org"));
        assertFalse(UrlUtil.isSafeRelativeRedirectUrl("/a\\..\\\\example.org"));
        assertFalse(UrlUtil.isSafeRelativeRedirectUrl("https://example.org"));
        assertFalse(UrlUtil.isSafeRelativeRedirectUrl("javascript:alert(1)"));
        assertFalse(UrlUtil.isSafeRelativeRedirectUrl("example.org"));
        assertFalse(UrlUtil.isSafeRelativeRedirectUrl("account"));
    }
}
