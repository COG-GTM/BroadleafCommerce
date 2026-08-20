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
import org.broadleafcommerce.common.security.util.RedirectUrlUtils;
import org.broadleafcommerce.common.util.StringUtil;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class BroadleafAdminAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    protected static final String SUCCESS_URL_PARAM = "successUrl=";

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
        // Only local targets are honored, otherwise the login failure could be used to redirect to an external site
        String failureUrlParam = RedirectUrlUtils.sanitizeRedirectUrl(
                StringUtil.cleanseUrlString(request.getParameter("failureUrl")));
        String successUrlParam = RedirectUrlUtils.sanitizeRedirectUrl(
                StringUtil.cleanseUrlString(request.getParameter("successUrl")));
        String failureUrl = failureUrlParam;
        Boolean sessionTimeout = (Boolean) request.getAttribute("sessionTimeout");

        if (StringUtils.isEmpty(failureUrl) && BooleanUtils.isNotTrue(sessionTimeout)) {
            failureUrl = defaultFailureUrl;
        }

        if (BooleanUtils.isTrue(sessionTimeout)) {
            failureUrl = "?sessionTimeout=true";
        }

        if (StringUtils.isEmpty(successUrlParam)) {
            //Grab url the user, was redirected from
            successUrlParam = extractSuccessUrlFromReferer(request);
        }

        if (failureUrl != null) {
            if (!StringUtils.isEmpty(successUrlParam)) {
                String successUrlSegment = SUCCESS_URL_PARAM + URLEncoder.encode(successUrlParam, StandardCharsets.UTF_8);

                if (!failureUrl.contains("?")) {
                    failureUrl += "?" + successUrlSegment;
                } else {
                    failureUrl += "&" + successUrlSegment;
                }
            }

            saveException(request, exception);
            getRedirectStrategy().sendRedirect(request, response, failureUrl);
        } else {
            super.onAuthenticationFailure(request, response, exception);
        }
    }

    /**
     * Preserves the original successUrl from the referer. Only targets local to the application are honored so that a
     * crafted referer cannot turn the login flow into a redirect to an external site.
     */
    protected String extractSuccessUrlFromReferer(HttpServletRequest request) {
        String referer = StringUtil.cleanseUrlString(request.getHeader("referer"));
        if (StringUtils.isEmpty(referer)) {
            return null;
        }

        String candidate = referer;
        int successUrlPos = referer.indexOf(SUCCESS_URL_PARAM);
        if (successUrlPos >= 0) {
            candidate = referer.substring(successUrlPos + SUCCESS_URL_PARAM.length());
            int nextParamPos = candidate.indexOf('&');
            if (nextParamPos >= 0) {
                candidate = candidate.substring(0, nextParamPos);
            }
        }

        return RedirectUrlUtils.sanitizeRedirectUrl(candidate, request);
    }

}
