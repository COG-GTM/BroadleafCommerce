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
package org.broadleafcommerce.common.config.javaconfig;

import org.broadleafcommerce.common.rest.api.wrapper.MapElementWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Java configuration equivalent of bl-common-applicationContext-wrapper.xml.
 * Defines REST API wrapper beans used for serialization/deserialization.
 *
 * <p>The original XML configuration is kept in place for backward compatibility
 * until all modules are migrated to Java configuration.</p>
 */
@Configuration
public class CommonWrapperConfiguration {

    @Bean(name = "com.broadleafcommerce.rest.api.wrapper.MapElementWrapper")
    @Scope("prototype")
    public MapElementWrapper mapElementWrapper() {
        return new MapElementWrapper();
    }
}
