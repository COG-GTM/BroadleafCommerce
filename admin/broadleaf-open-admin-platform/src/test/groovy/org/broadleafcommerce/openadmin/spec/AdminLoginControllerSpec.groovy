/*-
 * #%L
 * BroadleafCommerce Open Admin Platform
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
package org.broadleafcommerce.openadmin.spec

import org.broadleafcommerce.common.service.GenericResponse
import org.broadleafcommerce.openadmin.server.security.service.AdminAccountRecoveryRateLimiter
import org.broadleafcommerce.openadmin.server.security.service.AdminSecurityService
import org.broadleafcommerce.openadmin.web.controller.AdminLoginController
import spock.lang.Specification
import spock.lang.Unroll

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession

class AdminLoginControllerSpec extends Specification {

    AdminLoginController controller
    AdminSecurityService securityService
    AdminAccountRecoveryRateLimiter rateLimiter
    HttpServletRequest request
    HttpSession session

    def setup() {
        securityService = Mock(AdminSecurityService)
        rateLimiter = new AdminAccountRecoveryRateLimiter()
        session = Mock(HttpSession)
        request = Mock(HttpServletRequest) {
            getRemoteAddr() >> "203.0.113.7"
            getSession(true) >> session
        }
        controller = new AdminLoginController()
        controller.adminSecurityService = securityService
        controller.accountRecoveryRateLimiter = rateLimiter
    }

    private static GenericResponse responseWith(String errorCode) {
        GenericResponse response = new GenericResponse()
        if (errorCode != null) {
            response.addErrorCode(errorCode)
        }
        return response
    }

    @Unroll
    def "forgot password responds identically when the service reports '#errorCode'"() {
        when:
        String view = controller.processSendResetPasswordEmail(request, null, "someone")

        then:
        1 * securityService.sendResetPasswordNotification("someone") >> responseWith(errorCode)
        1 * session.setAttribute("forgot_password_username", "someone")
        0 * request.setAttribute("errorCode", _)
        view == "redirect:resetPassword?messageCode=passwordTokenSent"

        where:
        errorCode << [null, "invalidUser", "emailNotFound", "inactiveUser"]
    }

    @Unroll
    def "forgot username responds identically when the service reports '#errorCode'"() {
        when:
        String view = controller.processForgotUserName(request, "someone@example.com")

        then:
        1 * securityService.sendForgotUsernameNotification("someone@example.com") >> responseWith(errorCode)
        0 * request.setAttribute("errorCode", _)
        view == "redirect:login?messageCode=usernameSent"

        where:
        errorCode << [null, "notFound", "inactiveUser"]
    }

    def "rate limited forgot password requests are dropped but get the same response"() {
        given:
        rateLimiter.maxAttemptsPerIdentifier = 1

        when:
        String first = controller.processSendResetPasswordEmail(request, null, "admin")
        String second = controller.processSendResetPasswordEmail(request, null, "ADMIN ")

        then:
        1 * securityService.sendResetPasswordNotification(_) >> responseWith(null)
        first == second
        second == "redirect:resetPassword?messageCode=passwordTokenSent"
    }

    def "rate limited forgot username requests are dropped but get the same response"() {
        given:
        rateLimiter.maxAttemptsPerClient = 2

        when:
        List<String> views = (1..3).collect { controller.processForgotUserName(request, "user" + it + "@example.com") }

        then:
        2 * securityService.sendForgotUsernameNotification(_) >> responseWith("notFound")
        views.toSet() == ["redirect:login?messageCode=usernameSent"] as Set
    }
}
