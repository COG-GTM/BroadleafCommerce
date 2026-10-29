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
package org.broadleafcommerce.profile.web.controller;

import org.apache.commons.validator.GenericValidator;
import org.broadleafcommerce.common.persistence.EntityConfiguration;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerPhone;
import org.broadleafcommerce.profile.core.domain.Phone;
import org.broadleafcommerce.profile.core.service.CustomerPhoneService;
import org.broadleafcommerce.profile.web.controller.validator.CustomerPhoneValidator;
import org.broadleafcommerce.profile.web.controller.validator.PhoneValidator;
import org.broadleafcommerce.profile.web.core.CustomerState;
import org.broadleafcommerce.profile.web.core.model.PhoneNameForm;
import org.broadleafcommerce.profile.web.core.util.PhoneFormatter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ValidationUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Provides access and mutator functions to manage a customer's phones.
 *
 * @author sconlon
 */
@Controller("blCustomerPhoneController")
@RequestMapping("/myaccount/phone")
public class CustomerPhoneController {

    private static final String prefix = "myAccount/phone/customerPhones";
    private static final String redirect = "redirect:/myaccount/phone/viewPhone.htm";

    /**
     * The only {@link PhoneNameForm} properties that may be bound from the request. Ids are deliberately excluded so
     * a request can never point the form at another customer's rows.
     */
    protected static final String[] ALLOWED_PHONE_NAME_FORM_FIELDS = {
            "phoneName",
            "phone.phoneNumber",
            "phone.countryCode",
            "phone.extension",
            "phone.default"
    };

    @Resource(name = "blCustomerPhoneService")
    private CustomerPhoneService customerPhoneService;
    @Resource(name = "blCustomerPhoneValidator")
    private CustomerPhoneValidator customerPhoneValidator;
    @Resource(name = "blCustomerState")
    private CustomerState customerState;
    @Resource(name = "blEntityConfiguration")
    private EntityConfiguration entityConfiguration;
    @Resource(name = "blPhoneFormatter")
    private PhoneFormatter phoneFormatter;
    @Resource(name = "blPhoneValidator")
    private PhoneValidator phoneValidator;

    /* ??? -
     * Will these static defaults alter their ability to be
     * overwritten via the appContext?  TODO scc: test this scenario
     * */
    private String deletePhoneSuccessView = redirect;
    private String makePhoneDefaultSuccessView = redirect;
    private String savePhoneErrorView = prefix;
    private String savePhoneSuccessView = prefix;
    private String viewPhoneErrorView = prefix;
    private String viewPhoneSuccessView = prefix;

    /**
     * Completely deletes the customerPhone with the given customerPhoneId from the database. The customerPhone must
     * belong to the customer on the current request.
     *
     * @param customerPhoneId
     * @param request
     * @return
     * @throws SecurityException if the customerPhone does not belong to the current customer
     */
    @RequestMapping(value = "deletePhone", method = {
            RequestMethod.GET, RequestMethod.POST}
    )
    public String deletePhone(@RequestParam(required = true)
                              Long customerPhoneId, HttpServletRequest request) {
        CustomerPhone customerPhone = readCustomerPhoneOwnedByCurrentCustomer(customerPhoneId, request);
        customerPhoneService.deleteCustomerPhoneById(customerPhone.getId());

        request.setAttribute("phone.deletedPhone", "true");

        return deletePhoneSuccessView + customerPhoneId;
    }

    @InitBinder("phoneNameForm")
    public void initPhoneNameFormBinder(WebDataBinder binder) {
        binder.setAllowedFields(ALLOWED_PHONE_NAME_FORM_FIELDS);
    }

    /**
     * Called before each and every request comes into the controller, and is placed on the request for use by those methods.
     *
     * @param request
     * @param model
     * @return
     */
    @ModelAttribute("phoneNameForm")
    public PhoneNameForm initPhoneNameForm(HttpServletRequest request, Model model) {
        PhoneNameForm form = new PhoneNameForm();
        form.setPhone((Phone) entityConfiguration.createEntityInstance(
                "org.broadleafcommerce.profile.core.domain.Phone"
        ));

        return form;
    }

