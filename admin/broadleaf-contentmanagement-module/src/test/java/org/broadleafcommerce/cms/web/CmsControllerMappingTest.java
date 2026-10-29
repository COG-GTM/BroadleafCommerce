/*-
 * #%L
 * BroadleafCommerce CMS Module
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
package org.broadleafcommerce.cms.web;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

/**
 * The cms package is component-scanned by both the storefront (contentClient) and admin (contentCreator)
 * contexts, so no controller in it may expose the legacy /preview/** route that rendered an arbitrary
 * request-chosen template.
 */
public class CmsControllerMappingTest extends TestCase {

    public void testNoScannedControllerMapsPreviewRoute() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        Set<BeanDefinition> controllers = scanner.findCandidateComponents("org.broadleafcommerce.cms");
        assertFalse("Expected to find @Controller classes in org.broadleafcommerce.cms", controllers.isEmpty());

        List<String> offenders = new ArrayList<>();
        for (BeanDefinition definition : controllers) {
            MergedAnnotation<RequestMapping> mapping = ((AnnotatedBeanDefinition) definition).getMetadata()
                    .getAnnotations().get(RequestMapping.class);
            if (!mapping.isPresent()) {
                continue;
            }
            for (String path : mapping.getStringArray("path")) {
                if (path.startsWith("/preview")) {
                    offenders.add(definition.getBeanClassName() + " -> " + path);
                }
            }
        }
        assertTrue("Controllers must not map /preview/**: " + offenders, offenders.isEmpty());
    }

    public void testPreviewTemplateControllerIsRemoved() {
        try {
            Class.forName("org.broadleafcommerce.cms.web.PreviewTemplateController");
            fail("PreviewTemplateController must not be on the classpath");
        } catch (ClassNotFoundException expected) {
            // expected
        }
    }
}
