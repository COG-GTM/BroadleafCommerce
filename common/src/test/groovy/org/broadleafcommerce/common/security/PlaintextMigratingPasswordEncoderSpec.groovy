/*-
 * #%L
 * BroadleafCommerce Common Libraries
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
package org.broadleafcommerce.common.security

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

import spock.lang.Specification

class PlaintextMigratingPasswordEncoderSpec extends Specification {

    PlaintextMigratingPasswordEncoder encoder = new PlaintextMigratingPasswordEncoder(new BCryptPasswordEncoder(4))

    def "New values are encoded with salted BCrypt"() {
        when:
        String encoded = encoder.encode('secret')

        then:
        encoded.startsWith('$2a$')
        encoded != encoder.encode('secret')
        encoder.matches('secret', encoded)
        !encoder.matches('other', encoded)
        !encoder.upgradeEncoding(encoded)
    }

    def "Legacy plaintext values still match and are flagged for upgrade"() {
        expect:
        encoder.matches('secret', 'secret')
        !encoder.matches('Secret', 'secret')
        !encoder.matches('secret', 'secret2')
        encoder.upgradeEncoding('secret')
    }

    def "A BCrypt hash cannot be used as a password for itself"() {
        given:
        String encoded = encoder.encode('secret')

        expect:
        !encoder.matches(encoded, encoded)
    }

    def "Weaker BCrypt hashes are flagged for upgrade"() {
        given:
        String weak = new BCryptPasswordEncoder(4).encode('secret')

        expect:
        new PlaintextMigratingPasswordEncoder(new BCryptPasswordEncoder(5)).upgradeEncoding(weak)
    }

    def "Missing values never match"() {
        expect:
        !encoder.matches(null, 'secret')
        !encoder.matches('secret', null)
        !encoder.matches('', '')
        !encoder.upgradeEncoding(null)
        !encoder.upgradeEncoding('')
    }

}