    /**
     * Sets the passed in customerPhoneId as the default phone for the current customer.
     *
     * @param customerPhoneId
     * @param request
     * @return
     * @throws SecurityException if the customerPhone does not belong to the current customer
     */
    @RequestMapping(value = "makePhoneDefault", method = {
            RequestMethod.GET, RequestMethod.POST}
    )
    public String makePhoneDefault(
            @RequestParam(required = true) Long customerPhoneId,
            HttpServletRequest request
    ) {
        CustomerPhone customerPhone = readCustomerPhoneOwnedByCurrentCustomer(customerPhoneId, request);
        customerPhoneService.makeCustomerPhoneDefault(customerPhone.getId(), customerPhone.getCustomer().getId());

        request.setAttribute("phone.madePhoneDefault", "true");

        return makePhoneDefaultSuccessView;
    }

    /**
     * Creates a new phone if no customerPhoneId is passed in. Otherwise the existing customerPhone (and its phone) are
     * updated with the submitted values, provided they belong to the current customer.
     *
     * @param phoneNameForm
     * @param errors
     * @param request
     * @param customerPhoneId the customerPhone to update; must belong to the current customer
     * @param phoneId         optional; when supplied it must be the phone of the customerPhone being updated
     * @return
     * @throws SecurityException if the customerPhone or phone does not belong to the current customer
     */
    @RequestMapping(value = "savePhone", method = {
            RequestMethod.GET, RequestMethod.POST}
    )
    public String savePhone(
            @ModelAttribute("phoneNameForm") PhoneNameForm phoneNameForm,
            BindingResult errors,
            HttpServletRequest request,
            @RequestParam(required = false) Long customerPhoneId,
            @RequestParam(required = false) Long phoneId
    ) {
        if (GenericValidator.isBlankOrNull(phoneNameForm.getPhoneName())) {
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "phoneName", "phoneName.required");
        }

        CustomerPhone existingCustomerPhone = null;
        if (customerPhoneId != null && customerPhoneId > 0) {
            existingCustomerPhone = readCustomerPhoneOwnedByCurrentCustomer(customerPhoneId, request);
            Phone existingPhone = existingCustomerPhone.getPhone();
            if (phoneId != null && (existingPhone == null || !phoneId.equals(existingPhone.getId()))) {
                throw new SecurityException("The requested phone does not belong to the current customer");
            }
        }

        phoneNameForm.getPhone().setId(null);

        phoneFormatter.formatPhoneNumber(phoneNameForm.getPhone());
        errors.pushNestedPath("phone");
        phoneValidator.validate(phoneNameForm.getPhone(), errors);
        errors.popNestedPath();

