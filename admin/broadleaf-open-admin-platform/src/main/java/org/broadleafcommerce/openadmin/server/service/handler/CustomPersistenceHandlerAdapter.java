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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadleafcommerce.common.exception.ServiceException;
import org.broadleafcommerce.common.presentation.client.OperationType;
import org.broadleafcommerce.common.util.dao.DynamicDaoHelperImpl;
import org.broadleafcommerce.openadmin.dto.ClassMetadata;
import org.broadleafcommerce.openadmin.dto.CriteriaTransferObject;
import org.broadleafcommerce.openadmin.dto.DynamicResultSet;
import org.broadleafcommerce.openadmin.dto.Entity;
import org.broadleafcommerce.openadmin.dto.FieldMetadata;
import org.broadleafcommerce.openadmin.dto.MergedPropertyType;
import org.broadleafcommerce.openadmin.dto.PersistencePackage;
import org.broadleafcommerce.openadmin.dto.PersistencePerspective;
import org.broadleafcommerce.openadmin.server.dao.DynamicEntityDao;
import org.broadleafcommerce.openadmin.server.service.persistence.PersistenceManager;
import org.broadleafcommerce.openadmin.server.service.persistence.module.InspectHelper;
import org.broadleafcommerce.openadmin.server.service.persistence.module.RecordHelper;

import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.EntityManager;

/**
 * Convenience class for those {@link org.broadleafcommerce.openadmin.server.service.handler.CustomPersistenceHandler} implementations
 * that do not wish to implement all the methods of the interface.
 *
 * @author Jeff Fischer
 */
public class CustomPersistenceHandlerAdapter implements CustomPersistenceHandler {

    @Override
    public Boolean canHandleInspect(PersistencePackage persistencePackage) {
        return false;
    }

    @Override
    public Boolean canHandleFetch(PersistencePackage persistencePackage) {
        return false;
    }

    @Override
    public Boolean canHandleAdd(PersistencePackage persistencePackage) {
        return false;
    }

    @Override
    public Boolean canHandleRemove(PersistencePackage persistencePackage) {
        return false;
    }

    @Override
    public Boolean canHandleUpdate(PersistencePackage persistencePackage) {
        return false;
    }

    @Override
    public DynamicResultSet inspect(
            PersistencePackage persistencePackage,
            DynamicEntityDao dynamicEntityDao,
            InspectHelper helper
    ) throws ServiceException {
        throw new ServiceException("Inspect not supported");
    }

    @Override
    public DynamicResultSet fetch(
            PersistencePackage persistencePackage,
            CriteriaTransferObject cto,
            DynamicEntityDao dynamicEntityDao,
            RecordHelper helper
    ) throws ServiceException {
        throw new ServiceException("Fetch not supported");
    }

    @Override
    public Entity add(
            PersistencePackage persistencePackage,
            DynamicEntityDao dynamicEntityDao,
            RecordHelper helper
    ) throws ServiceException {
        throw new ServiceException("Add not supported");
    }

    @Override
    public void remove(
            PersistencePackage persistencePackage,
            DynamicEntityDao dynamicEntityDao,
            RecordHelper helper
    ) throws ServiceException {
        throw new ServiceException("Remove not supported");
    }

    @Override
    public Entity update(PersistencePackage persistencePackage, DynamicEntityDao dynamicEntityDao, RecordHelper helper) throws ServiceException {
        throw new ServiceException("Update not supported");
    }

    @Override
    public Boolean willHandleSecurity(PersistencePackage persistencePackage) {
        return false;
    }

    @Override
    public int getOrder() {
        return CustomPersistenceHandler.DEFAULT_ORDER;
    }

    /**
     * This is a helper method that can be invoked as a first step in a custom inspect phase
     */
    protected Map<String, FieldMetadata> getMetadata(PersistencePackage persistencePackage, InspectHelper helper) throws ServiceException {
        String entityName = persistencePackage.getCeilingEntityFullyQualifiedClassname();
        PersistencePerspective perspective = persistencePackage.getPersistencePerspective();
        return helper.getSimpleMergedProperties(entityName, perspective);
    }

