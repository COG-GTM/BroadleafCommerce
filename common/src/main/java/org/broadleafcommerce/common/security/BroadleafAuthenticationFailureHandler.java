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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
    private static final int MAX_DECODE_PASSES = 3;

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
     * Returns the url only if it is a path local to this application, otherwise null. See {@link #isLocalPath(String)}.
     */
    public String validateUrlParam(final String url) {
        return isLocalPath(url) ? url : null;
    }

    /**
     * A local path starts with a single {@code /} followed by something other than {@code /} or {@code \\}, so it
     * can carry neither a scheme nor an authority. Backslashes, whitespace and control characters are rejected because
     * browsers normalize or strip them. The check is repeated after each of up to {@value #MAX_DECODE_PASSES}
     * percent-decodings so an encoded value such as {@code /%2F%2Fhost} cannot become protocol-relative if a later
     * consumer decodes it again; values still changing after that are rejected.
     */
    protected boolean isLocalPath(final String url) {
        if (url == null) {
            return false;
        }
        String candidate = url;
        for (int pass = 0; pass <= MAX_DECODE_PASSES; pass++) {
            if (!isLocalPathSyntax(candidate)) {
                return false;
            }
            final String decoded = percentDecode(candidate);
            if (decoded.equals(candidate)) {
                return true;
            }
            candidate = decoded;
        }
        return false;
    }

    /**
     * Decodes valid {@code %XX} escapes as UTF-8, leaving malformed escapes and {@code +} untouched (unlike
     * {@link java.net.URLDecoder}, which throws on the former and turns the latter into a space).
     */
    private static String percentDecode(final String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        final StringBuilder result = new StringBuilder(value.length());
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int i = 0;
        while (i < value.length()) {
            final char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length() && isHex(value.charAt(i + 1)) && isHex(value.charAt(i + 2))) {
                bytes.write(Character.digit(value.charAt(i + 1), 16) << 4 | Character.digit(value.charAt(i + 2), 16));
                i += 3;
            } else {
                if (bytes.size() > 0) {
                    result.append(bytes.toString(StandardCharsets.UTF_8));
                    bytes.reset();
                }
                result.append(c);
                i++;
            }
        }
        if (bytes.size() > 0) {
            result.append(bytes.toString(StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    private static boolean isHex(final char c) {
        return Character.digit(c, 16) >= 0 && c < 128;
    }

    private static boolean isLocalPathSyntax(final String url) {
        if (url.isEmpty() || url.charAt(0) != '/') {
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
        return true;
    }

    public void setExceptionMappings(final Map<String, String> failureUrlMap) {
        this.failureUrlMap.clear();
        this.failureUrlMap.putAll(failureUrlMap);
    }

}
