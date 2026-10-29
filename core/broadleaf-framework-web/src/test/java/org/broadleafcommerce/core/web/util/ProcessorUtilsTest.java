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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

public class ProcessorUtilsTest {

    @Test
    public void encodePathKeepsOrdinaryPathsUnchanged() {
        assertEquals("/", ProcessorUtils.encodePath(""));
        assertEquals("/", ProcessorUtils.encodePath(null));
        assertEquals("/shop/hot-sauces", ProcessorUtils.encodePath("/shop/hot-sauces"));
        assertEquals("/ctx/search;jsessionid=AB12", ProcessorUtils.encodePath("/ctx/search;jsessionid=AB12"));
    }

    @Test
    public void encodePathDoesNotDoubleEncode() {
        assertEquals("/hot%20sauces/%22x", ProcessorUtils.encodePath("/hot%20sauces/%22x"));
    }

    @Test
    public void encodePathEncodesAttributeBreakingCharacters() {
        assertEquals("/a%22onmouseover=alert(1)%3E%3Cb%27%60",
                ProcessorUtils.encodePath("/a\"onmouseover=alert(1)><b'`"));
        assertEquals("/a%20b%09c", ProcessorUtils.encodePath("/a b\tc"));
        assertEquals("/%25zz%25", ProcessorUtils.encodePath("/%zz%"));
        assertEquals("/caf%C3%A9", ProcessorUtils.encodePath("/caf\u00e9"));
    }

    @Test
    public void getUrlWithEncodedBaseContainsNoQuotes() {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("sort", new String[]{"price asc"});
        String url = ProcessorUtils.getUrl(ProcessorUtils.encodePath("/search\" onfocus=\"alert(1)"), params);
        assertEquals("/search%22%20onfocus=%22alert(1)?sort=price+asc", url);
    }

    @Test
    public void requestBaseUrlsAreRelativeAndHandleContextPath() {
        HttpServletRequest request = request("/shop", "/shop/hot-sauces/\"x");
        assertEquals("/shop/hot-sauces/%22x", ProcessorUtils.getRequestBaseUrl(request));
        assertEquals("/hot-sauces/%22x", ProcessorUtils.getContextRelativeRequestBaseUrl(request));

        assertEquals("/", ProcessorUtils.getContextRelativeRequestBaseUrl(request("/shop", "/shop")));
        assertEquals("/search", ProcessorUtils.getContextRelativeRequestBaseUrl(request("", "/search")));
    }

    protected HttpServletRequest request(String contextPath, String requestUri) {
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getContextPath":
                            return contextPath;
                        case "getRequestURI":
                            return requestUri;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }
}
