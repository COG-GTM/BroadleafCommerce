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
package org.broadleafcommerce.common.security;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.web.RedirectStrategy;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * This class insures that if using the successUrl or failureUrl request
 * parameter, then the urls are valid and are local to the application
 * (preventing a user modifying to go somewhere else on login success/failure)
 */
public class LocalRedirectStrategy implements RedirectStrategy {

    private static final Log LOG = LogFactory.getLog(LocalRedirectStrategy.class);
    private static final int MAX_DECODE_PASSES = 3;
    private boolean contextRelative = false;
    private boolean enforcePortMatch = false;

    /*
     * (non-Javadoc)
     *
     * @see
     * org.springframework.security.web.RedirectStrategy#sendRedirect(javax.
     * servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse,
     * java.lang.String)
     */
    @Override
    public void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url) throws IOException {
        if (url.startsWith("/") || url.startsWith("\\")) {
            if (!isLocalPath(url)) {
                rejectRedirectUrl(url);
            }
        } else if (StringUtils.equals(request.getParameter("successUrl"), url)
                || StringUtils.equals(request.getParameter("failureUrl"), url)) {
            validateRedirectUrl(request.getContextPath(), url, request.getServerName(), request.getServerPort());
        }
        String redirectUrl = calculateRedirectUrl(request.getContextPath(), url);
        redirectUrl = response.encodeRedirectURL(redirectUrl);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Redirecting to '" + url + "'");
        }

        response.sendRedirect(redirectUrl);
    }

    /**
     * Create the redirect url
     *
     * @param contextPath
     * @param url
     * @return
     */
    protected String calculateRedirectUrl(String contextPath, String url) {
        if ((!(url.startsWith("http://"))) && (!(url.startsWith("https://")))) {
            if (this.contextRelative) {
                return url;
            }
            return contextPath + url;
        }

        if (!(this.contextRelative)) {
            return url;
        }

        url = url.substring(url.indexOf("://") + 3);
        url = url.substring(url.indexOf(contextPath) + contextPath.length());

        if ((url.length() > 1) && (url.charAt(0) == '/')) {
            url = url.substring(1);
        }

        return url;
    }

    /**
     * Insure the url is valid (must begin with http or https) and local to the
     * application
     *
     * @param contextPath       the application context path
     * @param url               the url to validate
     * @param requestServerName the server name of the request
     * @param requestServerPort the port of the request
     * @throws MalformedURLException if the url is invalid
     */
    protected void validateRedirectUrl(
            String contextPath,
            String url,
            String requestServerName,
            int requestServerPort
    ) throws MalformedURLException {
        URL urlObject = new URL(url);
        if (urlObject.getProtocol().equals("http") || urlObject.getProtocol().equals("https")) {
            if (StringUtils.equals(requestServerName, urlObject.getHost())) {
                if (!enforcePortMatch || requestServerPort == urlObject.getPort()) {
                    if (StringUtils.isEmpty(contextPath) || urlObject.getPath().startsWith("/" + contextPath)) {
                        return;
                    }
                }
            }
        }
        rejectRedirectUrl(url);
    }

    protected void rejectRedirectUrl(String url) throws MalformedURLException {
        String errorMessage = "Invalid redirect url specified.  Must be of the form /<relative view> or http[s]://<server name>[:<server port>][/<context path>]/...";
        LOG.warn(errorMessage + ":  " + url);
        throw new MalformedURLException(errorMessage + ":  " + url);
    }

    /**
     * Whether the url is a path on the current server: a single leading {@code /} followed by a path, with no
     * scheme, authority ({@code //host}), backslash, dot segment or whitespace/control characters. The path portion
     * is also checked after percent-decoding, so encoded delimiters such as {@code %2f}, {@code %5c} or
     * {@code %2e%2e} cannot hide an authority. Browsers treat {@code //host}, {@code /\host} and
     * {@code /<tab>/host} as links to {@code host}, so such values must never be used as a local redirect target.
     * The query and fragment may contain spaces (servlet parameters arrive decoded) but no control characters.
     *
     * @param url the url to check
     * @return true if the url is safe to use as a local redirect target
     */
    public static boolean isLocalPath(String url) {
        if (url == null) {
            return false;
        }
        int queryOrFragment = StringUtils.indexOfAny(url, "?#");
        String path = queryOrFragment < 0 ? url : url.substring(0, queryOrFragment);
        String queryAndFragment = queryOrFragment < 0 ? "" : url.substring(queryOrFragment);
        if (!hasLocalPathSyntax(path, false) || containsControlCharacter(queryAndFragment)) {
            return false;
        }
        for (int i = 0; i < MAX_DECODE_PASSES; i++) {
            if (hasDotSegment(path)) {
                return false;
            }
            String decoded;
            try {
                decoded = URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return false;
            }
            if (decoded.equals(path)) {
                return true;
            }
            if (!hasLocalPathSyntax(decoded, true)) {
                return false;
            }
            path = decoded;
        }
        return false;
    }

    private static boolean hasLocalPathSyntax(String value, boolean allowSpace) {
        if (value.isEmpty() || value.charAt(0) != '/') {
            return false;
        }
        if (value.length() > 1 && (value.charAt(1) == '/' || value.charAt(1) == '\\')) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || Character.isISOControl(c)
                    || ((Character.isWhitespace(c) || Character.isSpaceChar(c)) && !(allowSpace && c == ' '))) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDotSegment(String path) {
        for (String segment : StringUtils.splitPreserveAllTokens(path, "/?#;")) {
            if (".".equals(segment) || "..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This forces the redirect url port to match the request port. This could
     * be problematic when switching between secure and non-secure (e.g.
     * http://localhost:8080 to https://localhost:8443)
     *
     * @param enforcePortMatch
     */
    public void setEnforcePortMatch(boolean enforcePortMatch) {
        this.enforcePortMatch = enforcePortMatch;
    }

    /**
     * Set whether or not the context should be included in the redirect path. If true, the context
     * is excluded from the generated path, otherwise it is included.
     *
     * @param contextRelative
     */
    public void setContextRelative(boolean contextRelative) {
        this.contextRelative = contextRelative;
    }

}
