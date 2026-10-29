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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.same;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.broadleafcommerce.core.web.controller.account.validator.CustomerAddressValidator;
import org.broadleafcommerce.profile.core.domain.Address;
import org.broadleafcommerce.profile.core.domain.AddressImpl;
import org.broadleafcommerce.profile.core.domain.CustomerAddress;
import org.broadleafcommerce.profile.core.domain.CustomerAddressImpl;
import org.broadleafcommerce.profile.core.service.AddressService;
import org.broadleafcommerce.profile.core.service.CustomerAddressService;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import jakarta.servlet.http.HttpServletRequest;

public class BroadleafManageCustomerAddressesControllerTest {

    protected static final Long VICTIM_ADDRESS_ID = 42L;
    protected static final Long OWNED_ADDRESS_ID = 7L;

    protected BroadleafManageCustomerAddressesController controller;
    protected AddressService addressService;
    protected CustomerAddressService customerAddressService;
    protected CustomerAddressValidator validator;
    protected HttpServletRequest request;

    @Before
    public void setUp() {
        addressService = createMock(AddressService.class);
        customerAddressService = createMock(CustomerAddressService.class);
        validator = createNiceMock(CustomerAddressValidator.class);
        request = createNiceMock(HttpServletRequest.class);
        expect(request.getHeader("X-Requested-With")).andReturn("XMLHttpRequest").anyTimes();

        controller = new BroadleafManageCustomerAddressesController();
        controller.addressService = addressService;
        controller.customerAddressService = customerAddressService;
        controller.customerAddressValidator = validator;
        controller.validateCustomerOwnedData = true;
    }

    @Test
    public void addPersistsCopyInsteadOfRequestBoundAddress() throws Exception {
        CustomerAddressForm form = formTargeting(VICTIM_ADDRESS_ID, "1 Attacker Way");
        Address bound = form.getAddress();
        Address copy = new AddressImpl();

        addressService.populateAddressISOCountrySub(bound);
        expectLastCall();
        expect(addressService.copyAddress(same(bound))).andReturn(copy);
        Capture<Address> saved = EasyMock.newCapture();
        expect(addressService.saveAddress(capture(saved))).andReturn(copy);
        expect(customerAddressService.create()).andReturn(new CustomerAddressImpl());
        expect(customerAddressService.saveCustomerAddress(anyObject(CustomerAddress.class)))
                .andAnswer(() -> (CustomerAddress) EasyMock.getCurrentArguments()[0]);
        replay(addressService, customerAddressService, validator, request);

        controller.addCustomerAddress(request, new ExtendedModelMap(), form, bindingResult(form),
                new RedirectAttributesModelMap());

        verify(addressService, customerAddressService);
        assertSame(copy, saved.getValue());
        assertNotSame(bound, saved.getValue());
        assertNull(saved.getValue().getId());
    }

    @Test
    public void updateCopiesValuesOntoOwnedAddress() throws Exception {
        Address owned = new AddressImpl();
        owned.setId(OWNED_ADDRESS_ID);
        owned.setAddressLine1("1 Home St");
        CustomerAddress customerAddress = new CustomerAddressImpl();
        customerAddress.setId(5L);
        customerAddress.setAddress(owned);

        CustomerAddressForm form = formTargeting(VICTIM_ADDRESS_ID, "2 New St");
        Address bound = form.getAddress();

        expect(customerAddressService.readCustomerAddressById(5L)).andReturn(customerAddress);
        expect(addressService.copyAddress(same(owned), same(bound))).andAnswer(() -> {
            owned.setAddressLine1(bound.getAddressLine1());
            return owned;
        });
        expect(customerAddressService.saveCustomerAddress(same(customerAddress))).andReturn(customerAddress);
        replay(addressService, customerAddressService, validator, request);

        controller.updateCustomerAddress(request, new ExtendedModelMap(), 5L, form, bindingResult(form),
                new RedirectAttributesModelMap());

        verify(addressService, customerAddressService);
        assertSame(owned, customerAddress.getAddress());
        assertEquals(OWNED_ADDRESS_ID, customerAddress.getAddress().getId());
        assertEquals("2 New St", customerAddress.getAddress().getAddressLine1());
        assertEquals("Home", customerAddress.getAddressName());
    }

    protected CustomerAddressForm formTargeting(Long addressId, String line1) {
        CustomerAddressForm form = new CustomerAddressForm();
        form.getAddress().setId(addressId);
        form.getAddress().setAddressLine1(line1);
        form.setAddressName("Home");
        return form;
    }

    protected BeanPropertyBindingResult bindingResult(CustomerAddressForm form) {
        return new BeanPropertyBindingResult(form, "customerAddressForm");
    }
}
