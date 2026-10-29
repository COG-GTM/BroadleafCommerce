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

import org.apache.commons.lang3.StringUtils;
import org.broadleafcommerce.common.exception.ServiceException;
import org.broadleafcommerce.common.web.controller.BroadleafAbstractController;
import org.broadleafcommerce.core.order.domain.NullOrderImpl;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.pricing.service.exception.PricingException;
import org.broadleafcommerce.core.web.order.CartState;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.service.CustomerService;
import org.broadleafcommerce.profile.web.controller.validator.RegisterCustomerValidator;
import org.broadleafcommerce.profile.web.core.form.RegisterCustomerForm;
import org.broadleafcommerce.profile.web.core.security.CustomerStateRequestProcessor;
import org.broadleafcommerce.profile.web.core.service.login.LoginService;
import org.broadleafcommerce.profile.web.core.service.register.RegistrationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;

import java.util.Objects;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * The controller responsible for registering a customer.
 * <p>
 * Uses a component registered with the name blCustomerValidator to perform validation of the
 * submitted customer.
 * <p>
 * Uses the property "useEmailForLogin" to determine if the username should be defaulted to the
 * email address if no username is supplied.
 *
 * @author apazzolini
 * @author bpolster
 */
public class BroadleafRegisterController extends BroadleafAbstractController {

    /**
     * Registration form properties that may never be populated from request parameters. Binding {@code customer.id}
     * would make the registration merge over an existing customer row.
     */
    protected static final String[] DISALLOWED_REGISTRATION_FIELDS = {
            "customer.id",
            "customer.*.id",
            "customer.password",
            "customer.passwordChangeRequired",
            "customer.registered",
            "customer.deactivated",
            "customer.anonymous",
            "customer.cookied",
            "customer.loggedIn",
            "customer.externalId",
            "customer.auditable*",
            "customer.customerAttributes*",
            "customer.customerAddresses*",
            "customer.customerPhones*",
            "customer.customerPayments*",
            "customer.customerLocale*",
            "oAuth2UserRequest*"
    };

    protected static String registerSuccessView = "ajaxredirect:";
    protected static String registerView = "authentication/register";
    @Value("${use.email.for.site.login:true}")
    protected boolean useEmailForLogin;
    @Resource(name = "blRegistrationService")
    protected RegistrationService registrationService;

    @Resource(name = "blCustomerService")
    protected CustomerService customerService;

    @Resource(name = "blRegisterCustomerValidator")
    protected RegisterCustomerValidator registerCustomerValidator;

    @Resource(name = "blLoginService")
    protected LoginService loginService;

    @Resource(name = "blOrderService")
    protected OrderService orderService;

    public String register(
            RegisterCustomerForm registerCustomerForm,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) {
        registrationService.addRedirectUrlToForm(registerCustomerForm);

        return getRegisterView();
    }

    public String processRegister(
            RegisterCustomerForm registerCustomerForm,
            BindingResult errors,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) throws ServiceException, PricingException {
        if (!isRegistrableCustomer(registerCustomerForm.getCustomer(), request)) {
            errors.reject("registration.invalidCustomer");
            return getRegisterView();
        }

        if (useEmailForLogin) {
            Customer customer = registerCustomerForm.getCustomer();
            customer.setUsername(customer.getEmailAddress());
        }

        registerCustomerValidator.validate(registerCustomerForm, errors, useEmailForLogin);
        if (!errors.hasErrors()) {
            Customer newCustomer = customerService.registerCustomer(
                    registerCustomerForm.getCustomer(),
                    registerCustomerForm.getPassword(),
                    registerCustomerForm.getPasswordConfirm()
            );
            assert (newCustomer != null);

            // The next line needs to use the customer from the input form and not the customer returned after registration
            // so that we still have the unencoded password for use by the authentication mechanism.
            loginService.loginCustomer(registerCustomerForm.getCustomer());

            // Need to ensure that the Cart on CartState is owned by the newly registered customer.
            Order cart = CartState.getCart();
            if (cart != null && !(cart instanceof NullOrderImpl) && cart.getEmailAddress() == null) {
                cart.setEmailAddress(newCustomer.getEmailAddress());
                orderService.save(cart, false);
            }

            String redirectUrl = registerCustomerForm.getRedirectUrl();
            if (StringUtils.isNotBlank(redirectUrl) && redirectUrl.contains(":")) {
                redirectUrl = null;
            }
            return StringUtils.isBlank(redirectUrl) ? getRegisterSuccessView() : "redirect:" + redirectUrl;
        } else {
            //Initially Customer was read from the db and is "attached to session",
            //later CustomerStateFilter does order "unlock", where it starts transaction,
            //and so on transaction commit session is flashed and changes to customer is persisted.
            //For this case we need to detach customer from session. QA#4356
            customerService.detachCustomer(registerCustomerForm.getCustomer());
            return getRegisterView();
        }
    }

    public RegisterCustomerForm initCustomerRegistrationForm() {
        return registrationService.initCustomerRegistrationForm();
    }

    @InitBinder
    public void initRegistrationBinder(WebDataBinder binder) {
        binder.setDisallowedFields(getDisallowedRegistrationFields());
    }

    protected String[] getDisallowedRegistrationFields() {
        return DISALLOWED_REGISTRATION_FIELDS;
    }

    /**
     * A registration may only create a new customer or convert the persisted anonymous customer of the current
     * session. Any other customer id on the submitted form means the request is trying to register over an existing
     * customer.
     */
    protected boolean isRegistrableCustomer(Customer customer, HttpServletRequest request) {
        if (customer == null) {
            return false;
        }
        Long customerId = customer.getId();
        return customerId == null || Objects.equals(customerId, getSessionAnonymousCustomerId(request));
    }

    protected Long getSessionAnonymousCustomerId(HttpServletRequest request) {
        HttpSession session = request == null ? null : request.getSession(false);
        if (session == null) {
            return null;
        }
        Object id = session.getAttribute(CustomerStateRequestProcessor.getAnonymousCustomerIdSessionAttributeName());
        return id instanceof Long ? (Long) id : null;
    }

    public boolean isUseEmailForLogin() {
        return useEmailForLogin;
    }

    public void setUseEmailForLogin(boolean useEmailForLogin) {
        this.useEmailForLogin = useEmailForLogin;
    }

    /**
     * Returns the view that will be returned from this controller when the
     * registration is successful.   The success view should be a redirect (e.g. start with "redirect:" since
     * this will cause the entire SpringSecurity pipeline to be fulfilled.
     * <p>
     * By default, returns "redirect:/"
     *
     * @return the register success view
     */
    public String getRegisterSuccessView() {
        return registerSuccessView;
    }

    /**
     * Returns the view that will be used to display the registration page.
     * <p>
     * By default, returns "/register"
     *
     * @return the register view
     */
    public String getRegisterView() {
        return registerView;
    }

}
