/*-
 * #%L
 * BroadleafCommerce Admin Module
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
package org.broadleafcommerce.admin.server.service.handler;

import org.broadleafcommerce.common.exception.SecurityServiceException;
import org.broadleafcommerce.common.persistence.Status;
import org.broadleafcommerce.core.catalog.domain.ProductImpl;
import org.broadleafcommerce.core.search.domain.IndexField;
import org.broadleafcommerce.core.search.domain.IndexFieldImpl;
import org.broadleafcommerce.core.search.domain.IndexFieldTypeImpl;
import org.broadleafcommerce.openadmin.dto.Entity;
import org.broadleafcommerce.openadmin.dto.PersistencePackage;
import org.broadleafcommerce.openadmin.server.dao.DynamicEntityDao;
import org.broadleafcommerce.openadmin.server.service.persistence.module.RecordHelper;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IndexFieldCustomPersistenceHandlerTest {

    private final List<Class<?>> retrievedTypes = new ArrayList<>();
    private final List<Object> mergedInstances = new ArrayList<>();
    private final List<Character> archivedFlags = new ArrayList<>();

    private IndexFieldCustomPersistenceHandler handler;
    private DynamicEntityDao dynamicEntityDao;
    private RecordHelper helper;

    @Before
    public void setUp() {
        handler = new IndexFieldCustomPersistenceHandler();
        Status archivable = (Status) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Status.class},
                (proxy, method, args) -> {
                    if ("setArchived".equals(method.getName())) {
                        archivedFlags.add((Character) args[0]);
                    }
                    return null;
                });
        dynamicEntityDao = (DynamicEntityDao) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{DynamicEntityDao.class}, (proxy, method, args) -> {
                    if ("retrieve".equals(method.getName())) {
                        retrievedTypes.add((Class<?>) args[0]);
                        return archivable;
                    }
                    if ("merge".equals(method.getName())) {
                        mergedInstances.add(args[0]);
                        return args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        helper = (RecordHelper) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{RecordHelper.class},
                (proxy, method, args) -> {
                    if ("getSimpleMergedProperties".equals(method.getName())) {
                        return Collections.emptyMap();
                    }
                    if ("getPrimaryKey".equals(method.getName())) {
                        return 1L;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    public void removeRejectsEntityTypeOutsideIndexFieldHierarchy() throws Exception {
        PersistencePackage pkg = removePackage(IndexField.class.getName(), ProductImpl.class.getName());
        assertTrue(handler.canHandleRemove(pkg));

        try {
            handler.remove(pkg, dynamicEntityDao, helper);
            fail("Expected remove of a ProductImpl through the IndexField section to be rejected");
        } catch (SecurityServiceException expected) {
            // expected
        }

        assertTrue("No entity should be loaded", retrievedTypes.isEmpty());
        assertTrue("No entity should be archived", archivedFlags.isEmpty());
        assertTrue("No entity should be merged", mergedInstances.isEmpty());
    }

    @Test
    public void removeRejectsIndexFieldThroughIndexFieldTypeSection() throws Exception {
        PersistencePackage pkg = removePackage(IndexFieldTypeImpl.class.getName(), IndexFieldImpl.class.getName());
        assertTrue(handler.canHandleRemove(pkg));

        try {
            handler.remove(pkg, dynamicEntityDao, helper);
            fail("Expected remove of an IndexField through the IndexFieldType section to be rejected");
        } catch (SecurityServiceException expected) {
            // expected
        }

        assertTrue(retrievedTypes.isEmpty());
        assertTrue(mergedInstances.isEmpty());
    }

    @Test
    public void removeArchivesIndexField() throws Exception {
        PersistencePackage pkg = removePackage(IndexField.class.getName(), IndexFieldImpl.class.getName());

        handler.remove(pkg, dynamicEntityDao, helper);

        assertEquals(Collections.singletonList(IndexFieldImpl.class), retrievedTypes);
        assertEquals(Collections.singletonList('Y'), archivedFlags);
        assertEquals(1, mergedInstances.size());
    }

    private PersistencePackage removePackage(String ceilingEntity, String entityType) {
        Entity entity = new Entity();
        entity.setType(new String[]{entityType});
        PersistencePackage pkg = new PersistencePackage();
        pkg.setCeilingEntityFullyQualifiedClassname(ceilingEntity);
        pkg.setEntity(entity);
        return pkg;
    }

}
