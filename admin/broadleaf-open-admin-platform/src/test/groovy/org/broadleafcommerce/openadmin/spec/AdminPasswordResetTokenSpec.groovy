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

import org.broadleafcommerce.common.event.BroadleafApplicationEventPublisher
import org.broadleafcommerce.common.service.GenericResponse
import org.broadleafcommerce.openadmin.server.security.dao.AdminUserDao
import org.broadleafcommerce.openadmin.server.security.dao.ForgotPasswordSecurityTokenDao
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser
import org.broadleafcommerce.openadmin.server.security.domain.AdminUserImpl
import org.broadleafcommerce.openadmin.server.security.domain.ForgotPasswordSecurityToken
import org.broadleafcommerce.openadmin.server.security.service.AdminSecurityServiceImpl
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

import spock.lang.Specification

class AdminPasswordResetTokenSpec extends Specification {

    AdminUserDao adminUserDao = Mock()
    ForgotPasswordSecurityTokenDao tokenDao = Mock()
    AdminUserImpl adminUser = new AdminUserImpl(id: 1L, login: 'admin', email: 'admin@example.com', activeStatusFlag: true)
    List<ForgotPasswordSecurityToken> savedTokens = []
    String emailedToken

    AdminSecurityServiceImpl service = new AdminSecurityServiceImpl() {
        @Override
        protected int getTokenExpiredMinutes() {
            return 30
        }

        @Override
        protected String getResetPasswordURL() {
            return null
        }

        @Override
        AdminUser saveAdminUser(AdminUser user) {
            return user
        }
    }

    def setup() {
        service.adminUserDao = adminUserDao
        service.forgotPasswordSecurityTokenDao = tokenDao
        service.passwordEncoderBean = new BCryptPasswordEncoder(4)
        service.eventPublisher = Mock(BroadleafApplicationEventPublisher) {
            publishEvent(_) >> { args -> emailedToken = args[0].token }
        }
        adminUserDao.readAdminUserByUserName('admin') >> adminUser
        tokenDao.saveToken(_) >> { ForgotPasswordSecurityToken t -> savedTokens << t; t }
        tokenDao.readUnusedTokensByAdminUserId(1L) >> { savedTokens.findAll { !it.tokenUsedFlag } }
    }

    def "A reset token issued under the BCrypt default is stored hashed and can be redeemed"() {
        when:
        service.sendResetPasswordNotification('admin')

        then:
        savedTokens.size() == 1
        emailedToken
        savedTokens[0].token != emailedToken
        savedTokens[0].token.startsWith('$2')

        when:
        GenericResponse response = service.resetPasswordUsingToken('admin', emailedToken, 'n3wPass!', 'n3wPass!')

        then:
        !response.hasErrors
        adminUser.unencodedPassword == 'n3wPass!'
        savedTokens[0].tokenUsedFlag
    }

    def "A wrong reset token is rejected"() {
        given:
        service.sendResetPasswordNotification('admin')

        when:
        GenericResponse response = service.resetPasswordUsingToken('admin', 'not-the-token', 'n3wPass!', 'n3wPass!')

        then:
        response.errorCodesList.contains('invalidToken')
    }
}
