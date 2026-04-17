/*-
 * #%L
 * BroadleafCommerce Common Enterprise
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
import org.broadleafcommerce.common.extensibility.jpa.copy.DirectCopyIgnorePattern;
import org.hibernate.bytecode.enhance.spi.EnhancementContext;
import org.hibernate.jpa.internal.enhance.EnhancingClassTransformerImpl;

import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;


/**
 * This is the override of Hibernate's bytecode enhancement transformer that adds filtration based on
 * class/package name to prevent parsing unwanted classes.
 * <p>
 * <b>Hibernate 6 Compatibility:</b> This class extends {@link EnhancingClassTransformerImpl}, which is an
 * internal Hibernate class. In Hibernate 6, the bytecode enhancement infrastructure was significantly
 * refactored:
 * <ul>
 *   <li>The {@link EnhancementContext} interface gained new methods in Hibernate 6.2+</li>
 *   <li>Bytecode enhancement now uses ASM 9.x (up from 7.x) for class file processing</li>
 *   <li>Enhancement runs <em>after</em> Broadleaf's Javassist-based DirectCopy weaving, so the
 *       enhancer sees classes that already contain woven fields and methods</li>
 * </ul>
 * <p>
 * The ignore-pattern filtering in this class ensures that Hibernate's enhancer does not attempt to
 * process non-entity classes that might be loaded during the weaving phase, avoiding
 * {@link ClassCircularityError} and other class-loading issues.
 */
public class BroadleafHibernateEnhancingClassTransformerImpl extends EnhancingClassTransformerImpl {

    private static final Log LOG = LogFactory.getLog(BroadleafHibernateEnhancingClassTransformerImpl.class);

    private List<DirectCopyIgnorePattern> ignorePatterns;

    public BroadleafHibernateEnhancingClassTransformerImpl(EnhancementContext enhancementContext) {
        super(enhancementContext);
    }

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer
    ) throws IllegalClassFormatException {
        String convertedClassName = className.replace('/', '.');
        boolean isValidPattern = true;
        List<DirectCopyIgnorePattern> matchedPatterns = new ArrayList<>();
        for (DirectCopyIgnorePattern pattern : ignorePatterns) {
            boolean isPatternMatch = false;
            for (String patternString : pattern.getPatterns()) {
                isPatternMatch = convertedClassName.matches(patternString);
                if (isPatternMatch) {
                    break;
                }
            }
            if (isPatternMatch) {
                matchedPatterns.add(pattern);
            }
            isValidPattern = !(isPatternMatch && pattern.getTemplateTokenPatterns() == null);
            if (!isValidPattern) {
                break;
            }
        }

        if (isValidPattern) {
            try {
                return super.transform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
            } catch (Exception e) {
                // In Hibernate 6, the enhancer may encounter classes that were transformed by Javassist
                // with bytecode that the enhancer cannot fully process. Log and skip rather than fail.
                LOG.debug("Hibernate bytecode enhancement skipped for class [" + convertedClassName
                        + "] due to: " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    public void setIgnorePatterns(List<DirectCopyIgnorePattern> ignorePatterns) {
        this.ignorePatterns = ignorePatterns;
    }

}
