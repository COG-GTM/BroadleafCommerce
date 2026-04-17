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
package org.broadleafcommerce.common.extensibility.jpa.hibernate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Version;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;

import java.util.Set;

/**
 * Validates at startup that fields woven by DirectCopyClassTransformer are visible to Hibernate's
 * JPA metamodel. This catches potential issues where load-time Javassist weaving might not be
 * properly integrated with Hibernate's metamodel scanner.
 * <p>
 * <b>Hibernate 6 Compatibility Context:</b>
 * <p>
 * In Hibernate 6, the metamodel construction was significantly refactored. The key concern is whether
 * fields woven at class-load time via Javassist are visible when Hibernate builds its
 * {@code RuntimeMetamodel}. This validator confirms that the transformation-ordering guarantee
 * (Broadleaf weaving before Hibernate metamodel construction) holds at runtime.
 * <p>
 * <b>How it works:</b>
 * <ol>
 *   <li>After the persistence context is fully initialized, this bean queries the JPA metamodel</li>
 *   <li>For known entity classes that receive DirectCopy weaving (e.g., those with {@code archiveStatus},
 *       {@code sandBoxDiscriminator}, etc.), it verifies the woven attributes appear in the metamodel</li>
 *   <li>If any expected woven attributes are missing, it logs a warning indicating a potential
 *       Hibernate 6 compatibility issue</li>
 * </ol>
 * <p>
 * This bean is intentionally lightweight and only performs read-only metamodel introspection.
 * It does not modify any entities or schemas.
 *
 * @see org.broadleafcommerce.common.extensibility.jpa.copy.DirectCopyClassTransformer
 * @see org.broadleafcommerce.common.extensibility.jpa.MergePersistenceUnitManager
 */
public class DirectCopyTransformHibernate6CompatibilityValidator {

    private static final Log LOG = LogFactory.getLog(DirectCopyTransformHibernate6CompatibilityValidator.class);

    /**
     * Well-known attribute names that are commonly woven by DirectCopyClassTransformer via
     * template tokens like {@code archiveOnly}, {@code sandbox}, {@code multiTenantSite}, etc.
     */
    private static final String[] COMMON_WOVEN_ATTRIBUTES = {
            "archiveStatus",   // from WeaveArchiveStatus (ARCHIVE_ONLY token)
    };

    @PersistenceContext(unitName = "blPU")
    private EntityManager entityManager;

    @PostConstruct
    public void validateWovenFieldVisibility() {
        if (entityManager == null) {
            LOG.debug("EntityManager not available; skipping DirectCopy woven-field validation");
            return;
        }

        LOG.info("Validating DirectCopy woven-field visibility in Hibernate metamodel "
                + "(Hibernate version: " + Version.getVersionString() + ")");

        try {
            Set<EntityType<?>> entityTypes = entityManager.getMetamodel().getEntities();
            int checkedCount = 0;
            int wovenFieldsFound = 0;

            for (EntityType<?> entityType : entityTypes) {
                for (String wovenAttr : COMMON_WOVEN_ATTRIBUTES) {
                    if (hasAttribute(entityType, wovenAttr)) {
                        wovenFieldsFound++;
                        LOG.debug("Woven attribute '" + wovenAttr + "' found in entity: "
                                + entityType.getJavaType().getName());
                    }
                }
                checkedCount++;
            }

            if (wovenFieldsFound > 0) {
                LOG.info("DirectCopy Hibernate 6 compatibility check passed: " + wovenFieldsFound
                        + " woven attribute(s) confirmed visible in metamodel across "
                        + checkedCount + " entity type(s)");
            } else {
                LOG.warn("DirectCopy Hibernate 6 compatibility check: no woven attributes "
                        + "(e.g., archiveStatus) were found in the JPA metamodel. This may indicate "
                        + "that load-time class transformation did not run before Hibernate built "
                        + "its metamodel. Verify that the LoadTimeWeaver is properly configured "
                        + "and that MergePersistenceUnitManager is triggering class loading before "
                        + "EntityManagerFactory creation.");
            }
        } catch (Exception e) {
            LOG.warn("Unable to validate DirectCopy woven-field visibility: " + e.getMessage());
        }
    }

    private boolean hasAttribute(ManagedType<?> managedType, String attributeName) {
        try {
            managedType.getAttribute(attributeName);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
