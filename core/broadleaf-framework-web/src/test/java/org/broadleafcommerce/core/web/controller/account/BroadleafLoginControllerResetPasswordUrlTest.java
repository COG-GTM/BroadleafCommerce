/*-
 * #%L
 * BroadleafCommerce Framework Web
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
package org.broadleafcommerce.core.web.controller.account;

import org.broadleafcommerce.common.service.GenericResponse;
import org.broadleafcommerce.common.web.BaseUrlResolver;
import org.broadleafcommerce.profile.core.service.CustomerService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.ui.ExtendedModelMap;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.Assert.assertEquals;

public class BroadleafLoginControllerResetPasswordUrlTest {

    protected BroadleafLoginController controller;
    protected StandardEnvironment environment;
    protected String capturedResetPasswordUrl;

    @Before
    public void setUp() {
        environment = new StandardEnvironment();
        controller = new BroadleafLoginController();
        controller.environment = environment;
        controller.baseUrlResolver = new BaseUrlResolver() {
            @Override
            public String getSiteBaseUrl() {
                return "https://shop.example.com";
            }

            @Override
            public String getAdminBaseUrl() {
                return "https://admin.example.com";
            }
        };
        controller.customerService = (CustomerService) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{CustomerService.class}, (proxy, method, args) -> {
                    if ("sendForgotPasswordNotification".equals(method.getName())
                            || "sendForcedPasswordChangeNotification".equals(method.getName())) {
                        capturedResetPasswordUrl = (String) args[1];
                        return new GenericResponse();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    public void forgotPasswordIgnoresAttackerControlledHostHeader() {
        controller.processForgotPassword("alice", request("http", "mallory.example", 8666, "/store"),
                new ExtendedModelMap());

        assertEquals("https://shop.example.com" + "/authentication/resetPassword", capturedResetPasswordUrl);
    }

    @Test
    public void forcedPasswordChangeIgnoresAttackerControlledHostHeader() {
        controller.processForcedPasswordChange("alice", request("https", "mallory.example", 443, ""),
                new ExtendedModelMap());

        assertEquals("https://shop.example.com" + "/authentication/resetPassword", capturedResetPasswordUrl);
    }

    @Test
    public void usesConfiguredSiteBaseUrlEvenForItsOwnHost() {
        String url = controller.getResetPasswordUrl(request("http", "shop.example.com", 8080, "/ctx"));

        assertEquals("https://shop.example.com" + "/authentication/resetPassword", url);
    }

    @Test
    public void usesAllowlistedBaseUrlMatchingRequestHost() {
        allowedBaseUrls(" https://shop-a.example/ , https://shop-b.example:8443/store ");

        assertEquals("https://shop-b.example:8443/store" + "/authentication/resetPassword",
                controller.getResetPasswordUrl(request("http", "SHOP-B.example", 9999, "")));
        assertEquals("https://shop-a.example" + "/authentication/resetPassword",
                controller.getResetPasswordUrl(request("http", "shop-a.example", 80, "")));
    }

    @Test
    public void fallsBackToSiteBaseUrlWhenHostIsNotAllowlisted() {
        allowedBaseUrls("https://shop-a.example,not a url");

        assertEquals("https://shop.example.com" + "/authentication/resetPassword",
                controller.getResetPasswordUrl(request("https", "shop-a.example.mallory.example", 443, "")));
        assertEquals("https://shop.example.com" + "/authentication/resetPassword",
                controller.getResetPasswordUrl(request("https", null, 443, "")));
    }

    protected void allowedBaseUrls(String value) {
        Map<String, Object> props = new HashMap<>();
        props.put(BroadleafLoginController.RESET_PASSWORD_ALLOWED_BASE_URLS_PROPERTY, value);
        environment.getPropertySources().addFirst(new MapPropertySource("test", props));
    }

    protected HttpServletRequest request(String scheme, String serverName, int port, String contextPath) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getScheme":
                            return scheme;
                        case "getServerName":
                            return serverName;
                        case "getServerPort":
                            return port;
                        case "getContextPath":
                            return contextPath;
                        case "getHeader":
                            return "Host".equalsIgnoreCase((String) args[0]) ? serverName : null;
                        case "getAttribute":
                            return Boolean.FALSE;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }
}
