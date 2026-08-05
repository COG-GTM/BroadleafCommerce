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
import org.springframework.security.crypto.password.PasswordEncoder

import spock.lang.Specification

/**
 * Verifies the shipped default for the admin password encoder and that the bootstrap admin user seeded by
 * load_admin_users.sql is stored as a hash that the default encoder understands.
 */
class AdminPasswordEncoderSpec extends Specification {

    private PasswordEncoder defaultAdminEncoder() {
        Properties properties = new Properties()
        getClass().getResourceAsStream('/config/bc/admin/common.properties').withStream { properties.load(it) }
        return (PasswordEncoder) PropertyDrivenBeanFactory.createInstance(properties.getProperty('password.admin.encoder'))
    }

    def "The default admin password encoder hashes rather than stores the raw value"() {
        when:
            PasswordEncoder encoder = defaultAdminEncoder()
            String encoded = encoder.encode('unencodedPassword')

        then: "the raw value is not recoverable from what is persisted, and is salted per encoding"
            encoded != 'unencodedPassword'
            encoded != encoder.encode('unencodedPassword')

        and: "the encoded value still validates"
            encoder.matches('unencodedPassword', encoded)
            !encoder.matches('someOtherPassword', encoded)
    }

    def "The seeded bootstrap admin password is a hash readable by the default encoder"() {
        given:
            String sql = getClass().getResourceAsStream('/config/bc/sql/demo/load_admin_users.sql').text
            def insert = (sql =~ /INSERT INTO BLC_ADMIN_USER \(([^)]*)\) VALUES \(([^)]*)\)/)[0]
            List<String> columns = insert[1].split(',')*.trim()
            List<String> values = insert[2].split(',')*.trim()
            String seededPassword = values[columns.indexOf('PASSWORD')].replace("'", '')

        expect:
            seededPassword != 'admin'
            defaultAdminEncoder().matches('admin', seededPassword)
    }

}
