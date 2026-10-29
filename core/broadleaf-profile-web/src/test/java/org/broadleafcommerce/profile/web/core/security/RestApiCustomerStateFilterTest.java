/*-
 * #%L
 * BroadleafCommerce Profile Web
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
package org.broadleafcommerce.profile.web.core.security;

import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.common.web.BroadleafRequestCustomerResolverImpl;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;
import org.broadleafcommerce.profile.core.service.CustomerService;
import org.broadleafcommerce.profile.core.service.CustomerUserDetails;
import org.broadleafcommerce.profile.web.core.CustomerState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RestApiCustomerStateFilterTest {

    private static final Long ALICE_ID = 1042L;
    private static final Long MALLORY_ID = 2001L;

    private final Map<Long, Customer> customersById = new HashMap<>();
    private RestApiCustomerStateFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @Before
    public void setUp() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("blRequestCustomerResolver", BroadleafRequestCustomerResolverImpl.class);
        context.refresh();
        new BroadleafRequestCustomerResolverImpl().setApplicationContext(context);

        customersById.put(ALICE_ID, customer(ALICE_ID, "alice"));
        customersById.put(MALLORY_ID, customer(MALLORY_ID, "mallory"));

        filter = new RestApiCustomerStateFilter();
        filter.customerService = customerService();

        request = new MockHttpServletRequest("GET", "/api/v1/cart");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        BroadleafRequestContext brc = new BroadleafRequestContext();
        brc.setWebRequest(new ServletWebRequest(request, response));
        BroadleafRequestContext.setBroadleafRequestContext(brc);
    }

    @After
    public void tearDown() {
        SecurityContextHolder.clearContext();
        BroadleafRequestContext.setBroadleafRequestContext(null);
    }

    @Test
    public void anonymousCallerCannotSelectCustomerViaHeader() throws Exception {
        request.addHeader(RestApiCustomerStateFilter.CUSTOMER_ID_ATTRIBUTE, String.valueOf(ALICE_ID));

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
        assertNull(CustomerState.getCustomer());
    }

    @Test
    public void anonymousTokenCallerCannotSelectCustomerViaHeader() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        request.addHeader(RestApiCustomerStateFilter.CUSTOMER_ID_ATTRIBUTE, String.valueOf(ALICE_ID));

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    public void customerIdRequestParameterIsIgnored() throws Exception {
        request.setParameter(RestApiCustomerStateFilter.CUSTOMER_ID_ATTRIBUTE, String.valueOf(ALICE_ID));

        filter.doFilter(request, response, chain);

        assertSame(request, chain.getRequest());
        assertNull(CustomerState.getCustomer().getId());
        assertTrue(CustomerState.getCustomer().isAnonymous());
    }

    @Test
    public void customerIdRequestParameterCannotBypassImpersonationCheck() throws Exception {
        authenticate("csr-agent", RestApiCustomerStateFilter.DEFAULT_IMPERSONATION_AUTHORITY);
        request.setParameter(RestApiCustomerStateFilter.CUSTOMER_ID_ATTRIBUTE, String.valueOf(ALICE_ID));

        filter.doFilter(request, response, chain);

        assertSame(request, chain.getRequest());
        assertTrue(CustomerState.getCustomer().isAnonymous());
    }

    @Test
    public void authenticatedCustomerCannotActAsAnotherCustomer() throws Exception {
        authenticate("mallory", "ROLE_USER");
        request.addHeader(RestApiCustomerStateFilter.CUSTOMER_ID_ATTRIBUTE, String.valueOf(ALICE_ID));

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    public void authenticatedCustomerMayPassOwnCustomerId() throws Exception {
        authenticate("alice", "ROLE_USER");
        request.addHeader(RestApiCustomerStateFilter.CUSTOMER_ID_ATTRIBUTE, String.valueOf(ALICE_ID));

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertSame(request, chain.getRequest());
        assertEquals(ALICE_ID, CustomerState.getCustomer().getId());
    }

    @Test
    public void authenticatedCustomerIsResolvedFromPrincipalWithoutCustomerId() throws Exception {
        CustomerUserDetails principal = new CustomerUserDetails(ALICE_ID, "alice", "x",
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        filter.doFilter(request, response, chain);

        assertSame(request, chain.getRequest());
        assertEquals(ALICE_ID, CustomerState.getCustomer().getId());
    }

    @Test
    public void callerWithImpersonationAuthorityMayActAsAnyCustomer() throws Exception {
        authenticate("csr-agent", RestApiCustomerStateFilter.DEFAULT_IMPERSONATION_AUTHORITY);
        request.addHeader(RestApiCustomerStateFilter.CUSTOMER_ID_ATTRIBUTE, String.valueOf(ALICE_ID));

        filter.doFilter(request, response, chain);

        assertSame(request, chain.getRequest());
        assertEquals(ALICE_ID, CustomerState.getCustomer().getId());
    }

    @Test
    public void impersonationAuthoritiesAreConfigurable() throws Exception {
        filter.setImpersonationAuthorities(Collections.singleton("ROLE_TRUSTED_CLIENT"));
        authenticate("csr-agent", RestApiCustomerStateFilter.DEFAULT_IMPERSONATION_AUTHORITY);
        request.addHeader(RestApiCustomerStateFilter.CUSTOMER_ID_ATTRIBUTE, String.valueOf(ALICE_ID));

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
    }

    @Test
    public void serverSideCustomerIdAttributeIsTrusted() throws Exception {
        request.setAttribute(RestApiCustomerStateFilter.CUSTOMER_ID_ATTRIBUTE, ALICE_ID);

        filter.doFilter(request, response, chain);

        assertSame(request, chain.getRequest());
        assertEquals(ALICE_ID, CustomerState.getCustomer().getId());
    }

    @Test
    public void anonymousCallerWithoutCustomerIdGetsNewAnonymousCustomer() throws Exception {
        filter.doFilter(request, response, chain);

        assertSame(request, chain.getRequest());
        assertNull(CustomerState.getCustomer().getId());
        assertTrue(CustomerState.getCustomer().isAnonymous());
    }

    @Test
    public void nonNumericCustomerIdIsIgnored() throws Exception {
        request.addHeader(RestApiCustomerStateFilter.CUSTOMER_ID_ATTRIBUTE, "1.5e3");

        filter.doFilter(request, response, chain);

        assertSame(request, chain.getRequest());
        assertNull(CustomerState.getCustomer().getId());
    }

    private void authenticate(String username, String... authorities) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(username, null,
                AuthorityUtils.createAuthorityList(authorities));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static Customer customer(Long id, String username) {
        CustomerImpl customer = new CustomerImpl();
        customer.setId(id);
        customer.setUsername(username);
        return customer;
    }

    private CustomerService customerService() {
        return (CustomerService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CustomerService.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "readCustomerById":
                            return customersById.get((Long) args[0]);
                        case "readCustomerByUsername":
                            return customersById.values().stream()
                                    .filter(c -> c.getUsername().equals(args[0]))
                                    .findFirst().orElse(null);
                        case "createCustomer":
                            CustomerImpl anonymous = new CustomerImpl();
                            anonymous.setAnonymous(true);
                            return anonymous;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }
}
