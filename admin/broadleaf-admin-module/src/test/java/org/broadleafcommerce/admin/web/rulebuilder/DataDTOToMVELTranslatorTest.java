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
package org.broadleafcommerce.admin.web.rulebuilder;

import org.broadleafcommerce.admin.web.rulebuilder.service.CustomerFieldServiceImpl;
import org.broadleafcommerce.admin.web.rulebuilder.service.FulfillmentGroupFieldServiceImpl;
import org.broadleafcommerce.admin.web.rulebuilder.service.OrderFieldServiceImpl;
import org.broadleafcommerce.admin.web.rulebuilder.service.OrderItemFieldServiceImpl;
import org.broadleafcommerce.admin.web.rulebuilder.service.SkuFieldServiceImpl;
import org.broadleafcommerce.common.presentation.RuleOperatorType;
import org.broadleafcommerce.common.presentation.RuleOptionType;
import org.broadleafcommerce.common.presentation.client.SupportedFieldType;
import org.broadleafcommerce.openadmin.web.rulebuilder.BLCOperator;
import org.broadleafcommerce.openadmin.web.rulebuilder.DataDTOToMVELTranslator;
import org.broadleafcommerce.openadmin.web.rulebuilder.MVELTranslationException;
import org.broadleafcommerce.openadmin.web.rulebuilder.dto.DataDTO;
import org.broadleafcommerce.openadmin.web.rulebuilder.dto.ExpressionDTO;
import org.broadleafcommerce.openadmin.web.rulebuilder.dto.FieldData;

import junit.framework.TestCase;

/**
 * @author Elbert Bautista (elbertbautista)
 */
public class DataDTOToMVELTranslatorTest extends TestCase {

    private OrderItemFieldServiceImpl orderItemFieldService;
    private CustomerFieldServiceImpl customerFieldService;
    private OrderFieldServiceImpl orderFieldService;
    private FulfillmentGroupFieldServiceImpl fulfillmentGroupFieldService;
    private SkuFieldServiceImpl skuFieldService;

    @Override
    protected void setUp() {
        orderItemFieldService = new OrderItemFieldServiceImpl();
        orderItemFieldService.init();
        customerFieldService = new CustomerFieldServiceImpl();
        customerFieldService.init();
        orderFieldService = new OrderFieldServiceImpl();
        orderFieldService.init();
        fulfillmentGroupFieldService = new FulfillmentGroupFieldServiceImpl();
        fulfillmentGroupFieldService.init();
        skuFieldService = new SkuFieldServiceImpl();
        skuFieldService.init();
    }

    /**
     * Tests the creation of an MVEL expression from a DataDTO
     * @throws MVELTranslationException
     *
     * Here's an example of a DataWrapper with a single DataDTO
     *
     * [{"pk":"100",
     *  "quantity":"1",
     *  "condition":"AND",
     *  "rules":[
     *      {"pk":null,
     *      "quantity":null,
     *      "condition":null,
     *      "rules":null,
     *      "id":"category.name",
     *      "operator":"IEQUALS",
     *      "value":"merchandise"}]
     *  }]
     */
    public void testCreateMVEL() throws MVELTranslationException {
        DataDTOToMVELTranslator translator = new DataDTOToMVELTranslator();
        ExpressionDTO expressionDTO = new ExpressionDTO();
        expressionDTO.setId("category.name");
        expressionDTO.setOperator(BLCOperator.IEQUALS.name());
        expressionDTO.setValue("merchandise");

        String translated = translator.createMVEL("discreteOrderItem", expressionDTO, orderItemFieldService);
        String mvel = "MvelHelper.toUpperCase(?discreteOrderItem.?category.?name)==MvelHelper.toUpperCase(\"merchandise\")";
        assert(mvel.equals(translated));
    }

