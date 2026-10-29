/*-
 * #%L
 * BroadleafCommerce Framework
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
package org.broadleafcommerce.core.spec.config

import org.broadleafcommerce.common.util.PropertyDrivenBeanFactory
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

import spock.lang.Specification

/**
 * The shipped password.site.encoder backs blPasswordEncoder, which hashes customer passwords, challenge answers
 * and password reset tokens, so it must be a salted one-way hash.
 */
class SitePasswordEncoderSpec extends Specification {

    def "the default site encoder is BCrypt and does not store the raw value"() {
        given:
        Properties properties = new Properties()
        getClass().getResourceAsStream('/config/bc/fw/common.properties').withStream { properties.load(it) }
        PasswordEncoder encoder = (PasswordEncoder) PropertyDrivenBeanFactory
                .createInstance(properties.getProperty('password.site.encoder'))

        when:
        String first = encoder.encode('customerPassword')
        String second = encoder.encode('customerPassword')

        then:
        encoder instanceof BCryptPasswordEncoder
        first.startsWith('$2a$')
        first != 'customerPassword'
        first != second
        encoder.matches('customerPassword', first)
        !encoder.matches('wrong', first)
    }
}
