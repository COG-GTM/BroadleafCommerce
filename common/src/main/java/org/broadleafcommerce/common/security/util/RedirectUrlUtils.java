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
package org.broadleafcommerce.common.security.util;

import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Utilities used to determine whether a redirect target is local to the application. Targets that declare a scheme
 * (<code>https://evil.com</code>, <code>javascript:...</code>) or an authority (<code>//evil.com</code>, including the
 * backslash variants browsers normalize to <code>//</code>) are considered external and must never be sent to the
 * browser based on user supplied input.
 */
public final class RedirectUrlUtils {

    private RedirectUrlUtils() {
        // utility class
    }

    /**
     * Determines whether the given url stays within the application. Only relative targets are considered local:
     * context relative paths (a single leading slash), query/fragment only targets, and path relative targets that do
     * not declare a scheme.
     *
     * @param url the redirect target, typically originating from user input
     * @return true when the url can safely be used as a redirect target
     */
    public static boolean isLocalRedirectUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }

        String candidate = url.trim();
        if (containsControlCharacters(candidate)) {
            return false;
        }

        // Browsers treat backslashes in the authority position as slashes
        String normalized = candidate.replace('\\', '/');
        if (normalized.startsWith("//")) {
            return false;
        }
        if (normalized.startsWith("/") || normalized.startsWith("?") || normalized.startsWith("#")) {
            return true;
        }

        return !declaresScheme(normalized);
    }

    /**
     * Determines whether the given url stays within the application, additionally allowing absolute http(s) urls whose
     * host matches the host of the current request.
     *
     * @param url     the redirect target, typically originating from user input
     * @param request the current request
     * @return true when the url can safely be used as a redirect target
     */
    public static boolean isLocalRedirectUrl(String url, HttpServletRequest request) {
        if (isLocalRedirectUrl(url)) {
            return true;
        }
        if (StringUtils.isBlank(url) || containsControlCharacters(url.trim())) {
            return false;
        }

        try {
            URL urlObject = new URL(url.trim());
            boolean supportedProtocol = "http".equals(urlObject.getProtocol()) || "https".equals(urlObject.getProtocol());
            return supportedProtocol && StringUtils.equals(request.getServerName(), urlObject.getHost());
        } catch (MalformedURLException e) {
            return false;
        }
    }

    /**
     * Returns the given url when it {@link #isLocalRedirectUrl(String) is local}, otherwise null.
     *
     * @param url the redirect target, typically originating from user input
     * @return the local url or null when the url is external or empty
     */
    public static String sanitizeRedirectUrl(String url) {
        return isLocalRedirectUrl(url) ? url.trim() : null;
    }

    /**
     * Returns the given url when it {@link #isLocalRedirectUrl(String, HttpServletRequest) is local to the current
     * request}, otherwise null.
     *
     * @param url     the redirect target, typically originating from user input
     * @param request the current request
     * @return the local url or null when the url is external or empty
     */
    public static String sanitizeRedirectUrl(String url, HttpServletRequest request) {
        return isLocalRedirectUrl(url, request) ? url.trim() : null;
    }

    protected static boolean declaresScheme(String url) {
        int schemeEnd = url.indexOf(':');
        if (schemeEnd < 0) {
            return false;
        }

        // A colon appearing after the start of the path, query or fragment is not a scheme delimiter
        for (int i = 0; i < schemeEnd; i++) {
            char character = url.charAt(i);
            if (character == '/' || character == '?' || character == '#') {
                return false;
            }
        }

        return true;
    }

    protected static boolean containsControlCharacters(String url) {
        for (int i = 0; i < url.length(); i++) {
            if (url.charAt(i) < ' ' || url.charAt(i) == '\u007f') {
                return true;
            }
        }

        return false;
    }

}