    /**
     * Tests the creation of a Customer Qualification MVEL expression from a DataDTO
     * @throws MVELTranslationException
     *
     * [{"pk":null,
     *  "quantity":null,
     *  "condition":"AND",
     *  "rules":[
     *      {"pk":null,
     *      "quantity":null,
     *      "condition":null,
     *      "rules":null,
     *      "id":"emailAddress",
     *      "operator":"NOT_EQUAL_FIELD",
     *      "value":"username"}]
     *  }]
     */
    public void testCustomerQualificationMVEL() throws MVELTranslationException {
        DataDTOToMVELTranslator translator = new DataDTOToMVELTranslator();
        DataDTO dataDTO = new DataDTO();
        dataDTO.setCondition(BLCOperator.AND.name());

        //not currently supported
//        ExpressionDTO e1 = new ExpressionDTO();
//        e1.setName("emailAddress");
//        e1.setOperator(BLCOperator.NOT_EQUAL_FIELD.name());
//        e1.setValue("username");

        // Not supported
//        ExpressionDTO e2 = new ExpressionDTO();
//        e2.setName("deactivated");
//        e2.setOperator(BLCOperator.EQUALS.name());
//        e2.setValue("true");

        //dataDTO.getGroups().add(e1);
//        dataDTO.getGroups().add(e2);

        // Not supported
//        String translated = translator.createMVEL("customer", dataDTO, customerFieldService);
//        String mvel = "customer.?deactivated==true";
//        assert (mvel.equals(translated));
    }

    /**
     * Tests the creation of an Order Qualification MVEL expression from a DataDTO
     * @throws MVELTranslationException
     *
     * [{"pk":null,
     *  "quantity":null,
     *  "condition":"AND",
     *  "rules":[
     *      {"pk":null,
     *      "quantity":null,
     *      "condition":null,
     *      "rules":null,
     *      "id":"subTotal",
     *      "operator":"GREATER_OR_EQUAL",
     *      "value":"100"}]
     *  }]
     */
    public void testOrderQualificationMVEL() throws MVELTranslationException {
        DataDTOToMVELTranslator translator = new DataDTOToMVELTranslator();
        DataDTO dataDTO = new DataDTO();
        dataDTO.setCondition(BLCOperator.AND.name());

        ExpressionDTO expressionDTO = new ExpressionDTO();
        expressionDTO.setId("subTotal");
        expressionDTO.setOperator(BLCOperator.GREATER_OR_EQUAL.name());
        expressionDTO.setValue("100");
        dataDTO.getRules().add(expressionDTO);

        String translated = translator.createMVEL("order", dataDTO, orderFieldService);
        String mvel = "?order.?subTotal.getAmount()>=100";
        assert (mvel.equals(translated));
    }

    /**
     * Tests the creation of an Item Qualification MVEL expression from a DataDTO
     * @throws MVELTranslationException
     *
     * [{"pk":100,
     *  "quantity":1,
     *  "condition":"AND",
     *  "rules":[
     *      {"pk":null,
     *      "quantity":null,
     *      "condition":null,
     *      "rules":null,
     *      "id":"category.name",
     *      "operator":"EQUALS",
     *      "value":"test category"
     *      }]
     *  },
     *  {"pk":"200",
     *  "quantity":2,
     *  "condition":"NOT",
     *  "rules":[
     *      {"pk":null,
     *      "quantity":null,
     *      "condition":null,
     *      "rules":null,
     *      "id":"product.manufacturer",
     *      "operator":"EQUALS",
     *      "value":"test manufacturer"}]
     *  }]
     */
    public void testItemQualificationMVEL() throws MVELTranslationException {
        DataDTOToMVELTranslator translator = new DataDTOToMVELTranslator();

        DataDTO d1 = new DataDTO();
        d1.setQuantity(1);
        d1.setCondition(BLCOperator.AND.name());
        ExpressionDTO d1e1 = new ExpressionDTO();
        d1e1.setId("category.name");
        d1e1.setOperator(BLCOperator.EQUALS.name());
        d1e1.setValue("test category");
        d1.getRules().add(d1e1);

        String d1Translated = translator.createMVEL("discreteOrderItem", d1, orderItemFieldService);
        String d1Mvel = "?discreteOrderItem.?category.?name==\"test category\"";
        assert(d1Mvel.equals(d1Translated));

        DataDTO d2 = new DataDTO();
        d2.setQuantity(1);
        d2.setCondition(BLCOperator.NOT.name());
        ExpressionDTO d2e1 = new ExpressionDTO();
        d2e1.setId("product.manufacturer");
        d2e1.setOperator(BLCOperator.EQUALS.name());
        d2e1.setValue("test manufacturer");
        d2.getRules().add(d2e1);

        String d2Translated = translator.createMVEL("discreteOrderItem", d2, orderItemFieldService);
        String d2Mvel = "!(?discreteOrderItem.?product.?manufacturer==\"test manufacturer\")";
        assert (d2Mvel.equals(d2Translated));

    }

