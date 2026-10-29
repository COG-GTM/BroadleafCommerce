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

import junit.framework.TestCase;

public class UrlUtilTest extends TestCase {

    public void testLocalPathsAreAllowed() {
        assertTrue(UrlUtil.isLocalRedirectPath("/"));
        assertTrue(UrlUtil.isLocalRedirectPath("/account"));
        assertTrue(UrlUtil.isLocalRedirectPath("/checkout/cart?step=1&x=a%2Fb"));
        assertTrue(UrlUtil.isLocalRedirectPath("/shop/account#orders"));
    }

    public void testProtocolRelativeAndBackslashTargetsAreRejected() {
        assertFalse(UrlUtil.isLocalRedirectPath("//evil.example"));
        assertFalse(UrlUtil.isLocalRedirectPath("//evil.example/path"));
        assertFalse(UrlUtil.isLocalRedirectPath("/\\evil.example"));
        assertFalse(UrlUtil.isLocalRedirectPath("\\\\evil.example"));
        assertFalse(UrlUtil.isLocalRedirectPath("/foo\\..\\\\evil.example"));
        assertFalse(UrlUtil.isLocalRedirectPath("///evil.example"));
    }

    public void testSchemesHostsAndUserInfoAreRejected() {
        assertFalse(UrlUtil.isLocalRedirectPath("https://evil.example"));
        assertFalse(UrlUtil.isLocalRedirectPath("javascript:alert(1)"));
        assertFalse(UrlUtil.isLocalRedirectPath("/redirect:https://evil.example"));
        assertFalse(UrlUtil.isLocalRedirectPath("/@evil.example"));
        assertFalse(UrlUtil.isLocalRedirectPath("evil.example"));
        assertFalse(UrlUtil.isLocalRedirectPath("account"));
    }

    public void testWhitespaceAndControlCharactersAreRejected() {
        assertFalse(UrlUtil.isLocalRedirectPath("/\t/evil.example"));
        assertFalse(UrlUtil.isLocalRedirectPath("/\n/evil.example"));
        assertFalse(UrlUtil.isLocalRedirectPath(" //evil.example"));
        assertFalse(UrlUtil.isLocalRedirectPath("/account\r\nSet-Cookie: x=y"));
        assertFalse(UrlUtil.isLocalRedirectPath("/a b"));
    }

    public void testBlankIsRejected() {
        assertFalse(UrlUtil.isLocalRedirectPath(null));
        assertFalse(UrlUtil.isLocalRedirectPath(""));
    }

}
