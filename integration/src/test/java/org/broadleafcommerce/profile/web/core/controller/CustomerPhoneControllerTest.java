/*-
 * #%L
 * BroadleafCommerce Integration
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
package org.broadleafcommerce.profile.web.core.controller;

import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerPhone;
import org.broadleafcommerce.profile.core.service.CustomerPhoneService;
import org.broadleafcommerce.profile.core.service.CustomerService;
import org.broadleafcommerce.profile.web.controller.CustomerPhoneController;
import org.broadleafcommerce.profile.web.core.controller.dataprovider.CustomerPhoneControllerTestDataProvider;
import org.broadleafcommerce.profile.web.core.model.PhoneNameForm;
import org.broadleafcommerce.profile.web.core.security.CustomerStateRequestProcessor;
import org.broadleafcommerce.test.TestNGSiteIntegrationSetup;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Resource;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CustomerPhoneControllerTest extends TestNGSiteIntegrationSetup {

    @Resource
    private CustomerPhoneController customerPhoneController;
    @Resource
    private CustomerPhoneService customerPhoneService;
    @Resource
    private CustomerService customerService;
    private final List<Long> createdCustomerPhoneIds = new ArrayList<>();
    private Long userId = null;
    private MockHttpServletRequest request;
    private static final String SUCCESS = "customerPhones";

    @BeforeEach
    protected void setupCustomerId() throws Exception {
        userId = customerService.readCustomerByUsername("customer1").getId();
    }
    
    @Order(1)

    
    @ParameterizedTest
    @MethodSource("org.broadleafcommerce.profile.web.core.controller.dataprovider.CustomerPhoneControllerTestDataProvider#createCustomerPhone")
    @Transactional
    @Commit
    public void createCustomerPhoneFromController(PhoneNameForm phoneNameForm) {
        BindingResult errors = new BeanPropertyBindingResult(phoneNameForm, "phoneNameForm");

        Customer customer = customerService.readCustomerByUsername("customer1");
        request = this.getNewServletInstance();
        request.setAttribute(CustomerStateRequestProcessor.getCustomerRequestAttributeName(), customer);

        String view = customerPhoneController.savePhone(phoneNameForm, errors, request, null, null);
        assert (view.indexOf(SUCCESS) >= 0);

        List<CustomerPhone> phones = customerPhoneService.readAllCustomerPhonesByCustomerId(userId);

        boolean inPhoneList = false;

        Long id = (Long) request.getAttribute("customerPhoneId");
        assert (id != null);

        for (CustomerPhone p : phones) {
            if ((p.getPhoneName() != null) && p.getPhoneName().equals(phoneNameForm.getPhoneName())) {
                inPhoneList = true;
            }
        }
        assert (inPhoneList == true);

        createdCustomerPhoneIds.add(id);
    }

    @Order(2)


    @Test
    @Transactional
    public void makePhoneDefaultOnCustomerPhoneController() {
        Long nonDefaultPhoneId = null;
        List<CustomerPhone> phones_1 = customerPhoneService.readAllCustomerPhonesByCustomerId(userId);

        for (CustomerPhone p : phones_1) {
            if (!p.getPhone().isDefault()) {
                nonDefaultPhoneId = p.getId();
                break;
            }
        }

        request = this.getNewServletInstance();

        String view = customerPhoneController.makePhoneDefault(nonDefaultPhoneId, request);
        assert (view.indexOf("viewPhone") >= 0);

        List<CustomerPhone> phones = customerPhoneService.readAllCustomerPhonesByCustomerId(userId);

        for (CustomerPhone p : phones) {
            if (p.getId() == nonDefaultPhoneId) {
                assert (p.getPhone().isDefault());

                break;
            }
        }
    }

    @Order(3)


    @Test
    @Transactional
    public void readCustomerPhoneFromController() {
        List<CustomerPhone> phones_1 = customerPhoneService.readAllCustomerPhonesByCustomerId(userId);
        int phones_1_size = phones_1.size();

        request = this.getNewServletInstance();

        String view = customerPhoneController.deletePhone(createdCustomerPhoneIds.get(0), request);
        assert (view.indexOf("viewPhone") >= 0);

        List<CustomerPhone> phones_2 = customerPhoneService.readAllCustomerPhonesByCustomerId(userId);
        assert ((phones_1_size - phones_2.size()) == 1);
    }

    @Order(4)


    @Test
    public void viewCustomerPhoneFromController() {
        PhoneNameForm pnf = new PhoneNameForm();

        BindingResult errors = new BeanPropertyBindingResult(pnf, "phoneNameForm");

        request = this.getNewServletInstance();

        String view = customerPhoneController.viewPhone(null, request, pnf, errors);
        assert (view.indexOf(SUCCESS) >= 0);
        assert (request.getAttribute("customerPhoneId") == null);
    }

    @Order(5)


    @Test
    @Transactional
    public void viewExistingCustomerPhoneFromController() {
        List<CustomerPhone> phones_1 = customerPhoneService.readAllCustomerPhonesByCustomerId(userId);
        PhoneNameForm pnf = new PhoneNameForm();

        BindingResult errors = new BeanPropertyBindingResult(pnf, "phoneNameForm");

        Customer customer = customerService.readCustomerByUsername("customer1");
        request = this.getNewServletInstance();
        request.setAttribute(CustomerStateRequestProcessor.getCustomerRequestAttributeName(), customer);

        String view = customerPhoneController.viewPhone(phones_1.get(0).getId(), request, pnf, errors);
        assert (view.indexOf(SUCCESS) >= 0);
        assert (request.getAttribute("customerPhoneId").equals(phones_1.get(0).getId()));
    }

    private MockHttpServletRequest getNewServletInstance() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("customer_session", userId); //set customer on session

        return request;
    }
}
