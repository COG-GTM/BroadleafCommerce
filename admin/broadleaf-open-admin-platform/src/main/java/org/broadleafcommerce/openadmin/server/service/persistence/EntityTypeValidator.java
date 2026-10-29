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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadleafcommerce.common.exception.SecurityServiceException;
import org.broadleafcommerce.common.presentation.client.PersistencePerspectiveItemType;
import org.broadleafcommerce.openadmin.dto.AdornedTargetList;
import org.broadleafcommerce.openadmin.dto.Entity;
import org.broadleafcommerce.openadmin.dto.PersistencePackage;
import org.broadleafcommerce.openadmin.dto.PersistencePerspective;

import java.util.ArrayList;
import java.util.List;

/**
 * Ensures the entity type carried by a {@link PersistencePackage} (which may come from the posted {@code entityType}
 * form field) is the ceiling entity, or a subtype of it, before it is used to instantiate, load, update or delete a
 * record. Security checks are performed against the ceiling entity, so allowing an unrelated type would let a user
 * act on records of an entity they have no permission for.
 */
public final class EntityTypeValidator {

    private EntityTypeValidator() {
    }

    /**
     * Validates the entity type of the package, if one is present.
     */
    public static void validate(PersistencePackage persistencePackage) throws SecurityServiceException {
        if (StringUtils.isNotEmpty(getEntityType(persistencePackage))) {
            resolveEntityClass(persistencePackage);
        }
    }

    /**
     * @return the class named by the package's entity type, after verifying it is the ceiling entity (or, for
     * adorned target lists, the adorned target entity) or one of its subtypes
     */
    public static Class<?> resolveEntityClass(PersistencePackage persistencePackage) throws SecurityServiceException {
        String entityType = getEntityType(persistencePackage);
        if (StringUtils.isEmpty(entityType)) {
            throw new SecurityServiceException("No entity type was supplied");
        }
        Class<?> entityClass = loadClass(entityType);
        for (String allowedType : getAllowedTypes(persistencePackage)) {
            if (allowedType.equals(entityType)) {
                return entityClass;
            }
            try {
                if (Class.forName(allowedType).isAssignableFrom(entityClass)) {
                    return entityClass;
                }
            } catch (ClassNotFoundException e) {
                // not a class, so it can only match by name
            }
        }
        throw new SecurityServiceException(String.format(
                "Entity type %s is not compatible with ceiling entity %s",
                entityType, persistencePackage.getCeilingEntityFullyQualifiedClassname()
        ));
    }

    private static String getEntityType(PersistencePackage persistencePackage) {
        Entity entity = persistencePackage.getEntity();
        if (entity == null || ArrayUtils.isEmpty(entity.getType())) {
            return null;
        }
        return entity.getType()[0];
    }

    private static List<String> getAllowedTypes(PersistencePackage persistencePackage) {
        List<String> allowedTypes = new ArrayList<>();
        if (StringUtils.isNotEmpty(persistencePackage.getCeilingEntityFullyQualifiedClassname())) {
            allowedTypes.add(persistencePackage.getCeilingEntityFullyQualifiedClassname());
        }
        PersistencePerspective persistencePerspective = persistencePackage.getPersistencePerspective();
        if (persistencePerspective != null && persistencePerspective.getPersistencePerspectiveItems() != null) {
            AdornedTargetList adornedTargetList = (AdornedTargetList) persistencePerspective
                    .getPersistencePerspectiveItems().get(PersistencePerspectiveItemType.ADORNEDTARGETLIST);
            if (adornedTargetList != null
                    && StringUtils.isNotEmpty(adornedTargetList.getAdornedTargetEntityClassname())) {
                allowedTypes.add(adornedTargetList.getAdornedTargetEntityClassname());
            }
        }
        return allowedTypes;
    }

    private static Class<?> loadClass(String className) throws SecurityServiceException {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new SecurityServiceException("Unknown entity type " + className, e);
        }
    }

}
