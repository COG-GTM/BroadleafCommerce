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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.broadleafcommerce.common.util.StringUtil;
import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.service.CustomerService;
import org.broadleafcommerce.profile.core.service.CustomerUserDetails;
import org.broadleafcommerce.profile.web.core.CustomerState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Resolves the customer a RESTful API request acts on behalf of and sets it on {@link CustomerState}.
 * This must come after the BroadleafRequestFilter (blRequestFilter) and after the Spring Security filters that
 * authenticate the caller.
 * <p>
 * The acting customer is bound to the authenticated caller:
 * <ul>
 *     <li>A {@code customerId} request attribute set by a trusted server-side component is honoured as-is.</li>
 *     <li>A {@code customerId} request parameter or header is only honoured when it identifies the authenticated
 *     caller's own customer, or when the caller holds one of the {@link #getImpersonationAuthorities() impersonation
 *     authorities} (by default {@value #DEFAULT_IMPERSONATION_AUTHORITY}), e.g. a CSR or trusted service client.
 *     Any other client-supplied {@code customerId} is rejected with {@code 403 Forbidden}.</li>
 *     <li>Without a {@code customerId}, the customer is resolved from the authenticated principal; anonymous callers
 *     get a new anonymous customer.</li>
 * </ul>
 * <p>
 * For RESTful services, this should be used instead of CustomerStateFilter since it does not look at or touch cookies or session.
 * <p>
 * <p/>
 * User: Kelly Tisdell
 * Date: 4/18/12
 */
public class RestApiCustomerStateFilter extends OncePerRequestFilter implements Ordered {

    public static final String CUSTOMER_ID_ATTRIBUTE = "customerId";
    public static final String BLC_RULE_MAP_PARAM = "blRuleMap";
    public static final String DEFAULT_IMPERSONATION_AUTHORITY = "ROLE_CUSTOMER_IMPERSONATION";
    protected static final Log LOG = LogFactory.getLog(RestApiCustomerStateFilter.class);
    @Autowired
    @Qualifier("blCustomerService")
    protected CustomerService customerService;
    protected AntPathMatcher pathMatcher = new AntPathMatcher();
    protected List<String> excludeUrlPatterns;
    protected Set<String> impersonationAuthorities = new HashSet<>(
            Collections.singleton(DEFAULT_IMPERSONATION_AUTHORITY));

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        //If someone already set the customer on the request then we don't need to do anything.
        if (request.getAttribute(CustomerStateRequestProcessor.getCustomerRequestAttributeName()) == null) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Customer customer;

            //A customerId request attribute can only have been set server-side, so it is trusted
            Object trustedCustomerId = request.getAttribute(CUSTOMER_ID_ATTRIBUTE);
            if (trustedCustomerId != null) {
                customer = readCustomer(String.valueOf(trustedCustomerId));
            } else {
                String requestedCustomerId = request.getParameter(CUSTOMER_ID_ATTRIBUTE);
                if (requestedCustomerId == null) {
                    requestedCustomerId = request.getHeader(CUSTOMER_ID_ATTRIBUTE);
                }

                Customer authenticatedCustomer = resolveAuthenticatedCustomer(authentication);
                customer = authenticatedCustomer;

                if (requestedCustomerId != null && requestedCustomerId.trim().length() > 0) {
                    Long requestedId = parseCustomerId(requestedCustomerId);
                    if (requestedId == null) {
                        LOG.warn(String.format("The customer id passed in '%s' was not a number",
                                StringUtil.sanitize(requestedCustomerId)));
                    } else if (authenticatedCustomer != null && requestedId.equals(authenticatedCustomer.getId())) {
                        customer = authenticatedCustomer;
                    } else if (canImpersonate(authentication)) {
                        customer = customerService.readCustomerById(requestedId);
                    } else {
                        LOG.warn(String.format("Rejecting API request to act as customer '%s': the caller is not that"
                                + " customer and does not hold an impersonation authority", requestedId));
                        response.sendError(HttpServletResponse.SC_FORBIDDEN);
                        return;
                    }
                } else if (LOG.isDebugEnabled() && customer == null) {
                    LOG.debug("No customer ID was found for the API request. In order to look up a customer for the request" +
                            " send a request parameter or request header for the '" + CUSTOMER_ID_ATTRIBUTE + "' attribute");
                }
            }

            if (customer != null) {
                CustomerState.setCustomer(customer);
                setupCustomerForRuleProcessing(customer, request);
            }
        }
        if (CustomerState.getCustomer() == null) { //we need to create an anonymous customer for API calls
            ServletWebRequest servletWebRequest = new ServletWebRequest(request, response);
            BroadleafRequestContext.getBroadleafRequestContext().setWebRequest(servletWebRequest);
            Customer customer = this.customerService.createCustomer();
            CustomerState.setCustomer(customer);
            setupCustomerForRuleProcessing(customer, request);
        }

        filterChain.doFilter(request, response);
    }

    protected Customer readCustomer(String customerId) {
        Long id = parseCustomerId(customerId);
        if (id == null) {
            LOG.warn(String.format("The customer id passed in '%s' was not a number", StringUtil.sanitize(customerId)));
            return null;
        }
        return customerService.readCustomerById(id);
    }

    protected Long parseCustomerId(String customerId) {
        if (!NumberUtils.isDigits(customerId.trim())) {
            return null;
        }
        try {
            return Long.valueOf(customerId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Resolves the customer that the authenticated caller is. Subclasses can extend to resolve other types of
     * {@link Authentication} (e.g. OAuth tokens whose principal is not a customer username).
     *
     * @return the authenticated customer, or null for anonymous / unauthenticated callers
     */
    protected Customer resolveAuthenticatedCustomer(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomerUserDetails && ((CustomerUserDetails) principal).getId() != null) {
            return customerService.readCustomerById(((CustomerUserDetails) principal).getId());
        }
        if (principal instanceof Customer && ((Customer) principal).getId() != null) {
            return customerService.readCustomerById(((Customer) principal).getId());
        }
        String username = authentication.getName();
        return username == null ? null : customerService.readCustomerByUsername(username);
    }

    /**
     * @return whether the caller may act on behalf of any customer by passing a customerId parameter or header
     */
    protected boolean canImpersonate(Authentication authentication) {
        if (!isAuthenticated(authentication) || CollectionUtils.isEmpty(impersonationAuthorities)) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (impersonationAuthorities.contains(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    protected boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        if (CollectionUtils.isNotEmpty(excludeUrlPatterns)) {
            return excludeUrlPatterns.stream()
                    .anyMatch(p -> pathMatcher.match(p, request.getServletPath()));
        }
        return false;
    }

    protected void setupCustomerForRuleProcessing(Customer customer, HttpServletRequest request) {
        // Setup customer for content rule processing
        @SuppressWarnings("unchecked")
        Map<String, Object> ruleMap = (Map<String, Object>) request.getAttribute(BLC_RULE_MAP_PARAM);
        if (ruleMap == null) {
            ruleMap = new HashMap<>();
        }
        ruleMap.put("customer", customer);
        request.setAttribute(BLC_RULE_MAP_PARAM, ruleMap);
    }

    @Override
    public int getOrder() {
        return 2000;
    }

    public String getCustomerIdAttributeName() {
        return CUSTOMER_ID_ATTRIBUTE;
    }

    public List<String> getExcludeUrlPatterns() {
        return excludeUrlPatterns;
    }

    public void setExcludeUrlPatterns(List<String> excludeUrlPatterns) {
        this.excludeUrlPatterns = excludeUrlPatterns;
    }

    public Set<String> getImpersonationAuthorities() {
        return impersonationAuthorities;
    }

    /**
     * Authorities that allow a caller to act on behalf of any customer via the customerId parameter or header.
     * An empty set disables impersonation.
     */
    public void setImpersonationAuthorities(Set<String> impersonationAuthorities) {
        this.impersonationAuthorities = impersonationAuthorities;
    }

}
