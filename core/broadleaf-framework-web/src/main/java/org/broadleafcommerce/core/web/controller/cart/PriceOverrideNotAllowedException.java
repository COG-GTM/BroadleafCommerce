/*-
 * #%L
 * BroadleafCommerce Framework Web
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
package org.broadleafcommerce.core.web.controller.cart;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a user who is not permitted to override prices attempts to add an item to the cart
 * with client supplied override prices.
 *
 * @see BroadleafCartController#isPriceOverrideAllowed(jakarta.servlet.http.HttpServletRequest)
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class PriceOverrideNotAllowedException extends AccessDeniedException {

    private static final long serialVersionUID = 1L;

    public PriceOverrideNotAllowedException(String message) {
        super(message);
    }

}