    /**
     * Tests the creation of a Fulfillment Group Qualification MVEL expression from a DataDTO
     * @throws MVELTranslationException
     *
     * [{"pk":null,
     *  "quantity":null,
     *  "condition":"AND",
     *  "rules":[
     *      {"pk":null,
     *      "quantity":null,
     *      "condition":null,
     *      "rules":null,
     *      "id":"address.state.name",
     *      "operator":"EQUALS",
     *      "value":["Texas"]},
     *      {"pk":null,
     *      "quantity":null,
     *      "condition":null,
     *      "rules":null,
     *      "id":"retailShippingPrice",
     *      "operator":"BETWEEN_INCLUSIVE",
     *      "value":"[99,199]"}]
     *  }]
     */
    public void testFulfillmentQualificationMVEL() throws MVELTranslationException {
        DataDTOToMVELTranslator translator = new DataDTOToMVELTranslator();
        DataDTO dataDTO = new DataDTO();
        dataDTO.setCondition(BLCOperator.AND.name());

        ExpressionDTO e1 = new ExpressionDTO();
        e1.setId("address.stateProvinceRegion");
        e1.setOperator(BLCOperator.EQUALS.name());
        e1.setValue("Texas");

        ExpressionDTO e2 = new ExpressionDTO();
        e2.setId("retailFulfillmentPrice");
        e2.setOperator(BLCOperator.BETWEEN_INCLUSIVE.name());
        e2.setValue("[99,199]");

        dataDTO.getRules().add(e1);
        dataDTO.getRules().add(e2);

        String translated = translator.createMVEL("fulfillmentGroup", dataDTO, fulfillmentGroupFieldService);
        String mvel = "?fulfillmentGroup.?address.?stateProvinceRegion==\"Texas\"&&(?fulfillmentGroup.?retailFulfillmentPrice.getAmount()>=99&&(?fulfillmentGroup.?retailFulfillmentPrice.getAmount()<=199))";
        assert (mvel.equals(translated));
    }

    public void testItemQualificationCollectionMVEL() throws MVELTranslationException {
        DataDTOToMVELTranslator translator = new DataDTOToMVELTranslator();

        DataDTO d1 = new DataDTO();
        d1.setQuantity(1);
        d1.setCondition(BLCOperator.AND.name());
        ExpressionDTO d1e1 = new ExpressionDTO();
        d1e1.setId("name");
        d1e1.setOperator(BLCOperator.COLLECTION_IN.name());
        d1e1.setValue("[\"test sku\", \"test sku 2\"]");
        d1.getRules().add(d1e1);

        String d1Translated = translator.createMVEL("sku", d1, skuFieldService);
        String d1Mvel = "CollectionUtils.intersection(?sku.?name,[\"test sku\", \"test sku 2\"]).size()>0";
        assert(d1Mvel.equals(d1Translated));

    }

    public void testWithinDaysMVEL() throws MVELTranslationException {
        DataDTOToMVELTranslator translator = new DataDTOToMVELTranslator();

        DataDTO d1 = new DataDTO();
        d1.setQuantity(0);
        d1.setCondition(BLCOperator.AND.name());
        ExpressionDTO d1e1 = new ExpressionDTO();
        d1e1.setId("getCustomerAttributes()---invoice_date");
        d1e1.setOperator(BLCOperator.WITHIN_DAYS.name());
        d1e1.setValue("12");
        d1.getRules().add(d1e1);

        customerFieldService.getFields().add(new FieldData.Builder()
                .label("Customer - invoice date")
                .name("getCustomerAttributes()---invoice_date")
                .operators(RuleOperatorType.DATE)
                .options(RuleOptionType.EMPTY_COLLECTION)
                .type(SupportedFieldType.DATE)
                .build());

        String d1Translated = translator.createMVEL("customer", d1, customerFieldService);
        
        String d1Mvel = "(MvelHelper.convertField(\"DATE\",?customer.?getCustomerAttributes()[\"invoice_date\"])" +
                ">MvelHelper.convertField(\"DATE\",MvelHelper.subtractFromCurrentTime(12))" +
                "&&(MvelHelper.convertField(\"DATE\",?customer.?getCustomerAttributes()[\"invoice_date\"])" +
                "<MvelHelper.convertField(\"DATE\",MvelHelper.currentTime())))";

        customerFieldService.init();
        
        assert(d1Mvel.equals(d1Translated));

    }

