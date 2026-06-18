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
package com.example.attack;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * Test-only stand-in for a deserialization "gadget". It lives outside the {@code org.broadleafcommerce}
 * package on purpose so that it is NOT covered by the queue's deserialization allowlist. If the queue ever
 * deserializes it, {@link #readObject(ObjectInputStream)} simulates arbitrary code execution by flipping
 * {@link #executed}. A correctly hardened queue rejects the class during resolution, before {@code readObject}
 * is ever invoked, so {@link #executed} must remain {@code false}.
 */
public class MaliciousPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    public static volatile boolean executed = false;

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        executed = true;
    }
}
