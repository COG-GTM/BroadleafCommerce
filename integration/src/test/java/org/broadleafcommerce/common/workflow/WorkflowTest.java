/*-
 * #%L
 * BroadleafCommerce Integration
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
package org.broadleafcommerce.common.workflow;

import org.broadleafcommerce.core.pricing.service.workflow.TotalActivity;
import org.broadleafcommerce.core.workflow.Activity;
import org.broadleafcommerce.core.workflow.ModuleActivity;
import org.broadleafcommerce.core.workflow.PassThroughActivity;
import org.broadleafcommerce.core.workflow.ProcessContext;
import org.broadleafcommerce.core.workflow.SequenceProcessor;
import org.broadleafcommerce.core.workflow.state.test.TestExampleModuleActivity;
import org.broadleafcommerce.core.workflow.state.test.TestRollbackActivity;
import org.broadleafcommerce.test.TestNGSiteIntegrationSetup;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.core.Ordered;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import jakarta.annotation.Resource;

/**
 * 
 *
 * @author Phillip Verheyden (phillipuniverse)
 */
@ContextHierarchy(@ContextConfiguration(name = "siteRoot"))
public class WorkflowTest extends TestNGSiteIntegrationSetup {
    
    
    @ImportResource("classpath:bl-applicationContext-test-module.xml")
    @Configuration
    public static class WorkflowTestConfig {}
    
    @Resource(name = "blCheckoutWorkflowActivities")
    protected List<Activity<ProcessContext<? extends Object>>> activities;
    
    @Resource(name = "blCheckoutWorkflow")
    protected SequenceProcessor checkoutWorkflow;
    
    @Resource(name = "blTotalActivity")
    protected TotalActivity totalActivity;
    
    
    @Test
    public void testMergedOrderedActivities() {
        Assertions.assertEquals(PassThroughActivity.class, activities.get(0).getClass());
        Assertions.assertEquals(100, activities.get(0).getOrder());
        
        Assertions.assertEquals(PassThroughActivity.class, activities.get(6).getClass());
        Assertions.assertEquals(3000, activities.get(5).getOrder());
    }
    
    @Test
    public void testFrameworkOrderingChanged() {
        Assertions.assertEquals(8080, totalActivity.getOrder());
    }
    
    @Test
    public void testDetectedModuleActivity() {
        List<ModuleActivity> moduleActivities = checkoutWorkflow.getModuleActivities();
        Assertions.assertEquals(1, moduleActivities.size());
        Assertions.assertEquals("integration", moduleActivities.get(0).getModuleName());
    }
    
    @Test
    public void testNonExplicitOrdering() {
        Assertions.assertEquals(TestExampleModuleActivity.class, activities.get(activities.size() - 1).getClass());
        Assertions.assertEquals(Ordered.LOWEST_PRECEDENCE, activities.get(activities.size() - 1).getOrder());
    }
    
    /**
     * Tests that a merged activity can have the same order as a framework activity and come after it
     */
    @Test
    public void testSameOrderingConfiguredActivity() {
        Assertions.assertEquals(TestRollbackActivity.class, activities.get(9).getClass());
    }
    
    @Test
    public void testInBetweenActivity() {
        Assertions.assertEquals(PassThroughActivity.class, activities.get(6).getClass());
    }
    
}
