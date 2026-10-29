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
 * Redirects back to the admin login page after a failed login, preserving the {@code successUrl} the user was trying to
 * reach. The {@code failureUrl} and {@code successUrl} request parameters and the {@code Referer} header are untrusted,
 * so only targets local to the application are honored; anything else falls back to the configured default.
 */
public class BroadleafAdminAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    protected static final String SUCCESS_URL_PARAM = "successUrl=";

    private String defaultFailureUrl;

    public BroadleafAdminAuthenticationFailureHandler() {
        super();
    }

    public BroadleafAdminAuthenticationFailureHandler(String defaultFailureUrl) {
        super(defaultFailureUrl);
        this.defaultFailureUrl = defaultFailureUrl;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        String failureUrlParam = StringUtil.cleanseUrlString(request.getParameter("failureUrl"));
        String successUrlParam = StringUtil.cleanseUrlString(request.getParameter("successUrl"));
        String failureUrl = validateLocalPath(StringUtils.trimToNull(failureUrlParam), "failureUrl");
        successUrlParam = validateLocalPath(StringUtils.trimToNull(successUrlParam), "successUrl");
        Boolean sessionTimeout = (Boolean) request.getAttribute("sessionTimeout");

        if (StringUtils.isEmpty(failureUrl) && BooleanUtils.isNotTrue(sessionTimeout)) {
            failureUrl = defaultFailureUrl;
        }

        if (BooleanUtils.isTrue(sessionTimeout)) {
            failureUrl = "?sessionTimeout=true";
        }

        if (StringUtils.isEmpty(successUrlParam)) {
            //Grab url the user, was redirected from
            successUrlParam = getSuccessUrlFromReferer(request);
        }

        if (failureUrl != null) {
            if (!StringUtils.isEmpty(successUrlParam)) {
                if (!failureUrl.contains("?")) {
                    failureUrl += "?" + SUCCESS_URL_PARAM + successUrlParam;
                } else {
                    failureUrl += "&" + SUCCESS_URL_PARAM + successUrlParam;
                }
            }

            saveException(request, exception);
            getRedirectStrategy().sendRedirect(request, response, failureUrl);
        } else {
            super.onAuthenticationFailure(request, response, exception);
        }
    }

    /**
     * Preserves the original successUrl from the referer. If the referer carries a successUrl parameter its value is
     * used, otherwise the referer itself. Referers from another host and successUrl values that are not local to the
     * application are dropped.
     */
    protected String getSuccessUrlFromReferer(HttpServletRequest request) {
        String referer = StringUtils.trimToNull(StringUtil.cleanseUrlString(request.getHeader("referer")));
        if (referer == null || !isSameHostUrl(referer, request)) {
            return null;
        }

        int successUrlPos = referer.indexOf(SUCCESS_URL_PARAM);
        if (successUrlPos < 0) {
            return referer;
        }

        String successUrl = referer.substring(successUrlPos + SUCCESS_URL_PARAM.length());
        int nextParamPos = successUrl.indexOf('&');
        if (nextParamPos >= 0) {
            successUrl = successUrl.substring(0, nextParamPos);
        }
        if (isLocalPath(successUrl) || isSameHostUrl(successUrl, request)) {
            return successUrl;
        }
        return null;
    }

    protected String validateLocalPath(String url, String paramName) {
        if (url == null || isLocalPath(url)) {
            return url;
        }
        logger.warn("SECURITY FAILURE Ignoring non-local " + paramName + " on admin login failure: "
                + StringUtil.sanitize(url));
        return null;
    }

    /**
     * @return true if the url is a path on this server, e.g. {@code /admin/login}. Absolute and protocol-relative urls
     * ({@code //host}, {@code /\host}) and urls containing backslashes or control characters (which browsers
     * strip or normalize) are rejected.
     */
    protected boolean isLocalPath(String url) {
        if (StringUtils.isEmpty(url) || url.charAt(0) != '/') {
            return false;
        }
        if (url.startsWith("//") || url.contains("\\") || url.contains("://")) {
            return false;
        }
        for (int i = 0; i < url.length(); i++) {
            if (Character.isISOControl(url.charAt(i)) || Character.isWhitespace(url.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return true if the url is an absolute http(s) url pointing at the host that served this request
     */
    protected boolean isSameHostUrl(String url, HttpServletRequest request) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && uri.getHost().equalsIgnoreCase(request.getServerName());
        } catch (URISyntaxException e) {
            return false;
        }
    }

}
