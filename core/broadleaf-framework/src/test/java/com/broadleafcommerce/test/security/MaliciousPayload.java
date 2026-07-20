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
package com.broadleafcommerce.test.security;

import java.io.Serializable;

/**
 * A serializable class that lives in a package which is intentionally NOT on the deserialization allow-list used by
 * {@code ZookeeperDistributedQueue}. It stands in for the kind of arbitrary/gadget class an attacker would try to smuggle
 * through {@code ObjectInputStream.readObject()} in a CWE-502 exploit, and is used by
 * {@code ZookeeperDistributedQueueDeserializationTest} to verify that such classes are rejected.
 */
public class MaliciousPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String command;

    public MaliciousPayload(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }
}
