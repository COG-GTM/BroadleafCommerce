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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @author apazzolini
 * <p>
 * Utility class for Thymeleaf Processors
 */
public class ProcessorUtils {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /**
     * Returns a relative, percent-encoded base URL for the current request that is safe to use as the base
     * of a link built by {@link #getUrl(String, Map)}. Unlike {@link HttpServletRequest#getRequestURL()}, the
     * result never contains the client-supplied Host header and every character outside the RFC 3986 path
     * character set (quotes, angle brackets, whitespace, ...) is percent-encoded.
     *
     * @param request the current request
     * @return the encoded request path, including the context path
     */
    public static String getRequestBaseUrl(HttpServletRequest request) {
        return encodePath(request.getRequestURI());
    }

    /**
     * Same as {@link #getRequestBaseUrl(HttpServletRequest)} but without the context path, for use in Spring
     * {@code redirect:} view names, which are resolved relative to the context.
     *
     * @param request the current request
     * @return the encoded request path relative to the context path
     */
    public static String getContextRelativeRequestBaseUrl(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (uri != null && contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return encodePath(uri);
    }

    /**
     * Percent-encodes every character of {@code path} that is not allowed in an RFC 3986 path. Existing
     * well-formed percent-encoded sequences are preserved so that already encoded paths are not double encoded.
     *
     * @param path a raw request path
     * @return the encoded path, or "/" when {@code path} is empty
     */
    public static String encodePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        byte[] bytes = path.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            if (b == '%' && i + 2 < bytes.length && isHex(bytes[i + 1]) && isHex(bytes[i + 2])) {
                sb.append('%').append((char) bytes[i + 1]).append((char) bytes[i + 2]);
                i += 2;
            } else if (isAllowedPathChar(b)) {
                sb.append((char) b);
            } else {
                sb.append('%').append(HEX[b >> 4]).append(HEX[b & 0x0F]);
            }
        }
        return sb.toString();
    }

    protected static boolean isAllowedPathChar(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || "-._~!$&()*+,;=:@/".indexOf(c) >= 0;
    }

    protected static boolean isHex(byte b) {
        return (b >= '0' && b <= '9') || (b >= 'a' && b <= 'f') || (b >= 'A' && b <= 'F');
    }

    /**
     * Gets a UTF-8 URL encoded URL based on the current URL as well as the specified map
     * of query string parameters
     *
     * @param baseUrl
     * @param parameters
     * @return the built URL
     */
    public static String getUrl(String baseUrl, Map<String, String[]> parameters) {
        if (baseUrl.contains("?")) {
            throw new IllegalArgumentException("baseUrl contained a ? indicating it is not a base url");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(baseUrl);

        boolean atLeastOneParam = false;

        if (parameters != null && parameters.size() > 0) {
            for (Entry<String, String[]> entry : parameters.entrySet()) {
                if (entry.getValue().length > 0) {
                    atLeastOneParam = true;
                }
            }
        }

        if (atLeastOneParam) {
            sb.append("?");
        } else {
            return sb.toString();
        }

        for (Entry<String, String[]> entry : parameters.entrySet()) {
            String key = entry.getKey();
            for (String value : entry.getValue()) {
                StringBuilder parameter = new StringBuilder();
                try {
                    parameter.append(URLEncoder.encode(key, "UTF-8"));
                    parameter.append("=");
                    parameter.append(URLEncoder.encode(value, "UTF-8"));
                    parameter.append("&");
                } catch (UnsupportedEncodingException e) {
                    parameter = null;
                }
                sb.append(parameter);
            }
        }

        String url = sb.toString();
        if (url.charAt(url.length() - 1) == '&') {
            url = url.substring(0, url.length() - 1);
        }

        return url;
    }

}
