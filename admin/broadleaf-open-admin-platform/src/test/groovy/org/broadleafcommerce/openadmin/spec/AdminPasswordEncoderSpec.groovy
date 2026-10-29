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

import org.broadleafcommerce.common.util.PropertyDrivenBeanFactory
import org.broadleafcommerce.openadmin.server.security.dao.AdminUserDao
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser
import org.broadleafcommerce.openadmin.server.security.domain.AdminUserImpl
import org.broadleafcommerce.openadmin.server.security.service.AdminSecurityServiceImpl
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

import javax.cache.Cache

import spock.lang.Specification

/**
 * The shipped password.admin.encoder backs blAdminPasswordEncoder, which hashes admin passwords and admin
 * password reset tokens, so it must be a salted one-way hash.
 */
class AdminPasswordEncoderSpec extends Specification {

    static PasswordEncoder defaultAdminEncoder() {
        Properties properties = new Properties()
        AdminPasswordEncoderSpec.getResourceAsStream('/config/bc/admin/common.properties').withStream { properties.load(it) }
        return (PasswordEncoder) PropertyDrivenBeanFactory.createInstance(properties.getProperty('password.admin.encoder'))
    }

    def "the default admin encoder is BCrypt and does not store the raw value"() {
        given:
        PasswordEncoder encoder = defaultAdminEncoder()

        when:
        String first = encoder.encode('s3cret-Admin!')
        String second = encoder.encode('s3cret-Admin!')

        then:
        encoder instanceof BCryptPasswordEncoder
        first.startsWith('$2a$')
        first != 's3cret-Admin!'
        first != second
        encoder.matches('s3cret-Admin!', first)
        encoder.matches('s3cret-Admin!', second)
        !encoder.matches('wrong', first)
    }

    def "a reset token stored with the default encoder only validates against the raw token"() {
        given:
        PasswordEncoder encoder = defaultAdminEncoder()
        String token = 'abcdefghijklmnopqrst'

        when:
        String stored = encoder.encode(token)

        then:
        stored != token
        encoder.matches(token, stored)
        !encoder.matches(stored, stored)
    }

    def "the demo superadmin seed is stored as a BCrypt hash of the demo password"() {
        given:
        String sql = AdminPasswordEncoderSpec.getResourceAsStream('/config/bc/sql/demo/load_admin_users.sql').text
        def insert = (sql =~ /INSERT INTO BLC_ADMIN_USER \(([^)]*)\) VALUES \(([^)]*)\)/)[0]
        List<String> columns = insert[1].split(',')*.trim()
        List<String> values = insert[2].split(',')*.trim()
        String seeded = values[columns.indexOf('PASSWORD')].replace("'", '')

        expect:
        seeded != 'admin'
        defaultAdminEncoder().matches('admin', seeded)
    }

    def "saveAdminUser stores supplied and generated passwords hashed"() {
        given:
        PasswordEncoder encoder = defaultAdminEncoder()
        AdminUserDao dao = Mock()
        AdminSecurityServiceImpl service = new AdminSecurityServiceImpl()
        service.passwordEncoderBean = encoder
        service.adminUserDao = dao
        service.cache = Mock(Cache)
        List<String> persisted = []
        dao.saveAdminUser(_ as AdminUser) >> { AdminUser u -> persisted << u.password; u }

        when: "a password is supplied"
        AdminUser supplied = new AdminUserImpl()
        supplied.unencodedPassword = 's3cret-Admin!'
        service.saveAdminUser(supplied)

        then:
        persisted.every { it != 's3cret-Admin!' }
        encoder.matches('s3cret-Admin!', supplied.password)

        when: "no password is supplied, so one is generated"
        persisted.clear()
        AdminUser generated = new AdminUserImpl()
        service.saveAdminUser(generated)

        then:
        generated.password.startsWith('\$2a\$')
        persisted.every { it.startsWith('\$2a\$') }
    }
}
