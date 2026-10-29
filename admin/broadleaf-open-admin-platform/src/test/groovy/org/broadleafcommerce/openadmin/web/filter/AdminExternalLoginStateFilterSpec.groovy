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
package org.broadleafcommerce.openadmin.web.filter

import org.broadleafcommerce.common.security.BroadleafExternalAuthenticationUserDetails
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser
import org.broadleafcommerce.openadmin.server.security.domain.AdminUserImpl
import org.broadleafcommerce.openadmin.server.security.service.AdminSecurityService
import org.springframework.security.core.authority.SimpleGrantedAuthority
import spock.lang.Specification

class AdminExternalLoginStateFilterSpec extends Specification {

    AdminSecurityService adminSecurityService = Mock()
    AdminExternalLoginStateFilter filter

    def setup() {
        filter = new AdminExternalLoginStateFilter()
        def field = AdminExternalLoginStateFilter.getDeclaredField("adminSecurityService")
        field.accessible = true
        field.set(filter, adminSecurityService)
        adminSecurityService.readAllAdminRoles() >> []
    }

    def externalUser(String login, String password) {
        def details = new BroadleafExternalAuthenticationUserDetails(
                login, password, [new SimpleGrantedAuthority("ROLE_ADMIN")])
        details.firstName = "Alice"
        details.lastName = "Admin"
        details.email = "alice@example.com"
        return details
    }

    def "erased external credentials never fall back to the login as the local password"() {
        given:
        def details = externalUser("alice", "ldap-secret")
        details.eraseCredentials()
        AdminUser user = new AdminUserImpl()

        when:
        filter.saveAdminUser(details, user)

        then:
        1 * adminSecurityService.saveAdminUser(user) >> user
        user.login == "alice"
        user.unencodedPassword != null
        user.unencodedPassword != "alice"
        user.unencodedPassword.length() >= AdminExternalLoginStateFilter.GENERATED_PASSWORD_LENGTH
    }

    def "a previously provisioned login-as-password is replaced on the next provision"() {
        given:
        def details = externalUser("alice", "ldap-secret")
        details.eraseCredentials()
        AdminUser user = new AdminUserImpl()
        user.login = "alice"
        user.unencodedPassword = "alice"

        when:
        filter.saveAdminUser(details, user)

        then:
        1 * adminSecurityService.saveAdminUser(user) >> user
        user.unencodedPassword != "alice"
    }

    def "generated local passwords are unique per provision"() {
        given:
        def first = new AdminUserImpl()
        def second = new AdminUserImpl()
        def details = externalUser("alice", "ldap-secret")
        details.eraseCredentials()

        when:
        filter.saveAdminUser(details, first)
        filter.saveAdminUser(details, second)

        then:
        first.unencodedPassword != second.unencodedPassword
    }

    def "a supplied external password is still synchronised"() {
        given:
        AdminUser user = new AdminUserImpl()

        when:
        filter.saveAdminUser(externalUser("alice", "ldap-secret"), user)

        then:
        1 * adminSecurityService.saveAdminUser(user) >> user
        user.unencodedPassword == "ldap-secret"
    }
}
