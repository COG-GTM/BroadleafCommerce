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
import org.springframework.security.crypto.password.PasswordEncoder

import spock.lang.Specification

/**
 * Verifies the shipped default for the site (customer) password encoder. Customer passwords, security challenge
 * answers and forgot password tokens are all persisted with this encoder, so the default must be a salted, one way
 * hash rather than an encoder that keeps the value recoverable.
 */
class SitePasswordEncoderSpec extends Specification {

    def "The default site password encoder hashes rather than stores the raw value"() {
        given: "the shipped framework defaults"
            Properties properties = new Properties()
            getClass().getResourceAsStream('/config/bc/fw/common.properties').withStream { properties.load(it) }

        when: "the configured encoder is instantiated the same way blPasswordEncoder is"
            PasswordEncoder encoder = (PasswordEncoder) PropertyDrivenBeanFactory
                    .createInstance(properties.getProperty('password.site.encoder'))
            String encoded = encoder.encode('unencodedPassword')

        then: "the raw value is not recoverable from what is persisted, and is salted per encoding"
            encoded != 'unencodedPassword'
            encoded != encoder.encode('unencodedPassword')

        and: "the encoded value still validates"
            encoder.matches('unencodedPassword', encoded)
            !encoder.matches('someOtherPassword', encoded)
    }

}