    private String translate(String entityKey, String field, BLCOperator operator, String value,
                             org.broadleafcommerce.openadmin.web.rulebuilder.service.RuleBuilderFieldService fieldService)
            throws MVELTranslationException {
        ExpressionDTO expressionDTO = new ExpressionDTO();
        expressionDTO.setId(field);
        expressionDTO.setOperator(operator.name());
        expressionDTO.setValue(value);
        return new DataDTOToMVELTranslator().createMVEL(entityKey, expressionDTO, fieldService);
    }

    private void assertRejected(int code, String entityKey, String field, BLCOperator operator, String value,
                                org.broadleafcommerce.openadmin.web.rulebuilder.service.RuleBuilderFieldService fieldService) {
        try {
            String mvel = translate(entityKey, field, operator, value, fieldService);
            fail("Expected the rule to be rejected, but it was translated to: " + mvel);
        } catch (MVELTranslationException e) {
            assertEquals(code, e.getCode());
        }
    }

    /**
     * A string value containing a quote must stay a single string literal instead of breaking out into MVEL code.
     */
    public void testStringValueIsEscaped() throws MVELTranslationException {
        String injection = "x\" || java.lang.Runtime.getRuntime().exec(\"touch /tmp/pwned\") != null || \"";
        assertEquals("?discreteOrderItem.?category.?name==\"x\\\" || java.lang.Runtime.getRuntime().exec(\\\"touch "
                        + "/tmp/pwned\\\") != null || \\\"\"",
                translate("discreteOrderItem", "category.name", BLCOperator.EQUALS, injection, orderItemFieldService));
        assertEquals("?discreteOrderItem.?category.?name==\"x\\\" || 1==1 || \\\"\"",
                translate("discreteOrderItem", "category.name", BLCOperator.EQUALS, "x\" || 1==1 || \"",
                        orderItemFieldService));
        assertEquals("MvelHelper.toUpperCase(?discreteOrderItem.?category.?name)==MvelHelper.toUpperCase(\"x\\\")"
                        + "||true||(\\\"\")",
                translate("discreteOrderItem", "category.name", BLCOperator.IEQUALS, "x\")||true||(\"",
                        orderItemFieldService));
        assertEquals("org.apache.commons.lang3.StringUtils.contains(MvelHelper.toUpperCase(?discreteOrderItem.?category"
                        + ".?name),MvelHelper.toUpperCase(\"a\\\"),b(\\\"\"))",
                translate("discreteOrderItem", "category.name", BLCOperator.ICONTAINS, "a\"),b(\"",
                        orderItemFieldService));
    }

    /**
     * A value that is already wrapped in quotes keeps working, and its inner quotes are escaped.
     */
    public void testQuotedStringValueIsEscaped() throws MVELTranslationException {
        assertEquals("?discreteOrderItem.?category.?name==\"test category\"",
                translate("discreteOrderItem", "category.name", BLCOperator.EQUALS, "\"test category\"",
                        orderItemFieldService));
        assertEquals("?discreteOrderItem.?category.?name==\"a\\\"||true||\\\"b\"",
                translate("discreteOrderItem", "category.name", BLCOperator.EQUALS, "\"a\"||true||\"b\"",
                        orderItemFieldService));
        assertEquals("?discreteOrderItem.?category.?name==\"say \\\"hi\\\"\"",
                translate("discreteOrderItem", "category.name", BLCOperator.EQUALS, "say \\\"hi\\\"",
                        orderItemFieldService));
    }

    /**
     * A backslash cannot be used to escape the closing quote of the literal.
     */
    public void testBackslashCannotEscapeClosingQuote() throws MVELTranslationException {
        assertEquals("?discreteOrderItem.?category.?name==\"x\\\\\"",
                translate("discreteOrderItem", "category.name", BLCOperator.EQUALS, "x\\", orderItemFieldService));
        assertEquals("?discreteOrderItem.?category.?name==\"\\\\u005C\\\"||true||\\\"\"",
                translate("discreteOrderItem", "category.name", BLCOperator.EQUALS, "\\u005C\"||true||\"",
                        orderItemFieldService));
    }

