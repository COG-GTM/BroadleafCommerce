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
import org.junit.After;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PersistenceServiceImplTest {

    private static final String PREFIX = PersistenceServiceImplTest.class.getName() + "$";
    private static final String CANARY_PROPERTY = "blc.test.persistenceService.canaryInitialized";
    private static final String LOOKUP_CANARY_PROPERTY = "blc.test.persistenceService.lookupCanaryInitialized";
    private static final String CANARY_CLASS = PREFIX + "StaticInitCanary";
    private static final String LOOKUP_CANARY_CLASS = PREFIX + "LookupCanary";
    private static final String ENTITY_BEAN_ID = PREFIX + "FakeEntity";
    private static final String ENTITY_IMPL = PREFIX + "FakeEntityImpl";
    private static final String BROKEN_BEAN_ID = "brokenEntityBean";
    private static final String NON_ENTITY_BEAN_ID = "nonEntityConfigBean";

    private RecordingPersistenceService service;
    private StubEntityConfiguration entityConfiguration;

    @Before
    public void setUp() {
        System.clearProperty(CANARY_PROPERTY);
        System.clearProperty(LOOKUP_CANARY_PROPERTY);
        service = new RecordingPersistenceService();
        entityConfiguration = new StubEntityConfiguration();
        service.entityConfiguration = entityConfiguration;
        service.registerEntity(TargetModeType.SANDBOX.getType(), FakeEntityImpl.class,
                proxy(EntityManager.class), proxy(PlatformTransactionManager.class));
        service.registerEntityBeanNames();
        service.resolvedNames.clear();
        entityConfiguration.lookedUpBeanIds.clear();
    }

    @After
    public void tearDown() {
        System.clearProperty(CANARY_PROPERTY);
        System.clearProperty(LOOKUP_CANARY_PROPERTY);
    }

    @Test
    public void registeredEntityNamesAreValid() {
        assertTrue(service.validateEntityClassName(ENTITY_BEAN_ID));
        assertTrue(service.validateEntityClassName(ENTITY_IMPL));
        assertTrue(service.validateEntityClassName(PREFIX + "FakeEntityBase"));
    }

    @Test
    public void nonEntityClassIsRejectedWithoutLoadingOrInitializingIt() {
        assertFalse(service.validateEntityClassName(CANARY_CLASS));

        assertNull("static initializer must not run", System.getProperty(CANARY_PROPERTY));
        assertTrue("no reflective resolution before the allowlist check", service.resolvedNames.isEmpty());
    }

    @Test
    public void unknownAndPlatformNamesReturnFalseInsteadOfThrowing() {
        assertFalse(service.validateEntityClassName("no.such.Clazz"));
        assertFalse(service.validateEntityClassName("java.lang.String"));
        assertFalse(service.validateEntityClassName("java.io.Serializable"));
        assertFalse(service.validateEntityClassName(""));
        assertFalse(service.validateEntityClassName(null));
        assertTrue(service.resolvedNames.isEmpty());
    }

    @Test
    public void nonEntityBeanIsRejectedWithoutBeingInstantiated() {
        assertFalse(service.validateEntityClassName(NON_ENTITY_BEAN_ID));

        assertTrue("bean must not be created", entityConfiguration.lookedUpBeanIds.isEmpty());
        assertTrue(service.resolvedNames.isEmpty());
    }

    @Test
    public void allowlistedNameThatFailsToResolveReturnsFalse() {
        assertFalse(service.validateEntityClassName(BROKEN_BEAN_ID));
    }

    @Test
    public void classResolutionDoesNotRunStaticInitializers() {
        Class<?> resolved = service.getClassForName(LOOKUP_CANARY_CLASS);

        assertEquals(LOOKUP_CANARY_CLASS, resolved.getName());
        assertNull(System.getProperty(LOOKUP_CANARY_PROPERTY));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if ("hashCode".equals(m.getName())) {
                return System.identityHashCode(p);
            }
            if ("equals".equals(m.getName())) {
                return p == a[0];
            }
            return null;
        });
    }

    private static class RecordingPersistenceService extends PersistenceServiceImpl {

        private final List<String> resolvedNames = new ArrayList<>();

        @Override
        public Class<?> getCeilingImplClassFromEntityManagers(String className) {
            resolvedNames.add(className);
            Class<?> ceiling = getClassForName(className);
            return ceiling.isAssignableFrom(FakeEntityImpl.class) ? FakeEntityImpl.class : null;
        }
    }

    private static class StubEntityConfiguration extends EntityConfiguration {

        private final List<String> lookedUpBeanIds = new ArrayList<>();

        @Override
        public Class<?> lookupEntityClass(String beanId) {
            lookedUpBeanIds.add(beanId);
            if (ENTITY_BEAN_ID.equals(beanId)) {
                return FakeEntityImpl.class;
            }
            if (BROKEN_BEAN_ID.equals(beanId)) {
                throw new IllegalStateException("bean cannot be created");
            }
            throw new NoSuchBeanDefinitionException(beanId);
        }

        @Override
        public String[] getEntityBeanNames() {
            return new String[]{ENTITY_BEAN_ID, BROKEN_BEAN_ID, NON_ENTITY_BEAN_ID};
        }

        @Override
        public Class<?> getEntityBeanType(String beanId) {
            if (NON_ENTITY_BEAN_ID.equals(beanId)) {
                return StringBuilder.class;
            }
            return FakeEntityImpl.class;
        }
    }

    public interface FakeEntity extends java.io.Serializable {
    }

    public abstract static class FakeEntityBase implements FakeEntity {
    }

    public static class FakeEntityImpl extends FakeEntityBase {
    }

    public static class StaticInitCanary {
        static {
            System.setProperty(CANARY_PROPERTY, "true");
        }
    }

    public static class LookupCanary {
        static {
            System.setProperty(LOOKUP_CANARY_PROPERTY, "true");
        }
    }

}
