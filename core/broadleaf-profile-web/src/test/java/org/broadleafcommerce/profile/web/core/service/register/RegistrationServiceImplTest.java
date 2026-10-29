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
package org.broadleafcommerce.profile.web.core.service.register;

import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.profile.web.core.form.RegisterCustomerForm;
import org.junit.After;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.Assert.assertEquals;

public class RegistrationServiceImplTest {

    private static final String CONTEXT_PATH = "/shop";

    private final RegistrationServiceImpl registrationService = new RegistrationServiceImpl();

    @After
    public void clearRequestContext() {
        BroadleafRequestContext.setBroadleafRequestContext(null);
    }

    @Test
    public void localSuccessUrlIsKept() {
        assertEquals("/account", redirectUrlFor("/account"));
        assertEquals("/checkout?step=1", redirectUrlFor("/checkout?step=1"));
    }

    @Test
    public void missingSuccessUrlFallsBackToContextPath() {
        assertEquals(CONTEXT_PATH, redirectUrlFor(null));
        assertEquals(CONTEXT_PATH, redirectUrlFor(""));
    }

    @Test
    public void offSiteSuccessUrlFallsBackToContextPath() {
        assertEquals(CONTEXT_PATH, redirectUrlFor("//example.org"));
        assertEquals(CONTEXT_PATH, redirectUrlFor("//example.org/login"));
        assertEquals(CONTEXT_PATH, redirectUrlFor("/\\example.org"));
        assertEquals(CONTEXT_PATH, redirectUrlFor("https://example.org"));
        assertEquals(CONTEXT_PATH, redirectUrlFor("javascript:alert(1)"));
        assertEquals(CONTEXT_PATH, redirectUrlFor("/\t/example.org"));
    }

    private String redirectUrlFor(String successUrl) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", CONTEXT_PATH + "/register");
        request.setContextPath(CONTEXT_PATH);
        if (successUrl != null) {
            request.setParameter("successUrl", successUrl);
        }
        BroadleafRequestContext.getBroadleafRequestContext().setRequest(request);

        RegisterCustomerForm form = new RegisterCustomerForm();
        registrationService.addRedirectUrlToForm(form);
        return form.getRedirectUrl();
    }

}
