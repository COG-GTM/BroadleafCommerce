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

import org.broadleafcommerce.common.util.StringUtil;
import org.broadleafcommerce.common.util.UrlUtil;
import org.broadleafcommerce.common.web.BroadleafSandBoxResolver;
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser;
import org.broadleafcommerce.openadmin.server.security.remote.SecurityVerifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import jakarta.annotation.Resource;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Jeff Fischer
 */
public class BroadleafAdminAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String successUrlParameter = "successUrl=";
    private static final int MAX_URL_DECODE_PASSES = 3;
    protected String loginUri = "/login"; // default login uri but can be overridden in admin security config
    @Resource(name = "blAdminSecurityRemoteService")
    protected SecurityVerifier adminRemoteSecurityService;
    private RequestCache requestCache = new HttpSessionRequestCache();

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws ServletException, IOException {
        AdminUser user = adminRemoteSecurityService.getPersistentAdminUser();
        if (user != null && user.getLastUsedSandBoxId() != null) {
            request.getSession(false).setAttribute(BroadleafSandBoxResolver.SANDBOX_ID_VAR, user.getLastUsedSandBoxId());
        }

        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest == null) {
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }

        String targetUrlParameter = getTargetUrlParameter();
        if (isAlwaysUseDefaultTargetUrl() || (targetUrlParameter != null
                && StringUtils.hasText(request.getParameter(targetUrlParameter)))) {
            requestCache.removeRequest(request, response);
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }

        clearAuthenticationAttributes(request);
        // Use the DefaultSavedRequest URL
        String targetUrl = savedRequest.getRedirectUrl();

        try {
            UrlUtil.validateUrl(targetUrl, request);
        } catch (IOException e) {
            logger.error("SECURITY FAILURE Bad redirect location: " + StringUtil.sanitize(targetUrl), e);
            response.sendError(403);
            return;
        }

        // Remove the sessionTimeout flag if necessary
        targetUrl = targetUrl.replace("sessionTimeout=true", "");
        if (targetUrl.charAt(targetUrl.length() - 1) == '?') {
            targetUrl = targetUrl.substring(0, targetUrl.length() - 1);
        }

        if (targetUrl.contains(successUrlParameter)) {
            int successUrlPosition = targetUrl.indexOf(successUrlParameter) + successUrlParameter.length();
            int nextParamPosition = targetUrl.indexOf("&", successUrlPosition);
            if (nextParamPosition == -1) {
                targetUrl = targetUrl.substring(successUrlPosition, targetUrl.length());
            } else {
                targetUrl = targetUrl.substring(successUrlPosition, nextParamPosition);
            }
        }

        // Remove the login URI so we don't continuously redirect to the login page
        targetUrl = removeLoginSegment(targetUrl);

        // The url was rewritten after the check above (e.g. replaced by the embedded successUrl value),
        // so the final redirect target must be validated on its own
        if (!isSameOriginRedirectUrl(targetUrl, request)) {
            logger.warn("SECURITY FAILURE Bad redirect location, using default target url instead: "
                    + StringUtil.sanitize(targetUrl));
            targetUrl = getDefaultTargetUrl();
        }

        logger.debug("Redirecting to DefaultSavedRequest Url: " + StringUtil.sanitize(targetUrl));

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    /**
     * Determines whether the given redirect target stays on the current host. The url must either be a host relative
     * path (starting with a single '/') or an absolute url whose scheme, host and port match the current request.
     * The leading part of the url decoded form is checked as well so that encoded schemes, authorities or
     * backslashes are rejected.
     *
     * @param url the final redirect target
     * @param request the current request
     * @return true if the url is safe to redirect to
     */
    protected boolean isSameOriginRedirectUrl(String url, HttpServletRequest request) {
        if (url == null) {
            return false;
        }
        String path = stripSameOrigin(url, request);
        if (!hasHostRelativePrefix(path)) {
            return false;
        }
        int queryPosition = indexOfQueryOrFragment(path);
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\' || Character.isWhitespace(c) || Character.isISOControl(c)) {
                return false;
            }
        }
        if (path.substring(0, queryPosition).contains("://")) {
            return false;
        }
        String current = url;
        for (int i = 0; i < MAX_URL_DECODE_PASSES; i++) {
            String decoded;
            try {
                decoded = URLDecoder.decode(current, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return false;
            }
            if (decoded.equals(current)) {
                return true;
            }
            String decodedPath = stripSameOrigin(decoded, request);
            if (!hasHostRelativePrefix(decodedPath)
                    || decodedPath.substring(0, indexOfQueryOrFragment(decodedPath)).contains("://")) {
                return false;
            }
            current = decoded;
        }
        return false;
    }

    protected boolean hasHostRelativePrefix(String path) {
        if (path.isEmpty()) {
            return true;
        }
        if (path.charAt(0) != '/') {
            return false;
        }
        if (path.length() == 1) {
            return true;
        }
        char second = path.charAt(1);
        return second != '/' && second != '\\' && !Character.isWhitespace(second) && !Character.isISOControl(second);
    }

    protected int indexOfQueryOrFragment(String path) {
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '?' || c == '#') {
                return i;
            }
        }
        return path.length();
    }

    /**
     * Removes a leading scheme, host and optional port from the url when they match the current request.
     * Urls targeting any other origin are returned unchanged so that they fail the host relative path check.
     */
    protected String stripSameOrigin(String url, HttpServletRequest request) {
        String origin = request.getScheme() + "://" + request.getServerName();
        if (!url.regionMatches(true, 0, origin, 0, origin.length())) {
            return url;
        }
        String remainder = url.substring(origin.length());
        String port = ":" + request.getServerPort();
        if (remainder.startsWith(port)) {
            remainder = remainder.substring(port.length());
        }
        if (remainder.isEmpty() || remainder.charAt(0) == '/') {
            return remainder;
        }
        return url;
    }

    /**
     * Given the instance attribute loginUri, removes the loginUri from the passed url when present
     *
     * @param url
     * @return String
     */
    protected String removeLoginSegment(String url) {
        if (StringUtils.isEmpty(url)) {
            return "/";
        }
        int lastSlashPos = url.lastIndexOf(loginUri);
        if (lastSlashPos >= 0) {
            return url.substring(0, lastSlashPos);
        } else {
            return url;
        }
    }

    public String getLoginUri() {
        return loginUri;
    }

    public void setLoginUri(String loginUri) {
        this.loginUri = loginUri;
    }

}
