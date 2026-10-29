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
package org.broadleafcommerce.profile.web.core.form;

import org.broadleafcommerce.profile.core.domain.Customer;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;

import java.io.Serial;
import java.io.Serializable;

public class RegisterCustomerForm implements Serializable {

    /**
     * Request parameter paths that must never be bound onto a {@link RegisterCustomerForm}. The nested
     * {@link Customer} is a JPA entity, so binding these would let a registration request overwrite an
     * existing customer (e.g. via {@code customer.id}) or set privileged state on the new account.
     */
    public static final String[] DISALLOWED_BIND_FIELDS = new String[] {
            "customer.id",
            "customer.password",
            "customer.unencodedPassword",
            "customer.passwordChangeRequired",
            "customer.externalId",
            "customer.challengeAnswer",
            "customer.challengeQuestion.*",
            "customer.registered",
            "customer.anonymous",
            "customer.cookied",
            "customer.loggedIn",
            "customer.deactivated",
            "customer.preview",
            "customer.taxExempt*",
            "customer.auditable*",
            "customer.customerLocale*",
            "customer.customerAttributes*",
            "customer.customerAddresses*",
            "customer.customerPhones*",
            "customer.customerPayments*",
            "customer.customerRoles*"
    };

    @Serial
    private static final long serialVersionUID = 1L;

    protected Customer customer;
    protected String password;
    protected String passwordConfirm;
    protected String redirectUrl;
    protected String providerId;
    protected OAuth2UserRequest oAuth2UserRequest;

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPasswordConfirm() {
        return passwordConfirm;
    }

    public void setPasswordConfirm(String passwordConfirm) {
        this.passwordConfirm = passwordConfirm;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public OAuth2UserRequest getOAuth2UserRequest() {
        return oAuth2UserRequest;
    }

    public void setOAuth2UserRequest(OAuth2UserRequest oAuth2UserRequest) {
        this.oAuth2UserRequest = oAuth2UserRequest;
    }

}
