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
package org.broadleafcommerce.common.security.handler;

import org.apache.commons.lang3.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the CSRF token submitted with a request.
 * <p>
 * The token is read from the {@link #CSRF_TOKEN_HEADER} request header (used by AJAX requests) or from the request
 * body. A token supplied in the URL query string is rejected unless explicitly allowed, because URLs are written to
 * access logs, browser history and Referer headers, which would leak the session's token (CWE-598).
 * <p>
 * Reading the token from a {@code multipart/form-data} body via {@link HttpServletRequest#getParameter(String)}
 * requires multipart parsing to be enabled for the servlet (e.g. a {@code MultipartConfigElement} on the
 * {@code DispatcherServlet}, which Spring Boot registers automatically, or {@code <multipart-config>} in web.xml).
 */
public final class CsrfTokenRequestResolver {

    public static final String CSRF_TOKEN_HEADER = "X-CSRF-TOKEN";

    private CsrfTokenRequestResolver() {
    }

    public static String resolveToken(HttpServletRequest request, String parameterName, boolean allowQueryStringToken) {
        String headerToken = request.getHeader(CSRF_TOKEN_HEADER);
        if (StringUtils.isNotEmpty(headerToken)) {
            return headerToken;
        }
        if (!allowQueryStringToken && isInQueryString(request, parameterName)) {
            return null;
        }
        return request.getParameter(parameterName);
    }

    public static boolean isInQueryString(HttpServletRequest request, String parameterName) {
        String queryString = request.getQueryString();
        if (StringUtils.isEmpty(queryString) || StringUtils.isEmpty(parameterName)) {
            return false;
        }
        for (String pair : queryString.split("&")) {
            int idx = pair.indexOf('=');
            String rawName = idx >= 0 ? pair.substring(0, idx) : pair;
            String name;
            try {
                name = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                name = rawName;
            }
            if (parameterName.equals(name)) {
                return true;
            }
        }
        return false;
    }

}
