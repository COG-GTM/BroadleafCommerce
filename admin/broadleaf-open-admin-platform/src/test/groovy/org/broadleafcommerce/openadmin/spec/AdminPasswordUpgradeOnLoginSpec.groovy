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

import org.broadleafcommerce.common.security.PlaintextMigratingPasswordEncoder
import org.broadleafcommerce.openadmin.server.security.dao.AdminUserDao
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser
import org.broadleafcommerce.openadmin.server.security.domain.AdminUserImpl
import org.broadleafcommerce.openadmin.server.security.service.AdminSecurityHelper
import org.broadleafcommerce.openadmin.server.security.service.user.AdminUserDetailsServiceImpl
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

import spock.lang.Specification

class AdminPasswordUpgradeOnLoginSpec extends Specification {

    AdminUserDao adminUserDao = Mock()
    AdminUserImpl adminUser = new AdminUserImpl(id: 1L, login: 'admin', activeStatusFlag: true)
    PlaintextMigratingPasswordEncoder encoder = new PlaintextMigratingPasswordEncoder(new BCryptPasswordEncoder(4))
    DaoAuthenticationProvider provider

    def setup() {
        AdminUserDetailsServiceImpl userDetailsService = new AdminUserDetailsServiceImpl()
        userDetailsService.adminUserDao = adminUserDao
        userDetailsService.adminSecurityHelper = Mock(AdminSecurityHelper)
        adminUserDao.readAdminUserByUserName('admin') >> adminUser
        adminUserDao.saveAdminUser(_ as AdminUser) >> { AdminUser u -> u }

        provider = new DaoAuthenticationProvider()
        provider.userDetailsService = userDetailsService
        provider.passwordEncoder = encoder
        provider.userDetailsPasswordService = userDetailsService
    }

    def "A legacy plaintext password is rehashed with BCrypt on successful login"() {
        given:
        adminUser.password = 'admin'

        when:
        provider.authenticate(new UsernamePasswordAuthenticationToken('admin', 'admin'))

        then:
        adminUser.password != 'admin'
        adminUser.password.startsWith('$2a$')
        encoder.matches('admin', adminUser.password)
        !encoder.upgradeEncoding(adminUser.password)

        when: "the next login uses the BCrypt hash"
        provider.authenticate(new UsernamePasswordAuthenticationToken('admin', 'admin'))

        then:
        noExceptionThrown()
    }

    def "A wrong password is rejected and nothing is rewritten"() {
        given:
        adminUser.password = 'admin'

        when:
        provider.authenticate(new UsernamePasswordAuthenticationToken('admin', 'wrong'))

        then:
        thrown(BadCredentialsException)
        adminUser.password == 'admin'
        0 * adminUserDao.saveAdminUser(_)
    }

    def "A BCrypt password is not rewritten on login"() {
        given:
        String hash = encoder.encode('admin')
        adminUser.password = hash

        when:
        provider.authenticate(new UsernamePasswordAuthenticationToken('admin', 'admin'))

        then:
        adminUser.password == hash
        0 * adminUserDao.saveAdminUser(_)
    }

}
