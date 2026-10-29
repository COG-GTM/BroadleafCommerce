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
package org.broadleafcommerce.common.service;

import org.junit.Test;

import java.io.Serializable;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PersistenceServiceImplTest {

    interface SampleEntity extends Serializable {
    }

    abstract static class AbstractSampleEntity {
    }

    static class SampleEntityImpl extends AbstractSampleEntity implements SampleEntity {
    }

    static class TripwireState {
        static volatile boolean initialized = false;
    }

    static class Tripwire {
        static {
            TripwireState.initialized = true;
        }
    }

    @Test
    public void validateEntityTypeNameAcceptsEntitiesAndTheirSupertypesOnly() {
        PersistenceServiceImpl service = new PersistenceServiceImpl();
        service.registerEntityTypeNames(SampleEntityImpl.class);

        assertTrue(service.validateEntityTypeName(SampleEntityImpl.class.getName()));
        assertTrue(service.validateEntityTypeName(SampleEntity.class.getName()));
        assertTrue(service.validateEntityTypeName(AbstractSampleEntity.class.getName()));

        assertFalse(service.validateEntityTypeName(Object.class.getName()));
        assertFalse(service.validateEntityTypeName("java.lang.System"));
        assertFalse(service.validateEntityTypeName(""));
        assertFalse(service.validateEntityTypeName(null));
    }

    @Test
    public void validateEntityClassNameRejectsUnknownNamesWithoutLoadingThem() {
        PersistenceServiceImpl service = new PersistenceServiceImpl();
        service.registerEntityTypeNames(SampleEntityImpl.class);
        TripwireState.initialized = false;

        assertFalse(service.validateEntityClassName(Tripwire.class.getName()));
        assertFalse(service.validateEntityClassName("java.lang.System"));
        assertFalse(service.validateEntityClassName("com.example.DoesNotExist"));
        assertFalse(service.validateEntityClassName(null));
        assertFalse("Static initializer of a non-whitelisted class must not run", TripwireState.initialized);
    }

}
