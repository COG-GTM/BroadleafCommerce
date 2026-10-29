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
import org.broadleafcommerce.common.util.StringUtil;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class BroadleafAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    /**
     * Maps the fully-qualified classname of an exception thrown during an authentication failure to a view
     * to use as the destination. If the exception is not mapped, the defaultFailureUrl will be used.
     * <p>
     * Example of what you can add in your app's security configuration to map
     * {@link org.springframework.security.authentication.CredentialsExpiredException} to "/login/forcedPasswordChange":
     *
     * <pre>
     *      @Bean
     *      protected AuthenticationFailureHandler blAuthenticationFailureHandler(@Qualifier("blAuthenticationFailureRedirectStrategy") RedirectStrategy redirectStrategy) {
     *          final BroadleafAuthenticationFailureHandler response = new BroadleafAuthenticationFailureHandler("/login?error=true");
     *          response.setRedirectStrategy(redirectStrategy);
     *
     *          final Map<String, String> exceptionMappings = new HashMap<>();
     *          exceptionMappings.put("org.springframework.security.authentication.CredentialsExpiredException", "/login/forcedPasswordChange");
     *          response.setExceptionMappings(exceptionMappings);
     *
     *          return response;
     *      }
     *     </pre>
     * </p>
     */
    private final Map<String, String> failureUrlMap = new HashMap<>();
    private String defaultFailureUrl;

    public BroadleafAuthenticationFailureHandler() {
        super();
    }

    public BroadleafAuthenticationFailureHandler(String defaultFailureUrl) {
        super(defaultFailureUrl);
        this.defaultFailureUrl = defaultFailureUrl;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        final String failureUrlParam = StringUtil.cleanseUrlString(request.getParameter("failureUrl"));
        String successUrlParam = StringUtil.cleanseUrlString(request.getParameter("successUrl"));
        String failureUrl = StringUtils.trimToNull(failureUrlParam);

        // Verify that the url passed in is a servlet path and not a link redirecting away from the webapp.
        failureUrl = validateUrlParam(failureUrl);
        successUrlParam = validateUrlParam(successUrlParam);

        // check if the implementor has a particular view mapped for this exception
        if (failureUrl == null) {
            final String exceptionClassname = exception.getClass().getName();
            failureUrl = StringUtils.trimToNull(failureUrlMap.get(exceptionClassname));
        }

        if (failureUrl == null) {
            failureUrl = StringUtils.trimToNull(defaultFailureUrl);
        }

        if (failureUrl != null) {
            if (StringUtils.isNotEmpty(successUrlParam)) {
                String encodedSuccessUrl = URLEncoder.encode(successUrlParam, StandardCharsets.UTF_8);
                if (!failureUrl.contains("?")) {
                    failureUrl += "?successUrl=" + encodedSuccessUrl;
                } else {
                    failureUrl += "&successUrl=" + encodedSuccessUrl;
                }
            }

            saveException(request, exception);
            getRedirectStrategy().sendRedirect(request, response, failureUrl);
        } else {
            super.onAuthenticationFailure(request, response, exception);
        }
    }

    /**
     * Returns the url only if it is a path local to this application: it must start with a single {@code /}
     * followed by something other than {@code /} or {@code \\}, and must carry no scheme or authority.
     * Anything else (absolute urls, protocol-relative urls such as {@code //host}, backslashes,
     * control or whitespace characters) returns null.
     */
    public String validateUrlParam(final String url) {
        return isLocalPath(url) ? url : null;
    }

    protected boolean isLocalPath(final String url) {
        if (url == null || url.isEmpty() || url.charAt(0) != '/') {
            return false;
        }
        if (url.length() > 1 && (url.charAt(1) == '/' || url.charAt(1) == '\\')) {
            return false;
        }
        for (int i = 0; i < url.length(); i++) {
            final char c = url.charAt(i);
            if (c == '\\' || Character.isISOControl(c) || Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                return false;
            }
        }
        try {
            final URI uri = new URI(url);
            return uri.getScheme() == null
                    && uri.getRawAuthority() == null
                    && uri.getRawPath() != null
                    && uri.getRawPath().startsWith("/");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public void setExceptionMappings(final Map<String, String> failureUrlMap) {
        this.failureUrlMap.clear();
        this.failureUrlMap.putAll(failureUrlMap);
    }

}
