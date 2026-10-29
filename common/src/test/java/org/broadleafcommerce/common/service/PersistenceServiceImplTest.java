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

import org.broadleafcommerce.common.persistence.EntityConfiguration;
import org.broadleafcommerce.common.persistence.TargetModeType;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PersistenceServiceImplTest {

    private static final String UNTRUSTED_CLASS_NAME =
            PersistenceServiceImplTest.class.getName() + "$UntrustedClassWithStaticInitializer";
    private static final String CUSTOM_BEAN_ID = "customTestEntity";

    private RecordingPersistenceService service;

    @Before
    public void setUp() {
        StaticInitTracker.initialized = false;
        service = new RecordingPersistenceService();
        service.entityConfiguration = new TestEntityConfiguration();
        service.registerEntityBeanIds();
        service.registerEntity(TargetModeType.SANDBOX.getType(), TestEntityImpl.class,
                proxy(EntityManager.class), proxy(PlatformTransactionManager.class));
        service.resolvedClassNames.clear();
    }

    @Test
    public void testRegisteredEntityNamesAreValid() {
        assertTrue(service.validateEntityClassName(TestEntity.class.getName()));
        assertTrue(service.validateEntityClassName(TestEntityImpl.class.getName()));
        assertTrue(service.validateEntityClassName(AbstractTestEntity.class.getName()));
        assertTrue(service.validateEntityClassName(CUSTOM_BEAN_ID));
    }

    @Test
    public void testUnregisteredClassIsRejectedWithoutBeingLoaded() {
        assertFalse(service.validateEntityClassName(UNTRUSTED_CLASS_NAME));
        assertFalse("validation must not run the static initializer of a submitted class",
                StaticInitTracker.initialized);
        assertTrue(service.resolvedClassNames.isEmpty());
    }

    @Test
    public void testUnknownAndPlatformNamesAreRejectedWithoutResolution() {
        assertFalse(service.validateEntityClassName("com.example.DoesNotExist"));
        assertFalse(service.validateEntityClassName("java.awt.Toolkit"));
        assertFalse(service.validateEntityClassName(Object.class.getName()));
        assertFalse(service.validateEntityClassName(java.io.Serializable.class.getName()));
        assertFalse(service.validateEntityClassName(""));
        assertFalse(service.validateEntityClassName(null));
        assertTrue(service.resolvedClassNames.isEmpty());
    }

    @Test
    public void testRegisteredNameThatFailsToResolveIsRejectedWithoutException() {
        service.failResolution = true;
        assertFalse(service.validateEntityClassName(TestEntityImpl.class.getName()));
        assertEquals(1, service.resolvedClassNames.size());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, method, args) -> {
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(p);
            }
            if ("equals".equals(method.getName())) {
                return p == args[0];
            }
            return null;
        });
    }

    public interface TestEntity extends java.io.Serializable {
    }

    public abstract static class AbstractTestEntity implements TestEntity {
    }

    public static class TestEntityImpl extends AbstractTestEntity {
    }

    public static class StaticInitTracker {
        public static volatile boolean initialized;
    }

    public static class UntrustedClassWithStaticInitializer {
        static {
            StaticInitTracker.initialized = true;
        }
    }

    private static class TestEntityConfiguration extends EntityConfiguration {

        @Override
        public Class<?> lookupEntityClass(String beanId) {
            if (TestEntity.class.getName().equals(beanId) || CUSTOM_BEAN_ID.equals(beanId)) {
                return TestEntityImpl.class;
            }
            throw new NoSuchBeanDefinitionException(beanId);
        }

        @Override
        public String[] getEntityBeanNames() {
            return new String[]{TestEntity.class.getName(), CUSTOM_BEAN_ID};
        }
    }

    /**
     * Emulates the polymorphic ceiling lookup against a single mapped entity while still resolving the
     * submitted name through {@link PersistenceServiceImpl#getClassForName(String)}.
     */
    private static class RecordingPersistenceService extends PersistenceServiceImpl {

        private final List<String> resolvedClassNames = new ArrayList<>();
        private boolean failResolution;

        @Override
        public Class<?> getCeilingImplClassFromEntityManagers(String className) {
            Class<?> ceiling = getClassForName(className);
            if (failResolution || !ceiling.isAssignableFrom(TestEntityImpl.class)) {
                return null;
            }
            return TestEntityImpl.class;
        }

        @Override
        protected Class<?> getClassForName(String className) {
            resolvedClassNames.add(className);
            return super.getClassForName(className);
        }
    }
}
