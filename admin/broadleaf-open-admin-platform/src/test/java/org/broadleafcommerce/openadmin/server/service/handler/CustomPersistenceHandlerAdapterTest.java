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
package org.broadleafcommerce.openadmin.server.service.handler;

import org.broadleafcommerce.common.exception.ServiceException;
import org.broadleafcommerce.openadmin.dto.Entity;
import org.broadleafcommerce.openadmin.server.dao.DynamicEntityDao;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CustomPersistenceHandlerAdapterTest {

    public interface Ceiling extends Serializable {
    }

    public static class CeilingImpl implements Ceiling {
    }

    public static class CeilingSubImpl extends CeilingImpl {
    }

    static final AtomicBoolean NOT_AN_ENTITY_INITIALIZED = new AtomicBoolean();

    public static class NotAnEntity implements Ceiling {
        static {
            NOT_AN_ENTITY_INITIALIZED.set(true);
        }
    }

    private final CustomPersistenceHandlerAdapter adapter = new CustomPersistenceHandlerAdapter();
    private DynamicEntityDao dao;

    @Before
    public void setUp() {
        dao = (DynamicEntityDao) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DynamicEntityDao.class},
                (proxy, method, args) -> {
                    if ("getAllPolymorphicEntitiesFromCeiling".equals(method.getName()) && args[0] == Ceiling.class) {
                        return new Class<?>[]{CeilingSubImpl.class, CeilingImpl.class};
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static Entity entityOfType(String type) {
        Entity entity = new Entity();
        entity.setType(type == null ? null : new String[]{type});
        return entity;
    }

    @Test
    public void acceptsRegisteredPolymorphicEntity() throws Exception {
        assertSame(CeilingImpl.class, adapter.getValidatedEntityClass(entityOfType(CeilingImpl.class.getName()), Ceiling.class, dao));
        assertSame(CeilingSubImpl.class, adapter.getValidatedEntityClass(entityOfType(CeilingSubImpl.class.getName()), Ceiling.class, dao));
        assertSame(Ceiling.class, adapter.getValidatedEntityClass(entityOfType(Ceiling.class.getName()), Ceiling.class, dao));
    }

    @Test
    public void instantiatesRegisteredEntity() throws Exception {
        Ceiling instance = adapter.newValidatedEntityInstance(entityOfType(CeilingSubImpl.class.getName()), Ceiling.class, dao);
        assertEquals(CeilingSubImpl.class, instance.getClass());
    }

    @Test
    public void rejectsNonEntityClassWithoutLoadingIt() {
        assertRejected(entityOfType("org.broadleafcommerce.openadmin.server.service.handler.CustomPersistenceHandlerAdapterTest$NotAnEntity"));
        assertFalse("rejected class must not be initialized", NOT_AN_ENTITY_INITIALIZED.get());
    }

    @Test
    public void rejectsArbitraryClassAndMissingType() {
        assertRejected(entityOfType("java.util.ArrayList"));
        assertRejected(entityOfType("does.not.Exist"));
        assertRejected(entityOfType(""));
        assertRejected(entityOfType(null));
    }

    private void assertRejected(Entity entity) {
        try {
            adapter.newValidatedEntityInstance(entity, Ceiling.class, dao);
            fail("expected ServiceException");
        } catch (ServiceException e) {
            assertTrue(e.getMessage().contains("not a registered"));
        }
    }
}