    /**
     * Collection values are emitted as escaped string literals even when they are not wrapped in quotes.
     */
    public void testCollectionValuesAreEscaped() throws MVELTranslationException {
        assertEquals("CollectionUtils.intersection(?sku.?name,[\"a\\\" || true || \\\"\"]).size()>0",
                translate("sku", "name", BLCOperator.COLLECTION_IN, "[a\" || true || \"]", skuFieldService));
        assertEquals("CollectionUtils.intersection(?sku.?name,[\"x\\\\u005C\"]).size()>0",
                translate("sku", "name", BLCOperator.COLLECTION_IN, "[\"x\\u005C\"]", skuFieldService));
    }

    /**
     * A field comparison value is spliced into the rule as a property path, so it must name a registered field.
     */
    public void testFieldComparisonRequiresRegisteredField() {
        assertRejected(MVELTranslationException.OPERATOR_NOT_FOUND, "customer", "emailAddress",
                BLCOperator.EQUALS_FIELD, "username", customerFieldService);

        customerFieldService.getFields().add(new FieldData.Builder()
                .label("Customer - email (comparison)")
                .name("emailComparison")
                .operators("customComparisonOperators")
                .options(RuleOptionType.EMPTY_COLLECTION)
                .type(SupportedFieldType.STRING)
                .build());
        try {
            assertRejected(MVELTranslationException.SPECIFIED_FIELD_NOT_FOUND, "customer", "emailComparison",
                    BLCOperator.EQUALS_FIELD, "username||java.lang.Runtime.getRuntime().exec(\"id\")!=null",
                    customerFieldService);
            assertRejected(MVELTranslationException.SPECIFIED_FIELD_NOT_FOUND, "customer", "emailComparison",
                    BLCOperator.EQUALS_FIELD, "notARegisteredField", customerFieldService);
            assertEquals("?customer.?emailComparison==customer.username", translateUnchecked("customer",
                    "emailComparison", BLCOperator.EQUALS_FIELD, "username", customerFieldService));
        } finally {
            customerFieldService.init();
        }
    }

    private String translateUnchecked(String entityKey, String field, BLCOperator operator, String value,
                                      org.broadleafcommerce.openadmin.web.rulebuilder.service.RuleBuilderFieldService fieldService) {
        try {
            return translate(entityKey, field, operator, value, fieldService);
        } catch (MVELTranslationException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Operators the rule builder does not offer for a field are rejected.
     */
    public void testOperatorMustBeOfferedForField() {
        assertRejected(MVELTranslationException.OPERATOR_NOT_FOUND, "order", "subTotal",
                BLCOperator.COUNT_GREATER_THAN, "1", orderFieldService);
        assertRejected(MVELTranslationException.OPERATOR_NOT_FOUND, "discreteOrderItem", "category.name",
                BLCOperator.COLLECTION_IN, "[\"a\"]", orderItemFieldService);
    }

    /**
     * Boolean, numeric and id values must be literals of their type.
     */
    public void testTypedValuesAreValidated() throws MVELTranslationException {
        orderItemFieldService.getFields().add(new FieldData.Builder()
                .label("Order Item - on sale")
                .name("isOnSale")
                .operators(RuleOperatorType.BOOLEAN)
                .options(RuleOptionType.EMPTY_COLLECTION)
                .type(SupportedFieldType.BOOLEAN)
                .build());
        try {
            assertEquals("?discreteOrderItem.?isOnSale==true",
                    translate("discreteOrderItem", "isOnSale", BLCOperator.EQUALS, "TRUE", orderItemFieldService));
            assertRejected(MVELTranslationException.INCOMPATIBLE_RULE, "discreteOrderItem", "isOnSale",
                    BLCOperator.EQUALS, "true||java.lang.Runtime.getRuntime().exec(\"id\")!=null",
                    orderItemFieldService);
        } finally {
            orderItemFieldService.init();
        }
        assertRejected(MVELTranslationException.INCOMPATIBLE_DECIMAL_VALUE, "order", "subTotal",
                BLCOperator.GREATER_THAN, "1||true", orderFieldService);
    }

}
