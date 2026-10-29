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

import org.apache.commons.text.StringEscapeUtils;

/**
 * Escapes values that are written into JavaScript string literals inside an inline {@code <script>} element.
 * <p>
 * Quotes, backslashes, control characters and non-ASCII characters are escaped with
 * {@link StringEscapeUtils#escapeEcmaScript(String)}, which also turns {@code /} into {@code \/}. In addition
 * {@code <} and {@code >} are written as unicode escapes so that a value can never close the script element or
 * open an HTML comment inside it.
 */
public final class JavaScriptStringUtils {

    private JavaScriptStringUtils() {
    }

    /**
     * @return the escaped contents of a JavaScript string literal (without surrounding quotes), or an empty string
     * for {@code null}
     */
    public static String escape(Object value) {
        if (value == null) {
            return "";
        }
        return StringEscapeUtils.escapeEcmaScript(String.valueOf(value))
                .replace("<", "\\u003C")
                .replace(">", "\\u003E");
    }

    /**
     * @return a single-quoted JavaScript string literal for the given value
     */
    public static String quote(Object value) {
        return "'" + escape(value) + "'";
    }

}
