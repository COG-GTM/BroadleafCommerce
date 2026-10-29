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

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Fields of the nested {@link Customer} entity that must never be populated from request parameters during
     * registration. Binding these would allow a client to overwrite another customer's record (e.g. via
     * {@code customer.id}) or to set server-controlled state.
     */
    public static final String[] DISALLOWED_BINDING_FIELDS = {
            "customer.id",
            "customer.password",
            "customer.unencodedPassword",
            "customer.passwordChangeRequired",
            "customer.registered",
            "customer.deactivated",
            "customer.anonymous",
            "customer.cookied",
            "customer.loggedIn",
            "customer.preview",
            "customer.externalId",
            "customer.challengeAnswer",
            "customer.taxExemptionCode",
            "customer.auditable*",
            "customer.customerAttributes*",
            "customer.customerAddresses*",
            "customer.customerPhones*",
            "customer.customerPayments*"
    };

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
