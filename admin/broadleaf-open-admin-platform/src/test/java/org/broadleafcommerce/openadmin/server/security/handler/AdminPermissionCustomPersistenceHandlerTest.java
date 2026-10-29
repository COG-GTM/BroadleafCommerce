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
package org.broadleafcommerce.openadmin.server.security.handler;

import org.broadleafcommerce.common.exception.SecurityServiceException;
import org.broadleafcommerce.common.exception.ServiceException;
import org.broadleafcommerce.openadmin.dto.Entity;
import org.broadleafcommerce.openadmin.dto.PersistencePackage;
import org.broadleafcommerce.openadmin.dto.Property;
import org.broadleafcommerce.openadmin.server.dao.DynamicEntityDao;
import org.broadleafcommerce.openadmin.server.security.domain.AdminPermission;
import org.broadleafcommerce.openadmin.server.security.domain.AdminPermissionImpl;
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser;
import org.broadleafcommerce.openadmin.server.security.remote.EntityOperationType;
import org.broadleafcommerce.openadmin.server.security.remote.SecurityVerifier;
import org.broadleafcommerce.openadmin.server.service.persistence.module.RecordHelper;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AdminPermissionCustomPersistenceHandlerTest {

    private RecordingSecurityVerifier securityVerifier;
    private RecordingHandler handler;

    @Before
    public void setUp() {
        securityVerifier = new RecordingSecurityVerifier();
        handler = new RecordingHandler();
        handler.adminRemoteSecurityService = securityVerifier;
    }

    @Test
    public void addWithIdIsRejectedWithoutUpdatePermission() throws Exception {
        securityVerifier.allowed = EnumSet.of(EntityOperationType.ADD);

        try {
            handler.add(permissionPackage("42"), dynamicEntityDao(), recordHelper());
            fail("Expected update security check to reject the request");
        } catch (SecurityServiceException expected) {
            // expected
        }

        assertEquals(List.of(EntityOperationType.UPDATE), securityVerifier.checks);
        assertFalse("existing permission must not be modified", handler.updateCalled);
    }

    @Test
    public void addWithIdDelegatesToUpdateWhenUpdatePermitted() throws Exception {
        securityVerifier.allowed = EnumSet.of(EntityOperationType.ADD, EntityOperationType.UPDATE);

        handler.add(permissionPackage("42"), dynamicEntityDao(), recordHelper());

        assertEquals(List.of(EntityOperationType.UPDATE), securityVerifier.checks);
        assertTrue(handler.updateCalled);
    }

    @Test
    public void addWithoutIdDoesNotRequireUpdatePermission() throws Exception {
        securityVerifier.allowed = EnumSet.of(EntityOperationType.ADD);

        handler.add(permissionPackage(null), dynamicEntityDao(), recordHelper());

        assertTrue(securityVerifier.checks.isEmpty());
        assertFalse(handler.updateCalled);
    }

    private PersistencePackage permissionPackage(String id) {
        Entity entity = new Entity();
        entity.setType(new String[]{AdminPermissionImpl.class.getName()});
        List<Property> properties = new ArrayList<>();
        properties.add(property("name", "PERMISSION_ALL_PRODUCT"));
        properties.add(property("type", "ALL"));
        if (id != null) {
            properties.add(property("id", id));
        }
        entity.setProperties(properties.toArray(new Property[0]));
        PersistencePackage persistencePackage = new PersistencePackage();
        persistencePackage.setCeilingEntityFullyQualifiedClassname(AdminPermission.class.getName());
        persistencePackage.setCustomCriteria(new String[]{"createNewPermission"});
        persistencePackage.setEntity(entity);
        return persistencePackage;
    }

    private Property property(String name, String value) {
        Property property = new Property();
        property.setName(name);
        property.setValue(value);
        return property;
    }

    private DynamicEntityDao dynamicEntityDao() {
        return nullReturningProxy(DynamicEntityDao.class);
    }

    private RecordHelper recordHelper() {
        return nullReturningProxy(RecordHelper.class);
    }

    @SuppressWarnings("unchecked")
    private <T> T nullReturningProxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (proxy, method, args) -> null);
    }

    private static class RecordingHandler extends AdminPermissionCustomPersistenceHandler {

        boolean updateCalled;

        @Override
        public Entity update(PersistencePackage persistencePackage, DynamicEntityDao dynamicEntityDao, RecordHelper helper) {
            updateCalled = true;
            return persistencePackage.getEntity();
        }
    }

    private static class RecordingSecurityVerifier implements SecurityVerifier {

        final List<EntityOperationType> checks = new ArrayList<>();
        Set<EntityOperationType> allowed = EnumSet.noneOf(EntityOperationType.class);

        @Override
        public AdminUser getPersistentAdminUser() {
            return null;
        }

        @Override
        public void securityCheck(String ceilingEntityFullyQualifiedName, EntityOperationType operationType) throws ServiceException {
            check(operationType);
        }

        @Override
        public void securityCheck(PersistencePackage persistencePackage, EntityOperationType operationType) throws ServiceException {
            check(operationType);
        }

        private void check(EntityOperationType operationType) throws ServiceException {
            checks.add(operationType);
            if (!allowed.contains(operationType)) {
                throw new SecurityServiceException("Security Check Failed for " + operationType);
            }
        }
    }

}
