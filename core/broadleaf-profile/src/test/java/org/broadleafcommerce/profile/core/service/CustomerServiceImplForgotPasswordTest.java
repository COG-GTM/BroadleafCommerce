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
import org.broadleafcommerce.common.time.FixedTimeSource;
import org.broadleafcommerce.common.time.SystemTime;
import org.broadleafcommerce.profile.core.dao.CustomerDao;
import org.broadleafcommerce.profile.core.dao.CustomerForgotPasswordSecurityTokenDao;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerForgotPasswordSecurityToken;
import org.broadleafcommerce.profile.core.event.ForgotPasswordEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CustomerServiceImplForgotPasswordTest {

    private static final long CUSTOMER_ID = 42L;
    private static final String USERNAME = "alice";

    private long now = 1_800_000_000_000L;
    private CustomerServiceImpl service;
    private InMemoryTokenDao tokenDao;
    private CountingEncoder encoder;
    private List<ForgotPasswordEvent> sentEmails;
    private Customer customer;

    @Before
    public void setUp() {
        SystemTime.setGlobalTimeSource(new FixedTimeSource(now));
        customer = customer();
        tokenDao = new InMemoryTokenDao();
        encoder = new CountingEncoder();
        sentEmails = new ArrayList<>();

        service = new CustomerServiceImpl();
        service.customerForgotPasswordSecurityTokenDao = tokenDao;
        service.passwordEncoderBean = encoder;
        service.customerDao = customerDao(customer);
        service.eventPublisher = eventPublisher(sentEmails);
    }

    @After
    public void tearDown() {
        SystemTime.reset();
    }

    @Test
    public void issuingNewTokenInvalidatesEarlierTokens() {
        assertFalse(service.sendForgotPasswordNotification(USERNAME, null).getHasErrors());
        String first = sentEmails.get(0).getToken();
        advanceMinutes(1);
        assertFalse(service.sendForgotPasswordNotification(USERNAME, null).getHasErrors());
        String second = sentEmails.get(1).getToken();

        assertEquals(1, tokenDao.unusedCount());
        assertEquals(List.of("invalidToken"), service.checkPasswordResetToken(first, customer).getErrorCodesList());
        assertFalse(service.checkPasswordResetToken(second, customer).getHasErrors());
    }

    @Test
    public void resetAttemptComparesOnlyLiveTokens() {
        for (int i = 0; i < 5; i++) {
            service.sendForgotPasswordNotification(USERNAME, null);
            advanceMinutes(1);
        }
        encoder.matchCalls = 0;

        service.checkPasswordResetToken("bogus-token", customer);

        assertEquals(1, encoder.matchCalls);
    }

    @Test
    public void expiredTokensAreNotLoadedForComparison() {
        service.sendForgotPasswordNotification(USERNAME, null);
        String token = sentEmails.get(0).getToken();
        advanceMinutes(service.getTokenExpiredMinutes() + 1);
        encoder.matchCalls = 0;

        GenericResponse response = service.checkPasswordResetToken(token, customer);

        assertTrue(response.getHasErrors());
        assertEquals(0, encoder.matchCalls);
    }

    @Test
    public void forgotPasswordRequestsAreCappedPerCustomer() {
        int max = service.getMaxForgotPasswordTokensPerWindow();
        for (int i = 0; i < max; i++) {
            assertFalse(service.sendForgotPasswordNotification(USERNAME, null).getHasErrors());
        }

        GenericResponse blocked = service.sendForgotPasswordNotification(USERNAME, null);

        assertEquals(List.of("tooManyRequests"), blocked.getErrorCodesList());
        assertEquals(max, sentEmails.size());
        assertEquals(max, tokenDao.tokens.size());

        advanceMinutes(service.getForgotPasswordTokenWindowMinutes() + 1);
        assertFalse(service.sendForgotPasswordNotification(USERNAME, null).getHasErrors());
    }

    @Test
    public void successfulResetStillInvalidatesTheToken() {
        service.sendForgotPasswordNotification(USERNAME, null);
        String token = sentEmails.get(0).getToken();
        service.customerDao = customerDao(customer);

        GenericResponse response = service.resetPasswordUsingToken(USERNAME, token, "N3w-pass", "N3w-pass");

        assertFalse(response.getErrorCodesList().toString(), response.getHasErrors());
        assertEquals(0, tokenDao.unusedCount());
        assertEquals(List.of("invalidToken"), service.checkPasswordResetToken(token, customer).getErrorCodesList());
    }

    private void advanceMinutes(int minutes) {
        now += minutes * 60_000L;
        SystemTime.setGlobalTimeSource(new FixedTimeSource(now));
    }

    private static Customer customer() {
        Map<String, Object> props = new HashMap<>();
        props.put("getId", CUSTOMER_ID);
        props.put("getUsername", USERNAME);
        props.put("getEmailAddress", "alice@example.com");
        props.put("isDeactivated", false);
        props.put("isRegistered", true);
        return (Customer) Proxy.newProxyInstance(Customer.class.getClassLoader(), new Class[]{Customer.class},
                (proxy, method, args) -> {
                    if (props.containsKey(method.getName())) {
                        return props.get(method.getName());
                    }
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    return null;
                });
    }

    private static CustomerDao customerDao(Customer customer) {
        return (CustomerDao) Proxy.newProxyInstance(CustomerDao.class.getClassLoader(), new Class[]{CustomerDao.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "readCustomerByUsername":
                            return USERNAME.equals(args[0]) ? customer : null;
                        case "save":
                            return args[0];
                        default:
                            return null;
                    }
                });
    }

    private static BroadleafApplicationEventPublisher eventPublisher(List<ForgotPasswordEvent> sink) {
        return new BroadleafApplicationEventPublisher() {
            @Override
            public void publishEvent(ApplicationEvent applicationEvent) {
                if (applicationEvent instanceof ForgotPasswordEvent) {
                    sink.add((ForgotPasswordEvent) applicationEvent);
                }
            }

            @Override
            public void publishEventUnsynchronized(ApplicationEvent applicationEvent) {
                publishEvent(applicationEvent);
            }
        };
    }

    private static class CountingEncoder implements PasswordEncoder {

        private int counter;
        private int matchCalls;

        @Override
        public String encode(CharSequence rawPassword) {
            return "salt" + (counter++) + ":" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            matchCalls++;
            return encodedPassword != null && encodedPassword.endsWith(":" + rawPassword);
        }
    }

    private static class InMemoryTokenDao implements CustomerForgotPasswordSecurityTokenDao {

        private final Map<String, CustomerForgotPasswordSecurityToken> tokens = new HashMap<>();

        @Override
        public CustomerForgotPasswordSecurityToken readToken(String token) {
            return tokens.get(token);
        }

        @Override
        public List<CustomerForgotPasswordSecurityToken> readUnusedTokensByCustomerId(Long customerId) {
            return tokens.values().stream()
                    .filter(t -> t.getCustomerId().equals(customerId) && !t.isTokenUsedFlag())
                    .collect(Collectors.toList());
        }

        @Override
        public List<CustomerForgotPasswordSecurityToken> readUnusedTokensByCustomerId(Long customerId, Date createdAfter) {
            return readUnusedTokensByCustomerId(customerId).stream()
                    .filter(t -> t.getCreateDate().after(createdAfter))
                    .sorted(Comparator.comparing(CustomerForgotPasswordSecurityToken::getCreateDate).reversed())
                    .collect(Collectors.toList());
        }

        @Override
        public Long countTokensCreatedSince(Long customerId, Date createdAfter) {
            return tokens.values().stream()
                    .filter(t -> t.getCustomerId().equals(customerId) && t.getCreateDate().after(createdAfter))
                    .count();
        }

        @Override
        public CustomerForgotPasswordSecurityToken saveToken(CustomerForgotPasswordSecurityToken token) {
            tokens.put(token.getToken(), token);
            return token;
        }

        private long unusedCount() {
            return tokens.values().stream().filter(t -> !t.isTokenUsedFlag()).count();
        }
    }
}