        if (!errors.hasErrors()) {
            CustomerPhone customerPhone = (CustomerPhone) entityConfiguration.createEntityInstance(
                    "org.broadleafcommerce.profile.core.domain.CustomerPhone"
            );
            customerPhone.setCustomer(customerState.getCustomer(request));
            customerPhone.setPhoneName(phoneNameForm.getPhoneName());
            customerPhone.setPhone(phoneNameForm.getPhone());

            if (existingCustomerPhone != null) {
                customerPhone.setId(existingCustomerPhone.getId());
                if (existingCustomerPhone.getPhone() != null) {
                    phoneNameForm.getPhone().setId(existingCustomerPhone.getPhone().getId());
                }
            }

            customerPhoneValidator.validate(customerPhone, errors);

            if (!errors.hasErrors()) {
                if (existingCustomerPhone != null) {
                    customerPhone = copyFormOntoCustomerPhone(phoneNameForm, existingCustomerPhone);
                }
                customerPhone = customerPhoneService.saveCustomerPhone(customerPhone);
                request.setAttribute("customerPhoneId", customerPhone.getId());
                request.setAttribute("phoneId", customerPhone.getPhone().getId());
            }

            return savePhoneSuccessView;
        } else {
            return savePhoneErrorView;
        }
    }

    /**
     * Reads the customerPhone with the given id, but only if it belongs to the customer on the current request.
     * Unknown ids and ids owned by other customers are rejected the same way.
     *
     * @param customerPhoneId
     * @param request
     * @return the customerPhone owned by the current customer
     * @throws SecurityException if the customerPhone does not exist or belongs to another customer
     */
    protected CustomerPhone readCustomerPhoneOwnedByCurrentCustomer(Long customerPhoneId, HttpServletRequest request) {
        Customer currentCustomer = customerState.getCustomer(request);
        CustomerPhone customerPhone = customerPhoneId == null
                ? null
                : customerPhoneService.readCustomerPhoneById(customerPhoneId);

        if (!isOwnedBy(customerPhone, currentCustomer)) {
            throw new SecurityException("The requested phone does not belong to the current customer");
        }

        return customerPhone;
    }

    protected boolean isOwnedBy(CustomerPhone customerPhone, Customer customer) {
        return customer != null
                && customer.getId() != null
                && customerPhone != null
                && customerPhone.getCustomer() != null
                && customer.getId().equals(customerPhone.getCustomer().getId());
    }

    protected CustomerPhone copyFormOntoCustomerPhone(PhoneNameForm phoneNameForm, CustomerPhone customerPhone) {
        Phone submitted = phoneNameForm.getPhone();
        Phone phone = customerPhone.getPhone();
        if (phone == null) {
            customerPhone.setPhone(submitted);
        } else {
            phone.setPhoneNumber(submitted.getPhoneNumber());
            phone.setCountryCode(submitted.getCountryCode());
            phone.setExtension(submitted.getExtension());
            phone.setDefault(submitted.isDefault());
        }
        customerPhone.setPhoneName(phoneNameForm.getPhoneName());
        return customerPhone;
    }

    public void setCustomerPhoneService(CustomerPhoneService customerPhoneService) {
        this.customerPhoneService = customerPhoneService;
    }

    public void setCustomerPhoneValidator(CustomerPhoneValidator customerPhoneValidator) {
        this.customerPhoneValidator = customerPhoneValidator;
    }

    public void setCustomerState(CustomerState customerState) {
        this.customerState = customerState;
    }

    public void setdeletePhoneSuccessView(String deletePhoneSuccessView) {
        this.deletePhoneSuccessView = deletePhoneSuccessView;
    }

    public void setEntityConfiguration(EntityConfiguration entityConfiguration) {
        this.entityConfiguration = entityConfiguration;
    }

    public void setmakePhoneDefaultSuccessView(String makePhoneDefaultSuccessView) {
        this.makePhoneDefaultSuccessView = makePhoneDefaultSuccessView;
    }

    public void setPhoneFormatter(PhoneFormatter phoneFormatter) {
        this.phoneFormatter = phoneFormatter;
    }

    public void setPhoneValidator(PhoneValidator phoneValidator) {
        this.phoneValidator = phoneValidator;
    }

    public void setsavePhoneErrorView(String savePhoneErrorView) {
        this.savePhoneErrorView = savePhoneErrorView;
    }

    public void setsavePhoneSuccessView(String savePhoneSuccessView) {
        this.savePhoneSuccessView = savePhoneSuccessView;
    }

    public void setviewPhoneErrorView(String viewPhoneErrorView) {
        this.viewPhoneErrorView = viewPhoneErrorView;
    }

    public void setviewPhoneSuccessView(String viewPhoneSuccessView) {
        this.viewPhoneSuccessView = viewPhoneSuccessView;
    }

    /**
     * Provides a blank template for a new Customer Phone to be created if no customerPhoneId is provided.
     * Otherwise, when a customerPhoneId is provided, the associated customerPhone object is retrieved,
     * and placed on the request.
     *
     * @param customerPhoneId
     * @param request
     * @param phoneNameForm
     * @param errors
     * @return
     */
    @RequestMapping(value = "viewPhone", method = {
            RequestMethod.GET, RequestMethod.POST}
    )
    public String viewPhone(
            @RequestParam(required = false) Long customerPhoneId,
            HttpServletRequest request, @ModelAttribute("phoneNameForm") PhoneNameForm phoneNameForm,
            BindingResult errors
    ) {
        if (customerPhoneId == null) {
            return viewPhoneSuccessView;
        } else {
            CustomerPhone cPhone = customerPhoneService.readCustomerPhoneById(customerPhoneId);

            if (cPhone != null) {
                if (!isOwnedBy(cPhone, customerState.getCustomer(request))) {
                    return viewPhoneErrorView;
                }

                phoneNameForm.setPhone(cPhone.getPhone());
                phoneNameForm.setPhoneName(cPhone.getPhoneName());
                request.setAttribute("customerPhoneId", cPhone.getId());
                request.setAttribute("phoneId", cPhone.getPhone().getId());

                return viewPhoneSuccessView;
            } else {
                return viewPhoneErrorView;
            }
        }
    }

}
