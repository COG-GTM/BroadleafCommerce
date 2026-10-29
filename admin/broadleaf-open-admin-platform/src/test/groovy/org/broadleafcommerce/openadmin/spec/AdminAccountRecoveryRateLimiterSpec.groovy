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

import org.broadleafcommerce.openadmin.server.security.service.AdminAccountRecoveryRateLimiter
import spock.lang.Specification

class AdminAccountRecoveryRateLimiterSpec extends Specification {

    AdminAccountRecoveryRateLimiter limiter

    def setup() {
        limiter = new AdminAccountRecoveryRateLimiter()
        limiter.maxAttemptsPerClient = 3
        limiter.maxAttemptsPerIdentifier = 2
        limiter.windowSeconds = 900
    }

    def "identifier limit is case and whitespace insensitive"() {
        expect:
        limiter.tryAcquire("resetPassword", "10.0.0.1", "admin")
        limiter.tryAcquire("resetPassword", "10.0.0.2", " Admin ")
        !limiter.tryAcquire("resetPassword", "10.0.0.3", "ADMIN")
    }

    def "client limit applies across identifiers"() {
        expect:
        limiter.tryAcquire("forgotUsername", "10.0.0.1", "a@example.com")
        limiter.tryAcquire("forgotUsername", "10.0.0.1", "b@example.com")
        limiter.tryAcquire("forgotUsername", "10.0.0.1", "c@example.com")
        !limiter.tryAcquire("forgotUsername", "10.0.0.1", "d@example.com")
        limiter.tryAcquire("forgotUsername", "10.0.0.2", "d@example.com")
    }

    def "endpoints are limited independently"() {
        expect:
        limiter.tryAcquire("resetPassword", "10.0.0.1", "admin")
        limiter.tryAcquire("resetPassword", "10.0.0.1", "admin")
        !limiter.tryAcquire("resetPassword", "10.0.0.1", "admin")
        limiter.tryAcquire("forgotUsername", "10.0.0.1", "admin")
    }

    def "attempts outside the window are forgotten"() {
        given:
        limiter.windowSeconds = 0

        expect:
        (1..10).every { limiter.tryAcquire("resetPassword", "10.0.0.1", "admin") }
    }

    def "a non-positive limit disables that check"() {
        given:
        limiter.maxAttemptsPerIdentifier = 0

        expect:
        limiter.tryAcquire("resetPassword", "10.0.0.1", "admin")
        limiter.tryAcquire("resetPassword", "10.0.0.2", "admin")
        limiter.tryAcquire("resetPassword", "10.0.0.3", "admin")
    }
}
