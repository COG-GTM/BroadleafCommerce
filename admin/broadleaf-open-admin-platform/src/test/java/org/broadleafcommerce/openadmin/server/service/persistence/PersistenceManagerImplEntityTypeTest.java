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
package org.broadleafcommerce.openadmin.server.service.persistence;

import org.broadleafcommerce.common.exception.SecurityServiceException;
import org.broadleafcommerce.openadmin.dto.Entity;
import org.broadleafcommerce.openadmin.dto.PersistencePackage;
import org.broadleafcommerce.openadmin.server.security.domain.AdminRole;
import org.broadleafcommerce.openadmin.server.security.domain.AdminRoleImpl;
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser;
import org.broadleafcommerce.openadmin.server.security.domain.AdminUserImpl;
import org.broadleafcommerce.openadmin.server.security.remote.SecurityVerifier;
import org.broadleafcommerce.openadmin.server.service.handler.CustomPersistenceHandler;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PersistenceManagerImplEntityTypeTest {

    private final List<String> invocations = new ArrayList<>();
    private PersistenceManagerImpl persistenceManager;

    @Before
    public void setUp() {
        persistenceManager = new PersistenceManagerImpl();
        persistenceManager.persistenceManagerEventHandlers = Collections.emptyList();
        persistenceManager.adminRemoteSecurityService = (SecurityVerifier) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{SecurityVerifier.class}, (proxy, method, args) -> {
                    invocations.add("security." + method.getName());
                    return null;
                });
        CustomPersistenceHandler handler = (CustomPersistenceHandler) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{CustomPersistenceHandler.class}, (proxy, method, args) -> {
                    invocations.add("handler." + method.getName());
                    if (method.getName().startsWith("canHandle") || "willHandleSecurity".equals(method.getName())) {
                        return Boolean.TRUE;
                    }
                    if ("getOrder".equals(method.getName())) {
                        return CustomPersistenceHandler.DEFAULT_ORDER;
                    }
                    return null;
                });
        persistenceManager.setCustomPersistenceHandlers(new ArrayList<>(Collections.singletonList(handler)));
    }

    @Test
    public void removeRejectsEntityTypeOutsideCeilingHierarchy() throws Exception {
        try {
            persistenceManager.remove(pkg(AdminRole.class.getName(), AdminUserImpl.class.getName()));
            fail("Expected SecurityServiceException");
        } catch (SecurityServiceException expected) {
            // expected
        }
        assertTrue("No handler or security call should happen: " + invocations, invocations.isEmpty());
    }

    @Test
    public void updateRejectsEntityTypeOutsideCeilingHierarchy() throws Exception {
        try {
            persistenceManager.update(pkg(AdminRole.class.getName(), AdminUserImpl.class.getName()));
            fail("Expected SecurityServiceException");
        } catch (SecurityServiceException expected) {
            // expected
        }
        assertTrue(invocations.toString(), invocations.isEmpty());
    }

    @Test
    public void addRejectsEntityTypeOutsideCeilingHierarchy() throws Exception {
        try {
            persistenceManager.add(pkg(AdminUserImpl.class.getName(), AdminRoleImpl.class.getName()));
            fail("Expected SecurityServiceException");
        } catch (SecurityServiceException expected) {
            // expected
        }
        assertTrue(invocations.toString(), invocations.isEmpty());
    }

    @Test
    public void updateRejectsSubPackageEntityTypeOutsideCeilingHierarchy() throws Exception {
        PersistencePackage root = pkg(AdminUser.class.getName(), AdminUserImpl.class.getName());
        Map<String, PersistencePackage> subPackages = new HashMap<>();
        subPackages.put("dynamicForm", pkg(AdminRole.class.getName(), AdminUserImpl.class.getName()));
        root.setSubPackages(subPackages);
        try {
            persistenceManager.update(root);
            fail("Expected SecurityServiceException");
        } catch (SecurityServiceException expected) {
            // expected
        }
        assertTrue(invocations.toString(), invocations.isEmpty());
    }

    @Test
    public void acceptsEntityTypeWithinCeilingHierarchy() throws Exception {
        persistenceManager.validateEntityTypeAgainstCeiling(pkg(AdminUser.class.getName(), AdminUserImpl.class.getName()));
        persistenceManager.validateEntityTypeAgainstCeiling(pkg(AdminUserImpl.class.getName(), AdminUserImpl.class.getName()));
    }

    @Test
    public void ignoresMissingOrUnresolvableTypes() throws Exception {
        persistenceManager.validateEntityTypeAgainstCeiling(pkg(AdminUser.class.getName(), null));
        persistenceManager.validateEntityTypeAgainstCeiling(pkg(null, AdminUserImpl.class.getName()));
        persistenceManager.validateEntityTypeAgainstCeiling(pkg(AdminUser.class.getName(), "com.example.DoesNotExist"));
        PersistencePackage noEntity = new PersistencePackage();
        noEntity.setCeilingEntityFullyQualifiedClassname(AdminUser.class.getName());
        persistenceManager.validateEntityTypeAgainstCeiling(noEntity);
    }

    private PersistencePackage pkg(String ceilingEntity, String entityType) {
        Entity entity = new Entity();
        if (entityType != null) {
            entity.setType(new String[]{entityType});
        }
        PersistencePackage pkg = new PersistencePackage();
        pkg.setCeilingEntityFullyQualifiedClassname(ceilingEntity);
        pkg.setEntity(entity);
        return pkg;
    }

}
