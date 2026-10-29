/*-
 * #%L
 * BroadleafCommerce Profile
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
package org.broadleafcommerce.profile.core.dao;

import org.broadleafcommerce.profile.core.domain.CustomerForgotPasswordSecurityToken;

import java.util.Date;
import java.util.List;

/**
 * @author bpolster
 */
public interface CustomerForgotPasswordSecurityTokenDao {

    CustomerForgotPasswordSecurityToken readToken(String token);

    List<CustomerForgotPasswordSecurityToken> readUnusedTokensByCustomerId(Long customerId);

    /**
     * Reads the unused tokens for the customer that were created after {@code createdAfter}, newest first.
     */
    List<CustomerForgotPasswordSecurityToken> readUnusedTokensByCustomerId(Long customerId, Date createdAfter);

    /**
     * Counts all tokens (used or not) issued to the customer after {@code createdAfter}.
     */
    Long countTokensCreatedSince(Long customerId, Date createdAfter);

    CustomerForgotPasswordSecurityToken saveToken(CustomerForgotPasswordSecurityToken token);

}
