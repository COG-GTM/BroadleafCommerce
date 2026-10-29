/*-
 * #%L
 * BroadleafCommerce Framework Web
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
package org.broadleafcommerce.core.web.util;

import org.junit.Assert;
import org.junit.Test;

public class JavaScriptStringUtilsTest {

    @Test
    public void testNullIsEmptyLiteral() {
        Assert.assertEquals("", JavaScriptStringUtils.escape(null));
        Assert.assertEquals("''", JavaScriptStringUtils.quote(null));
    }

    @Test
    public void testPlainValueIsUnchanged() {
        Assert.assertEquals("'Hawt Like a Habanero 12'", JavaScriptStringUtils.quote("Hawt Like a Habanero 12"));
    }

    @Test
    public void testQuotesAndBackslashesAreEscaped() {
        Assert.assertEquals("\\\\\\';alert(1);\\/\\/", JavaScriptStringUtils.escape("\\';alert(1);//"));
        Assert.assertEquals("\\\"x\\\"", JavaScriptStringUtils.escape("\"x\""));
    }

    @Test
    public void testScriptTagCannotBeClosed() {
        String escaped = JavaScriptStringUtils.escape("</script><script>alert(1)</script><!--");
        Assert.assertFalse(escaped.contains("<"));
        Assert.assertFalse(escaped.contains(">"));
        Assert.assertEquals("\\u003C\\/script\\u003E\\u003Cscript\\u003Ealert(1)\\u003C\\/script\\u003E\\u003C!--",
                escaped);
    }

    @Test
    public void testLineTerminatorsAreEscaped() {
        Assert.assertEquals("a\\nb\\rc\\u2028d\\u2029", JavaScriptStringUtils.escape("a\nb\rc\u2028d\u2029"));
    }

}
