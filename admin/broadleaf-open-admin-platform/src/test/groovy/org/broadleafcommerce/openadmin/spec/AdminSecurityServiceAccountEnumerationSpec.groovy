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

import org.broadleafcommerce.openadmin.server.security.dao.AdminUserDao
import org.broadleafcommerce.openadmin.server.security.dao.ForgotPasswordSecurityTokenDao
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser
import org.broadleafcommerce.openadmin.server.security.service.AdminSecurityServiceImpl
import org.springframework.security.crypto.password.PasswordEncoder
import spock.lang.Specification
import spock.lang.Unroll

class AdminSecurityServiceAccountEnumerationSpec extends Specification {

    AdminSecurityServiceImpl service
    AdminUserDao adminUserDao
    ForgotPasswordSecurityTokenDao tokenDao
    PasswordEncoder passwordEncoder

    def setup() {
        adminUserDao = Mock(AdminUserDao)
        tokenDao = Mock(ForgotPasswordSecurityTokenDao)
        passwordEncoder = Mock(PasswordEncoder)
        service = new AdminSecurityServiceImpl()
        service.adminUserDao = adminUserDao
        service.forgotPasswordSecurityTokenDao = tokenDao
        service.passwordEncoderBean = passwordEncoder
    }

    private AdminUser user(boolean exists, String email, Boolean active) {
        if (!exists) {
            return null
        }
        AdminUser user = Mock(AdminUser)
        user.getId() >> 1L
        user.getEmail() >> email
        user.getActiveStatusFlag() >> active
        return user
    }

    @Unroll
    def "reset with token reports invalidToken for a #description user"() {
        given:
        AdminUser account = user(exists, email, active)
        adminUserDao.readAdminUserByUserName("target") >> account

        when:
        def response = service.resetPasswordUsingToken("target", "sometoken", "Passw0rd!", "Passw0rd!")

        then:
        response.errorCodesList == ["invalidToken"]

        where:
        description  | exists | email           | active
        "missing"    | false  | null            | null
        "email-less" | true   | null            | true
        "inactive"   | true   | "a@example.com" | false
    }

    def "reset with token reports invalidToken for an active user with a wrong token"() {
        given:
        adminUserDao.readAdminUserByUserName("target") >> user(true, "a@example.com", true)
        tokenDao.readUnusedTokensByAdminUserId(1L) >> []

        when:
        def response = service.resetPasswordUsingToken("target", "sometoken", "Passw0rd!", "Passw0rd!")

        then:
        response.errorCodesList == ["invalidToken"]
    }

    @Unroll
    def "password validation errors do not depend on whether the account exists (#description)"() {
        given:
        AdminUser account = user(exists, "a@example.com", true)
        adminUserDao.readAdminUserByUserName("target") >> account

        when:
        def response = service.resetPasswordUsingToken("target", "sometoken", "Passw0rd!", "different")

        then:
        response.errorCodesList == ["passwordMismatch"]

        where:
        description | exists
        "missing"   | false
        "active"    | true
    }

    def "forgot password for an unknown user still performs a token hash"() {
        given:
        adminUserDao.readAdminUserByUserName("ghost") >> null

        when:
        def response = service.sendResetPasswordNotification("ghost")

        then:
        1 * passwordEncoder.encode(_) >> "hashed"
        0 * tokenDao.saveToken(_)
        response.hasErrors
    }
}
