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
package org.broadleafcommerce.core.web.order.security;

import static org.junit.Assert.assertEquals;

import org.broadleafcommerce.common.security.LocalRedirectStrategy;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;

public class BroadleafAuthenticationSuccessHandlerTest {

    @Test
    public void protocolRelativeSuccessUrlRedirectsToDefaultTarget() throws Exception {
        for (String successUrl : new String[] {"//evil.example/login", "/\\evil.example", "/%2fevil.example"}) {
            assertEquals(successUrl, "/", redirectAfterLogin(successUrl));
        }
    }

    @Test
    public void absoluteSuccessUrlRedirectsToDefaultTarget() throws Exception {
        assertEquals("/", redirectAfterLogin("https://evil.example/login"));
    }

    @Test
    public void localSuccessUrlIsHonored() throws Exception {
        assertEquals("/account", redirectAfterLogin("/account"));
    }

    private String redirectAfterLogin(String successUrl) throws Exception {
        BroadleafAuthenticationSuccessHandler handler = new BroadleafAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl("/");
        handler.setTargetUrlParameter("successUrl");
        handler.setRedirectStrategy(new LocalRedirectStrategy());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login_post.htm");
        request.setParameter("successUrl", successUrl);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, new TestingAuthenticationToken("customer", "n/a"));

        return response.getRedirectedUrl();
    }

}