    /**
     * This is a helper method that can be invoked as the last step in a custom inspect phase. It will assemble the
     * appropriate DynamicResultSet from the given parameters.
     */
    protected DynamicResultSet getResultSet(
            PersistencePackage persistencePackage,
            InspectHelper helper,
            Map<String, FieldMetadata> metadata
    ) throws ServiceException {
        String entityName = persistencePackage.getCeilingEntityFullyQualifiedClassname();
        try {
            if (helper instanceof PersistenceManager) {
                Class<?>[] entities = ((PersistenceManager) helper).getPolymorphicEntities(entityName);
                Map<MergedPropertyType, Map<String, FieldMetadata>> allMergedProperties = new HashMap<>();
                allMergedProperties.put(MergedPropertyType.PRIMARY, metadata);
                ClassMetadata mergedMetadata = helper.buildClassMetadata(
                        entities, persistencePackage, allMergedProperties
                );
                DynamicResultSet results = new DynamicResultSet(mergedMetadata);
                return results;
            }
        } catch (ClassNotFoundException e) {
            throw new ServiceException(e);
        }
        return new DynamicResultSet();
    }

    protected String[] getPolymorphicClasses(Class<?> clazz, EntityManager em, boolean useCache) {
        DynamicDaoHelperImpl helper = new DynamicDaoHelperImpl();
        Class<?>[] classes = helper.getAllPolymorphicEntitiesFromCeiling(clazz, true, useCache);
        String[] result = new String[classes.length];
        for (int i = 0; i < classes.length; i++) {
            result[i] = classes[i].getName();
        }
        return result;
    }

    /**
     * Resolves the entity class requested in {@code entity.getType()[0]} without loading arbitrary classes by name.
     * The requested type is only accepted if it is the given ceiling class itself or one of the registered
     * persistent entities polymorphic to it; anything else is rejected before any class is loaded or instantiated.
     */
    protected Class<?> getValidatedEntityClass(
            Entity entity,
            Class<?> ceilingClass,
            DynamicEntityDao dynamicEntityDao
    ) throws ServiceException {
        String requestedType = (entity == null || ArrayUtils.isEmpty(entity.getType())) ? null : entity.getType()[0];
        if (StringUtils.isNotBlank(requestedType)) {
            if (ceilingClass.getName().equals(requestedType)) {
                return ceilingClass;
            }
            Class<?>[] entities = dynamicEntityDao.getAllPolymorphicEntitiesFromCeiling(ceilingClass);
            if (entities != null) {
                for (Class<?> candidate : entities) {
                    if (candidate.getName().equals(requestedType)) {
                        return candidate;
                    }
                }
            }
        }
        throw new ServiceException("Entity type is not a registered " + ceilingClass.getName() + " entity");
    }

    protected <T> T newValidatedEntityInstance(
            Entity entity,
            Class<T> ceilingClass,
            DynamicEntityDao dynamicEntityDao
    ) throws ServiceException {
        Class<?> entityClass = getValidatedEntityClass(entity, ceilingClass, dynamicEntityDao);
        try {
            return ceilingClass.cast(entityClass.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException e) {
            throw new ServiceException("Unable to instantiate " + entityClass.getName(), e);
        }
    }

    protected Class getClassForName(String ceilingEntityFullyQualifiedClassname) {
        try {
            return Class.forName(ceilingEntityFullyQualifiedClassname);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    protected boolean isAssignableFrom(String ceilingEntityFullyQualifiedClassname, Class targetClass) {
        try {
            Class<?> clazz = Class.forName(ceilingEntityFullyQualifiedClassname);
            return targetClass.isAssignableFrom(clazz);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    protected boolean meetsCustomCriteria(PersistencePackage pkg, String[] customCriteria) {
        if (pkg.getCustomCriteria() == null) {
            return false;
        }
        for (String criteria : pkg.getCustomCriteria()) {
            if (criteria != null) {
                for (String search : customCriteria) {
                    if (criteria.equals(search)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    protected boolean isBasicOperation(PersistencePackage pkg) {
        return pkg.getPersistencePerspective().getOperationTypes().getAddType().equals(OperationType.BASIC);
    }

    protected boolean isMapOperation(PersistencePackage pkg) {
        return pkg.getPersistencePerspective().getOperationTypes().getAddType().equals(OperationType.MAP);
    }

    protected boolean isAdornedListOperation(PersistencePackage pkg) {
        return pkg.getPersistencePerspective().getOperationTypes().getAddType().equals(OperationType.ADORNEDTARGETLIST);
    }

}
