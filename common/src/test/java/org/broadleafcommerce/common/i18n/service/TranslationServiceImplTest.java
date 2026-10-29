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
package org.broadleafcommerce.common.i18n.service;

import org.broadleafcommerce.common.i18n.domain.TranslatedEntity;
import org.broadleafcommerce.common.service.PersistenceService;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class TranslationServiceImplTest {

    static class TripwireState {
        static volatile boolean initialized = false;
    }

    static class Tripwire {
        static {
            TripwireState.initialized = true;
        }
    }

    private final AtomicReference<Class<?>> resolvedClass = new AtomicReference<>();
    private TranslationServiceImpl service;

    @Before
    public void setUp() {
        TripwireState.initialized = false;
        resolvedClass.set(null);
        service = new TranslationServiceImpl() {
            @Override
            protected TranslatedEntity getEntityType(Class<?> entityClass) {
                resolvedClass.set(entityClass);
                return TranslatedEntity.SITE;
            }
        };
    }

    private PersistenceService persistenceServiceAllowing(String className, boolean allowed) {
        return (PersistenceService) Proxy.newProxyInstance(
                PersistenceService.class.getClassLoader(),
                new Class<?>[] { PersistenceService.class },
                (proxy, method, args) -> {
                    if ("validateEntityTypeName".equals(method.getName())) {
                        return allowed && className.equals(args[0]);
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    return null;
                });
    }

    private void assertRejected(String className) {
        try {
            service.getAssignableEntityType(className);
            fail("Expected " + className + " to be rejected");
        } catch (IllegalArgumentException e) {
            // expected
        }
        assertEquals(null, resolvedClass.get());
    }

    @Test
    public void rejectsUnregisteredClassWithoutInitializingIt() {
        String className = Tripwire.class.getName();
        service.persistenceService = persistenceServiceAllowing(className, false);

        assertRejected(className);
        assertFalse("Static initializer of a non-whitelisted class must not run", TripwireState.initialized);
    }

    @Test
    public void rejectsArbitraryJdkClass() {
        service.persistenceService = persistenceServiceAllowing("java.lang.System", false);
        assertRejected("java.lang.System");
    }

    @Test
    public void rejectsBlankAndMissingPersistenceService() {
        service.persistenceService = null;
        assertRejected(null);
        assertRejected("");
        assertRejected(Tripwire.class.getName());
        assertFalse(TripwireState.initialized);
    }

    @Test
    public void whitelistedEntityTypeIsResolvedWithoutStaticInitialization() {
        String className = Tripwire.class.getName();
        service.persistenceService = persistenceServiceAllowing(className, true);

        assertSame(TranslatedEntity.SITE, service.getAssignableEntityType(className));
        assertEquals(className, resolvedClass.get().getName());
        assertFalse(TripwireState.initialized);
    }

    @Test
    public void registeredTranslatedEntityTypeIsAllowed() {
        service.persistenceService = null;

        assertSame(TranslatedEntity.SITE, service.getAssignableEntityType(TranslatedEntity.SITE.getType()));
        assertEquals(TranslatedEntity.SITE.getType(), resolvedClass.get().getName());
    }

}
