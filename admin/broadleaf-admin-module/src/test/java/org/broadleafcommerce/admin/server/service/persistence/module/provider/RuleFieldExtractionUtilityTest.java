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
package org.broadleafcommerce.admin.server.service.persistence.module.provider;

import org.broadleafcommerce.admin.web.rulebuilder.service.CustomerFieldServiceImpl;
import org.broadleafcommerce.common.rule.MvelHelper;
import org.broadleafcommerce.openadmin.server.service.persistence.module.provider.RuleFieldExtractionUtility;
import org.broadleafcommerce.openadmin.web.rulebuilder.BLCOperator;
import org.broadleafcommerce.openadmin.web.rulebuilder.DataDTOToMVELTranslator;
import org.broadleafcommerce.openadmin.web.rulebuilder.MVELTranslationException;
import org.broadleafcommerce.openadmin.web.rulebuilder.dto.DataDTO;
import org.broadleafcommerce.openadmin.web.rulebuilder.dto.DataWrapper;
import org.broadleafcommerce.openadmin.web.rulebuilder.dto.ExpressionDTO;
import org.broadleafcommerce.profile.core.domain.Customer;
import org.broadleafcommerce.profile.core.domain.CustomerImpl;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

public class RuleFieldExtractionUtilityTest extends TestCase {

    private RuleFieldExtractionUtility utility;

    @Override
    protected void setUp() {
        final CustomerFieldServiceImpl customerFieldService = new CustomerFieldServiceImpl();
        customerFieldService.init();
        utility = new RuleFieldExtractionUtility() {
            @Override
            public String convertDTOToMvelString(DataDTOToMVELTranslator translator, String entityKey, DataDTO dto,
                                                 String fieldService) throws MVELTranslationException {
                return translator.createMVEL(entityKey, dto, customerFieldService);
            }
        };
    }

    public void testRawMvelIsRejected() {
        DataWrapper dw = utility.convertJsonToDataWrapper(
                "{\"data\":[],\"rawMvel\":\"Runtime.getRuntime().exec(\\\"id\\\")!=null\"}");
        assertNotNull(dw.getRawMvel());
        try {
            utility.rejectRawMvel(dw);
            fail("raw mvel was accepted");
        } catch (MVELTranslationException e) {
            assertEquals(MVELTranslationException.UNSAFE_RULE, e.getCode());
        }
    }

    public void testQuantityRuleValueCannotBreakOutOfLiteral() throws MVELTranslationException {
        String value = "a\" || 1==1 || \"";
        DataWrapper dw = utility.convertJsonToDataWrapper("{\"data\":[{\"quantity\":1,\"condition\":\"AND\","
                + "\"rules\":[{\"id\":\"firstName\",\"operator\":\"EQUALS\",\"value\":\"a\\\" || 1==1 || \\\"\"}]}]}");
        String mvel = utility.convertQuantityRuleDTOToMvelString(
                new DataDTOToMVELTranslator(), "customer", dw.getData().get(0), "CUSTOMER_FIELDS");

        Map<String, Object> vars = new HashMap<>();
        Customer customer = new CustomerImpl();
        customer.setFirstName("bob");
        vars.put("customer", customer);
        assertFalse(mvel, MvelHelper.evaluateRule(mvel, vars, null));
        customer.setFirstName(value);
        assertTrue(mvel, MvelHelper.evaluateRule(mvel, vars, null));
    }

    public void testSimpleRuleIsValidated() throws MVELTranslationException {
        DataWrapper dw = new DataWrapper();
        DataDTO dto = new DataDTO();
        dto.setCondition(BLCOperator.AND.name());
        ExpressionDTO expression = new ExpressionDTO();
        expression.setId("firstName");
        expression.setOperator(BLCOperator.EQUALS.name());
        expression.setValue("bob");
        dto.getRules().add(expression);
        dw.getData().add(dto);
        String mvel = utility.convertSimpleMatchRuleJsonToMvel(
                new DataDTOToMVELTranslator(), "customer", "CUSTOMER_FIELDS", dw);
        assertTrue(mvel, mvel.contains("\"bob\""));
    }

    public void testUnsafeMvelIsRejected() {
        try {
            utility.validateMvel("customer.firstName==\"a\" || Runtime.getRuntime().exec(\"id\")!=null");
            fail("unsafe mvel was accepted");
        } catch (MVELTranslationException e) {
            assertEquals(MVELTranslationException.UNSAFE_RULE, e.getCode());
        }
    }

}
