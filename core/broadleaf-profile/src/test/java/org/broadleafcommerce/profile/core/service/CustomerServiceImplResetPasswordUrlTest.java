/*-
 * #%L
 * BroadleafCommerce Profile
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
package org.broadleafcommerce.profile.core.service;

import org.broadleafcommerce.common.event.BroadleafApplicationEventPublisher;
import org.broadleafcommerce.common.service.GenericResponse;
import org.broadleafcommerce.common.web.BaseUrlResolver;
import org.broadleafcommerce.profile.core.dao.CustomerDao;
import org.broadleafcommerce.profile.core.dao.CustomerForgotPasswordSecurityTokenDao;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.event.ForgotPasswordEvent;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CustomerServiceImplResetPasswordUrlTest {

    protected CustomerServiceImpl service;
    protected StandardEnvironment environment;
    protected ForgotPasswordEvent publishedEvent;
    protected int savedTokens;

    @Before
    public void setUp() {
        environment = new StandardEnvironment();
        service = new CustomerServiceImpl();
        service.environment = environment;
        service.baseUrlResolver = new BaseUrlResolver() {
            @Override
            public String getSiteBaseUrl() {
                return "https://shop.example.com";
            }

            @Override
            public String getAdminBaseUrl() {
                return "https://admin.example.com";
            }
        };
        service.passwordEncoderBean = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "encoded:" + rawPassword;
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return encode(rawPassword).equals(encodedPassword);
            }
        };
        service.customerDao = proxy(CustomerDao.class, (p, m, a) -> {
            if ("readCustomerByUsername".equals(m.getName())) {
                return proxy(Customer.class, (cp, cm, ca) -> {
                    switch (cm.getName()) {
                        case "getId":
                            return 42L;
                        case "getEmailAddress":
                            return "alice@example.com";
                        case "isDeactivated":
                            return false;
                        default:
                            throw new UnsupportedOperationException(cm.getName());
                    }
                });
            }
            throw new UnsupportedOperationException(m.getName());
        });
        service.customerForgotPasswordSecurityTokenDao = proxy(CustomerForgotPasswordSecurityTokenDao.class,
                (p, m, a) -> {
                    savedTokens++;
                    return a[0];
                });
        service.eventPublisher = proxy(BroadleafApplicationEventPublisher.class, (p, m, a) -> {
            publishedEvent = (ForgotPasswordEvent) a[0];
            return null;
        });
    }

    @Test
    public void appendsTokenToSiteBaseUrl() {
        GenericResponse response = service.sendForgotPasswordNotification("alice",
                "https://shop.example.com/authentication/resetPassword");

        assertFalse(response.getHasErrors());
        assertEquals(1, savedTokens);
        assertEquals("https://shop.example.com/authentication/resetPassword?token=" + publishedEvent.getToken(),
                publishedEvent.getResetPasswordUrl());
    }

    @Test
    public void dropsResetPasswordUrlPointingAtForeignHost() {
        GenericResponse response = service.sendForgotPasswordNotification("alice",
                "http://acme-store.evil.example/authentication/resetPassword");

        assertFalse(response.getHasErrors());
        assertNotNull(publishedEvent.getToken());
        assertNull(publishedEvent.getResetPasswordUrl());
    }

    @Test
    public void forcedPasswordChangeAlsoDropsForeignHost() {
        service.sendForcedPasswordChangeNotification("alice", "https://evil.example/reset?x=1");

        assertNull(publishedEvent.getResetPasswordUrl());
    }

    @Test
    public void rejectsLookalikeAndNonHttpUrls() {
        assertFalse(service.isAllowedResetPasswordUrl("https://shop.example.com.evil.example/reset"));
        assertFalse(service.isAllowedResetPasswordUrl("https://shop.example.com@evil.example/reset"));
        assertFalse(service.isAllowedResetPasswordUrl("//evil.example/reset"));
        assertFalse(service.isAllowedResetPasswordUrl("javascript:alert(1)"));
        assertFalse(service.isAllowedResetPasswordUrl("ftp://shop.example.com/reset"));
        assertFalse(service.isAllowedResetPasswordUrl("https://shop.example.com/a b"));
    }

    @Test
    public void allowsSiteHostAndRelativeUrls() {
        assertTrue(service.isAllowedResetPasswordUrl("http://SHOP.example.com:8080/ctx/reset"));
        assertTrue(service.isAllowedResetPasswordUrl("/authentication/resetPassword"));
    }

    @Test
    public void allowsHostsFromAllowedBaseUrlsProperty() {
        environment.getPropertySources().addFirst(new MapPropertySource("test", Collections.singletonMap(
                CustomerServiceImpl.RESET_PASSWORD_ALLOWED_BASE_URLS_PROPERTY,
                " https://shop-a.example/ , not a url, https://shop-b.example:8443/store ")));

        assertTrue(service.isAllowedResetPasswordUrl("https://shop-b.example/store/reset"));
        assertTrue(service.isAllowedResetPasswordUrl("https://shop-a.example/reset"));
        assertFalse(service.isAllowedResetPasswordUrl("https://shop-c.example/reset"));
    }

    @SuppressWarnings("unchecked")
    protected <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, handler);
    }
}
