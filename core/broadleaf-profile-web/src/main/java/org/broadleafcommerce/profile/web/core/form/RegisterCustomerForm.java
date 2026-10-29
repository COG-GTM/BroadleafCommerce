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
import org.springframework.core.ResolvableType;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.web.bind.WebDataBinder;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class RegisterCustomerForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Customer properties that must never be populated from registration request parameters. Allowing them to be
     * bound would, for example, let a request supply the id of an existing customer and overwrite that account.
     */
    public static final String[] DISALLOWED_BINDING_FIELDS = {
            "customer.id",
            "customer.registered",
            "customer.deactivated",
            "customer.anonymous",
            "customer.cookied",
            "customer.loggedIn",
            "customer.password",
            "customer.unencodedPassword",
            "customer.passwordChangeRequired",
            "customer.externalId",
            "customer.taxExemptionCode",
            "customer.preview",
            "customer.auditable*"
    };

    /**
     * Prevents security-sensitive {@link #DISALLOWED_BINDING_FIELDS} from being bound onto a {@link RegisterCustomerForm}.
     * Binders for other targets are left untouched.
     */
    public static void restrictBinding(WebDataBinder binder) {
        if (!isRegisterCustomerFormBinder(binder)) {
            return;
        }
        Set<String> disallowed = new LinkedHashSet<>(Arrays.asList(DISALLOWED_BINDING_FIELDS));
        String[] existing = binder.getDisallowedFields();
        if (existing != null) {
            disallowed.addAll(Arrays.asList(existing));
        }
        binder.setDisallowedFields(disallowed.toArray(new String[0]));
    }

    protected static boolean isRegisterCustomerFormBinder(WebDataBinder binder) {
        Object target = binder.getTarget();
        if (target != null) {
            return target instanceof RegisterCustomerForm;
        }
        ResolvableType targetType = binder.getTargetType();
        return targetType != null && RegisterCustomerForm.class.isAssignableFrom(targetType.toClass());
    }

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
