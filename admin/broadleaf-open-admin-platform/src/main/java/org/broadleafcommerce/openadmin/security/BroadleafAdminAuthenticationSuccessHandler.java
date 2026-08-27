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
        if (!StringUtils.hasText(targetUrl)) {
            targetUrl = "/";
        }

        // The url has been mutated since it was validated above (successUrl extraction, sessionTimeout removal,
        // login segment removal), so the exact value being redirected to must be validated again
        try {
            validateFinalRedirectUrl(targetUrl, request);
        } catch (IOException e) {
            logger.error("SECURITY FAILURE Bad redirect location: " + StringUtil.sanitize(targetUrl), e);
            response.sendError(403);
            return;
        }

        logger.debug("Redirecting to DefaultSavedRequest Url: " + StringUtil.sanitize(targetUrl));

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    /**
     * Validates the exact url that will be emitted in the redirect. The url must resolve to this host: an optional
     * same origin prefix is removed and the remainder must be a host relative path that satisfies the ESAPI redirect
     * allowlist. The url encoded form is checked as well so that encoded schemes or authorities cannot slip through.
     *
     * @param url the final redirect target
     * @param request the current request
     * @throws IOException if the url is not a valid redirect location
     */
    protected void validateFinalRedirectUrl(String url, HttpServletRequest request) throws IOException {
        if (!StringUtils.hasText(url)) {
            throw new IOException("Redirect failed");
        }
        validateHostRelativePath(url, request);
        String decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8);
        if (!decodedUrl.equals(url)) {
            validateHostRelativePath(decodedUrl, request);
        }
    }

    protected void validateHostRelativePath(String url, HttpServletRequest request) throws IOException {
        String path = stripSameOriginPrefix(url, request);
        if (!path.startsWith("/") || path.startsWith("//") || path.startsWith("/\\")) {
            throw new IOException("Redirect failed");
        }
        UrlUtil.validateUrl(path, request);
    }

    /**
     * Removes a leading scheme and authority from the given url when they match the current request, resulting in a
     * host relative path. Urls that carry a scheme or authority for a different origin are returned unchanged so that
     * they are subsequently rejected as non relative.
     *
     * @param url the url to normalize
     * @param request the current request
     * @return the host relative path when the url targets this origin, otherwise the url unchanged
     */
    protected String stripSameOriginPrefix(String url, HttpServletRequest request) {
        String sameOrigin = request.getScheme() + "://" + request.getServerName();
        if (!url.regionMatches(true, 0, sameOrigin, 0, sameOrigin.length())) {
            return url;
        }
        String remainder = url.substring(sameOrigin.length());
        String port = ":" + request.getServerPort();
        if (remainder.startsWith(port)) {
            remainder = remainder.substring(port.length());
        }
        if (remainder.isEmpty()) {
            return "/";
        }
        return remainder;
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
