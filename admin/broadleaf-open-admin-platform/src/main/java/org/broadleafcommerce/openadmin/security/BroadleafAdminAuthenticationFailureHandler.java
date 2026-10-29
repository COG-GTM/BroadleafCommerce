/*-
 * #%L
 * BroadleafCommerce Open Admin Platform
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
package org.broadleafcommerce.openadmin.security;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadleafcommerce.common.security.LocalRedirectStrategy;
import org.broadleafcommerce.common.util.StringUtil;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Redirects a failed admin login back to the login page. The {@code failureUrl} request parameter is only honored when
 * it is a local path, and the {@code successUrl} carried along (from the request parameter or the referer) is only kept
 * when it is a local path or an absolute URL on the requesting host, so a crafted login POST cannot redirect away from
 * the admin.
 */
public class BroadleafAdminAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final String SUCCESS_URL_PARAM = "successUrl";

    private String defaultFailureUrl;

    public BroadleafAdminAuthenticationFailureHandler() {
        super();
        setRedirectStrategy(new LocalRedirectStrategy());
    }

    public BroadleafAdminAuthenticationFailureHandler(String defaultFailureUrl) {
        super(defaultFailureUrl);
        this.defaultFailureUrl = defaultFailureUrl;
        setRedirectStrategy(new LocalRedirectStrategy());
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        String failureUrlParam = StringUtil.cleanseUrlString(request.getParameter("failureUrl"));
        String successUrlParam = StringUtil.cleanseUrlString(request.getParameter("successUrl"));
        String failureUrl = (failureUrlParam != null) ? failureUrlParam.trim() : null;
        if (!isSafeFailureUrl(failureUrl)) {
            failureUrl = null;
        }
        Boolean sessionTimeout = (Boolean) request.getAttribute("sessionTimeout");

        if (StringUtils.isEmpty(failureUrl) && BooleanUtils.isNotTrue(sessionTimeout)) {
            failureUrl = defaultFailureUrl;
        }

        if (BooleanUtils.isTrue(sessionTimeout)) {
            failureUrl = "?sessionTimeout=true";
        }

        if (StringUtils.isEmpty(successUrlParam)) {
            //Grab url the user, was redirected from
            successUrlParam = request.getHeader("referer");
        }

        if (failureUrl != null) {
            String successUrl = extractSuccessUrl(successUrlParam);
            if (isSafeSuccessUrl(successUrl, request)) {
                //Preserve the original successUrl from the referer.  If there is one, it must be the last url segment
                if (!failureUrl.contains("?")) {
                    failureUrl += "?" + SUCCESS_URL_PARAM + "=" + successUrl;
                } else {
                    failureUrl += "&" + SUCCESS_URL_PARAM + "=" + successUrl;
                }
            }

            saveException(request, exception);
            getRedirectStrategy().sendRedirect(request, response, failureUrl);
        } else {
            super.onAuthenticationFailure(request, response, exception);
        }
    }

    /**
     * Returns the successUrl value carried by {@code source}: either the value of an embedded {@code successUrl}
     * parameter (e.g. from a referer of the login page) or {@code source} itself.
     */
    protected String extractSuccessUrl(String source) {
        if (StringUtils.isEmpty(source)) {
            return null;
        }
        int successUrlPos = source.indexOf(SUCCESS_URL_PARAM);
        if (successUrlPos < 0) {
            return source;
        }
        String successUrl = source.substring(successUrlPos + SUCCESS_URL_PARAM.length());
        return successUrl.startsWith("=") ? successUrl.substring(1) : successUrl;
    }

    /**
     * The failureUrl must be a local path, both as given and once any remaining URL encoding is removed.
     */
    protected boolean isSafeFailureUrl(String url) {
        String decoded = fullyDecode(url);
        return isLocalPath(url) && isLocalPath(decoded);
    }

    /**
     * The successUrl must be a local path or an absolute URL on the requesting host, both as given (the referer is
     * not decoded) and once any remaining URL encoding is removed.
     */
    protected boolean isSafeSuccessUrl(String url, HttpServletRequest request) {
        String decoded = fullyDecode(url);
        if (decoded == null) {
            return false;
        }
        String singleDecoded = StringUtil.decodeUrl(url);
        return (isLocalPath(singleDecoded) || isSameHostUrl(singleDecoded, request))
                && (isLocalPath(decoded) || isSameHostUrl(decoded, request));
    }

    private String fullyDecode(String url) {
        if (url == null) {
            return null;
        }
        try {
            String decoded = url;
            for (int i = 0; i < 3; i++) {
                String next = StringUtil.decodeUrl(decoded);
                if (next.equals(decoded)) {
                    return decoded;
                }
                decoded = next;
            }
            return decoded.equals(StringUtil.decodeUrl(decoded)) ? decoded : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * A local path starts with a single {@code /}, so it cannot carry a scheme or authority, and contains no
     * backslash, whitespace or control characters that browsers might normalize into one.
     */
    protected boolean isLocalPath(String url) {
        if (StringUtils.isEmpty(url) || url.charAt(0) != '/') {
            return false;
        }
        if (url.length() > 1 && (url.charAt(1) == '/' || url.charAt(1) == '\\')) {
            return false;
        }
        return hasNoUnsafeCharacters(url);
    }

    /**
     * An absolute http(s) URL without user info whose host is the host the request was made to.
     */
    protected boolean isSameHostUrl(String url, HttpServletRequest request) {
        if (StringUtils.isEmpty(url) || !hasNoUnsafeCharacters(url)) {
            return false;
        }
        try {
            URI uri = new URI(url);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getRawUserInfo() == null
                    && uri.getHost() != null
                    && uri.getHost().equalsIgnoreCase(request.getServerName());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean hasNoUnsafeCharacters(String url) {
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '\\' || Character.isWhitespace(c) || Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }

}
