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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;

public class BroadleafLoginControllerTest {

    private final BroadleafLoginController controller = new BroadleafLoginController();

    @Test
    public void localSuccessUrlIsAddedToModel() {
        ExtendedModelMap model = login("/account/orders");

        assertEquals("/account/orders", model.get("successUrl"));
    }

    @Test
    public void protocolRelativeSuccessUrlIsDropped() {
        String[] unsafe = {"//evil.example/login", "/\\evil.example", "/%2fevil.example", "https://evil.example"};
        for (String successUrl : unsafe) {
            ExtendedModelMap model = login(successUrl);

            assertFalse(successUrl, model.containsAttribute("successUrl"));
        }
    }

    private ExtendedModelMap login(String successUrl) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setParameter("successUrl", successUrl);
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals(controller.getLoginView(), controller.login(request, new MockHttpServletResponse(), model));
        return model;
    }

}
