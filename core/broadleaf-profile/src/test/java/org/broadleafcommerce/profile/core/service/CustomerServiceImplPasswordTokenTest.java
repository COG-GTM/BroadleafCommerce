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

import org.broadleafcommerce.common.service.GenericResponse;
import org.broadleafcommerce.common.time.SystemTime;
import org.broadleafcommerce.profile.core.dao.CustomerForgotPasswordSecurityTokenDao;
import org.broadleafcommerce.profile.core.domain.CustomerForgotPasswordSecurityToken;
import org.broadleafcommerce.profile.core.domain.CustomerForgotPasswordSecurityTokenImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import junit.framework.TestCase;

public class CustomerServiceImplPasswordTokenTest extends TestCase {

    private static final String RAW_TOKEN = "abcdefghij0123456789";

    private InMemoryTokenDao tokenDao;
    private CustomerServiceImpl service;

    @Override
    protected void setUp() throws Exception {
        tokenDao = new InMemoryTokenDao();
        service = new CustomerServiceImpl();
        service.customerForgotPasswordSecurityTokenDao = tokenDao;
        service.passwordEncoderBean = new BCryptPasswordEncoder();
    }

    public void testEncodedTokenIsNotStoredInPlaintext() {
        String encoded = service.encodePassword(RAW_TOKEN);

        assertFalse(RAW_TOKEN.equals(encoded));
        assertTrue(encoded.startsWith("$2a$"));
        assertTrue(service.isPasswordValid(RAW_TOKEN, encoded));
    }

    public void testSaltedTokenIsResolvedWithoutCustomer() {
        CustomerForgotPasswordSecurityToken stored = storeToken(RAW_TOKEN, 1L, minutesAgo(5));
        storeToken("othertoken0123456789", 2L, minutesAgo(1));

        GenericResponse response = service.checkPasswordResetToken(RAW_TOKEN, null);

        assertFalse(response.getErrorCodesList().toString(), response.getHasErrors());
        assertSame(stored, service.checkPasswordResetToken(RAW_TOKEN, null, new GenericResponse()));
    }

    public void testWrongTokenIsRejectedWithoutCustomer() {
        storeToken(RAW_TOKEN, 1L, minutesAgo(5));

        GenericResponse response = service.checkPasswordResetToken("wrongtoken0123456789", null);

        assertTrue(response.getErrorCodesList().contains("invalidToken"));
    }

    public void testStoredHashIsNotAcceptedAsToken() {
        CustomerForgotPasswordSecurityToken stored = storeToken(RAW_TOKEN, 1L, minutesAgo(5));

        GenericResponse response = service.checkPasswordResetToken(stored.getToken(), null);

        assertTrue(response.getErrorCodesList().contains("invalidToken"));
    }

    public void testExpiredTokenIsRejectedWithoutCustomer() {
        storeToken(RAW_TOKEN, 1L, minutesAgo(service.getTokenExpiredMinutes() + 60));

        GenericResponse response = service.checkPasswordResetToken(RAW_TOKEN, null);

        assertTrue(response.getHasErrors());
    }

    public void testUsedTokenIsRejectedWithoutCustomer() {
        storeToken(RAW_TOKEN, 1L, minutesAgo(5)).setTokenUsedFlag(true);

        GenericResponse response = service.checkPasswordResetToken(RAW_TOKEN, null);

        assertTrue(response.getHasErrors());
    }

    public void testDeterministicEncoderStillUsesPrimaryKeyLookup() {
        service.passwordEncoderBean = NoOpPasswordEncoder.getInstance();
        storeToken(RAW_TOKEN, 1L, minutesAgo(service.getTokenExpiredMinutes() + 60));

        GenericResponse response = service.checkPasswordResetToken(RAW_TOKEN, null);

        assertEquals(1, response.getErrorCodesList().size());
        assertEquals("tokenExpired", response.getErrorCodesList().get(0));
    }

    public void testCandidateLookupIsBounded() {
        service.setMaxPasswordResetTokenCandidates(1);
        storeToken(RAW_TOKEN, 1L, minutesAgo(5));
        storeToken("newertoken0123456789", 2L, minutesAgo(1));

        GenericResponse response = service.checkPasswordResetToken(RAW_TOKEN, null);

        assertTrue(response.getErrorCodesList().contains("invalidToken"));
        assertEquals(1, tokenDao.lastMaxResults);
    }

    private CustomerForgotPasswordSecurityToken storeToken(String rawToken, Long customerId, Date createDate) {
        CustomerForgotPasswordSecurityToken token = new CustomerForgotPasswordSecurityTokenImpl();
        token.setToken(service.encodePassword(rawToken));
        token.setCustomerId(customerId);
        token.setCreateDate(createDate);
        return tokenDao.saveToken(token);
    }

    private Date minutesAgo(int minutes) {
        return new Date(SystemTime.asMillis() - minutes * 60000L);
    }

    private static class InMemoryTokenDao implements CustomerForgotPasswordSecurityTokenDao {

        private final List<CustomerForgotPasswordSecurityToken> tokens = new ArrayList<>();
        private int lastMaxResults = -1;

        @Override
        public CustomerForgotPasswordSecurityToken readToken(String token) {
            return tokens.stream().filter(t -> t.getToken().equals(token)).findFirst().orElse(null);
        }

        @Override
        public List<CustomerForgotPasswordSecurityToken> readUnusedTokensByCustomerId(Long customerId) {
            return tokens.stream()
                    .filter(t -> !t.isTokenUsedFlag() && t.getCustomerId().equals(customerId))
                    .collect(Collectors.toList());
        }

        @Override
        public List<CustomerForgotPasswordSecurityToken> readUnusedTokensCreatedAfter(Date createdAfter, int maxResults) {
            lastMaxResults = maxResults;
            return tokens.stream()
                    .filter(t -> !t.isTokenUsedFlag() && !t.getCreateDate().before(createdAfter))
                    .sorted(Comparator.comparing(CustomerForgotPasswordSecurityToken::getCreateDate).reversed())
                    .limit(maxResults)
                    .collect(Collectors.toList());
        }

        @Override
        public CustomerForgotPasswordSecurityToken saveToken(CustomerForgotPasswordSecurityToken token) {
            tokens.add(token);
            return token;
        }
    }
}
