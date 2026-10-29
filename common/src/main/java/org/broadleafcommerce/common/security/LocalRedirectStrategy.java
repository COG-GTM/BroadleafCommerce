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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * This class insures that if using the successUrl or failureUrl request
 * parameter, then the urls are valid and are local to the application
 * (preventing a user modifying to go somewhere else on login success/failure)
 */
public class LocalRedirectStrategy implements RedirectStrategy {

    private static final Log LOG = LogFactory.getLog(LocalRedirectStrategy.class);
    private static final Pattern ABSOLUTE_URL_PREFIX = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://[^/?#]*");
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
        if (url.startsWith("/") || isRedirectUrlParameter(request, url)) {
            validateRedirectUrl(request.getContextPath(), url, request.getServerName(), request.getServerPort());
        }
        String redirectUrl = calculateRedirectUrl(request.getContextPath(), url);
        redirectUrl = response.encodeRedirectURL(redirectUrl);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Redirecting to '" + url + "'");
        }

        response.sendRedirect(redirectUrl);
    }

    protected boolean isRedirectUrlParameter(HttpServletRequest request, String url) {
        return StringUtils.equals(request.getParameter("successUrl"), url)
                || StringUtils.equals(request.getParameter("failureUrl"), url);
    }

    /**
     * Whether the url contains syntax that browsers (or intermediaries that decode the url) may interpret as pointing
     * outside the application, i.e. a protocol-relative prefix ({@code //host}), a backslash (treated as {@code /} by
     * browsers), a whitespace / control character (stripped by browsers, so {@code /<tab>/host} becomes
     * {@code //host}), or a {@code .} / {@code ..} path segment. The path is also checked in percent-decoded form so
     * that encoded variants such as {@code /%2fhost}, {@code /%5chost} or {@code /%2e%2e/} are rejected.
     *
     * @param url the url to check
     * @return true if the url should not be used as a redirect target
     */
    public static boolean hasUnsafeRedirectSyntax(String url) {
        if (url == null) {
            return false;
        }
        if (url.startsWith("//") || containsUnsafeCharacter(url, true)) {
            return true;
        }
        Matcher absolutePrefix = ABSOLUTE_URL_PREFIX.matcher(url);
        boolean relative = !absolutePrefix.find();
        String path = relative ? url : url.substring(absolutePrefix.end());
        int queryOrFragment = StringUtils.indexOfAny(path, "?#");
        if (queryOrFragment >= 0) {
            path = path.substring(0, queryOrFragment);
        }
        for (int i = 0; i < MAX_DECODE_PASSES; i++) {
            if (hasDotSegment(path)) {
                return true;
            }
            String decoded = percentDecode(path);
            if (decoded.equals(path)) {
                return false;
            }
            path = decoded;
            if ((relative && path.startsWith("//")) || containsUnsafeCharacter(path, false)) {
                return true;
            }
        }
        return hasDotSegment(path) || !percentDecode(path).equals(path);
    }

    private static boolean containsUnsafeCharacter(String value, boolean rejectSpace) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || Character.isISOControl(c) || (Character.isWhitespace(c) && (rejectSpace || c != ' '))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDotSegment(String path) {
        for (String segment : path.split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static String percentDecode(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()
                    && Character.digit(value.charAt(i + 1), 16) >= 0 && Character.digit(value.charAt(i + 2), 16) >= 0) {
                bytes.write(Character.digit(value.charAt(i + 1), 16) << 4 | Character.digit(value.charAt(i + 2), 16));
                i += 2;
            } else {
                byte[] encoded = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                bytes.write(encoded, 0, encoded.length);
            }
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
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

        if (url.isEmpty()) {
            return contextPath + "/";
        }

        if ((url.length() > 1) && (url.charAt(0) == '/')) {
            url = url.substring(1);
        }

        return url;
    }

    /**
     * Insure the url is valid (must be a path relative to the server root, or begin with http or https) and local to
     * the application
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
        if (url != null && !hasUnsafeRedirectSyntax(url)) {
            if (url.startsWith("/")) {
                return;
            }
            URL urlObject;
            try {
                urlObject = new URL(url);
            } catch (MalformedURLException e) {
                urlObject = null;
            }
            if (urlObject != null && isLocalUrl(urlObject, contextPath, requestServerName, requestServerPort)) {
                return;
            }
        }
        String errorMessage = "Invalid redirect url specified.  Must be of the form /<relative view> or http[s]://<server name>[:<server port>][/<context path>]/...";
        LOG.warn(errorMessage + ":  " + url);
        throw new MalformedURLException(errorMessage + ":  " + url);
    }

    protected boolean isLocalUrl(URL urlObject, String contextPath, String requestServerName, int requestServerPort) {
        String protocol = urlObject.getProtocol();
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            return false;
        }
        if (urlObject.getUserInfo() != null || !StringUtils.equalsIgnoreCase(requestServerName, urlObject.getHost())) {
            return false;
        }
        if (enforcePortMatch && requestServerPort != urlObject.getPort()) {
            return false;
        }
        if (StringUtils.isEmpty(contextPath)) {
            return true;
        }
        String path = urlObject.getPath();
        return path.equals(contextPath) || path.startsWith(contextPath + "/");
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
