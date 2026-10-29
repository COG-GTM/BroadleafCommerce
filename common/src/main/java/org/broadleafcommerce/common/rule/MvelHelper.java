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
package org.broadleafcommerce.common.rule;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.broadleafcommerce.common.RequestDTO;
import org.broadleafcommerce.common.TimeDTO;
import org.broadleafcommerce.common.presentation.client.SupportedFieldType;
import org.broadleafcommerce.common.time.SystemTime;
import org.broadleafcommerce.common.util.BLCSystemProperty;
import org.broadleafcommerce.common.util.EfficientLRUMap;
import org.broadleafcommerce.common.util.FormatUtil;
import org.broadleafcommerce.common.util.StringUtil;
import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;

import java.io.Serializable;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Helper class for some common rule functions that can be called from mvel as well as utility functions
 * to make calling MVEL rules within Broadleaf easier.
 * <p>
 * An instance of this class is available to the mvel runtime under the variable name MvelHelper with the
 * following functions:
 * <p>
 * convertField(type, fieldValue)
 * toUpperCase(value)
 *
 * @author Jeff Fischer
 */
public class MvelHelper {

    public static final String BLC_RULE_MAP_PARAM = "blRuleMap";
    // The following attribute is set in BroadleafProcessURLFilter
    public static final String REQUEST_DTO = "blRequestDTO";
    private static final Map<String, Serializable> DEFAULT_EXPRESSION_CACHE = new EfficientLRUMap<String, Serializable>(5000);
    private static final Log LOG = LogFactory.getLog(MvelHelper.class);
    private static boolean TEST_MODE = false;
    private static final Pattern LEGACY_TO_UPPER_CASE =
            Pattern.compile("MVEL\\s*\\.\\s*eval\\s*\\(\\s*([\"'])toUpperCase\\(\\)\\1\\s*,");
    private static final Pattern LEGACY_DATE_PARSE = Pattern.compile(
            "java\\s*\\.\\s*text\\s*\\.\\s*DateFormat\\s*\\.\\s*getDateTimeInstance\\s*\\(\\s*3\\s*,\\s*3\\s*\\)"
                    + "\\s*\\.\\s*parse\\s*\\(");

    static {
        System.setProperty("mvel2.disable.jit", "true");
    }

    /**
     * Converts a field to the specified type.    Useful when
     *
     * @param type
     * @param fieldValue
     * @return
     */
    public static Object convertField(String type, String fieldValue) {
        if (fieldValue == null) {
            return null;
        }
        try {
            if (type.equals(SupportedFieldType.BOOLEAN.toString())) {
                return Boolean.parseBoolean(fieldValue);
            } else if (type.equals(SupportedFieldType.DATE.toString())) {
                if (fieldValue.length() == 19) {
                    return FormatUtil.getDateFormat().parse(fieldValue);
                } else {
                    return FormatUtil.getTimeZoneFormat().parse(fieldValue);
                }
            } else if (type.equals(SupportedFieldType.INTEGER.toString())) {
                return Integer.parseInt(fieldValue);
            } else if (type.equals(SupportedFieldType.MONEY.toString())
                    || type.equals(SupportedFieldType.DECIMAL.toString())) {
                return new BigDecimal(fieldValue);
            }
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        throw new IllegalArgumentException("Unrecognized type(" + type + ") for map field conversion.");
    }

    public static Object toUpperCase(String value) {
        if (value == null) {
            return null;
        }
        return value.toUpperCase();
    }

    public static String currentTime() {
        Calendar calendar = SystemTime.asCalendar();

        return FormatUtil.getTimeZoneFormat().format(calendar.getTime());
    }

    public static String subtractFromCurrentTime(int days) {
        Calendar calendar = SystemTime.asCalendar();
        calendar.add(Calendar.DAY_OF_YEAR, -days);

        return FormatUtil.getTimeZoneFormat().format(calendar.getTime());
    }

    /**
     * Returns true if the passed in rule passes based on the passed in ruleParameters.
     * <p>
     * Also returns true if the rule is blank or null.
     * <p>
     * Calls the {@link #evaluateRule(String, Map, Map)} method passing in the DEFAULT_EXPRESSION_CACHE.
     * For systems that need to cache a large number of rule expressions, an alternate cache can be passed in.   The
     * default cache is able to cache up to 1,000 rule expressions which should suffice for most systems.
     *
     * @param rule
     * @param ruleParameters
     * @return
     */
    public static boolean evaluateRule(String rule, Map<String, Object> ruleParameters) {
        return evaluateRule(rule, ruleParameters, DEFAULT_EXPRESSION_CACHE);
    }

    /**
     * Evaluates the passed in rule given the passed in parameters.
     *
     * @param rule
     * @param ruleParameters
     * @return
     */
    public static boolean evaluateRule(
            String rule, Map<String,
            Object> ruleParameters,
            Map<String, Serializable> expressionCache
    ) {
        return evaluateRule(rule, ruleParameters, expressionCache, null);
    }

    /**
     * @param rule
     * @param ruleParameters
     * @param expressionCache
     * @param additionalContextImports additional imports to give to the {@link ParserContext} besides "MvelHelper"
     *                                 ({@link MvelHelper}), "CollectionUtils" ({@link SelectizeCollectionUtils}) and
     *                                 "StringUtils" ({@link StringUtils}) since they are automatically added
     * @return
     */
    public static boolean evaluateRule(
            String rule,
            Map<String, Object> ruleParameters,
            Map<String, Serializable> expressionCache,
            Map<String, Class<?>> additionalContextImports
    ) {

        // Null or empty is a match
        if (rule == null || "".equals(rule)) {
            return true;
        } else {
            // MVEL expression compiling can be expensive so let's cache the expression
            Serializable exp = null;
            if (expressionCache != null) {
                exp = expressionCache.get(rule);
            }
            if (exp == null) {
                ParserContext context = buildParserContext(additionalContextImports);

                String modifiedRule = modifyExpression(rewriteLegacyExpressions(rule), ruleParameters, context);

                try {
                    MvelRuleSandbox.validate(modifiedRule, getContextImportNames(additionalContextImports));
                } catch (MvelRuleViolationException e) {
                    LOG.warn("Refusing to evaluate the mvel expression (" + StringUtil.sanitize(rule) + "): "
                            + e.getMessage() + ". Returning false for the match expression");
                    exp = RejectedRule.INSTANCE;
                }

                synchronized (expressionCache) {
                    if (exp == null) {
                        exp = MVEL.compileExpression(modifiedRule, context);
                    }
                    expressionCache.put(rule, exp);
                }
            }

            if (exp == RejectedRule.INSTANCE) {
                return false;
            }

            Map<String, Object> mvelParameters = new HashMap<>();

            populateParamsFromMap(ruleParameters, mvelParameters);

            try {
                Object test = MVEL.executeExpression(exp, mvelParameters);
                if (test == null) {
                    // This can occur if there is no actual rule
                    return true;
                }
                return (Boolean) test;
            } catch (Exception e) {
                //Unable to execute the MVEL expression for some reason
                //Return false, but notify about the bad expression through logs
                if (!TEST_MODE && LOG.isInfoEnabled()) {
                    LOG.info("Unable to parse and/or execute the mvel expression (" + StringUtil.sanitize(rule)
                            + "). Reporting to the logs and returning false for the match expression", e);
                }
                return false;
            }
        }
    }

    /**
     * Builds the {@link ParserContext} used to compile rules. The {@link MVEL} facade is intentionally not imported so
     * that a rule cannot evaluate further expressions built at runtime.
     */
    public static ParserContext buildParserContext(Map<String, Class<?>> additionalContextImports) {
        ParserContext context = new ParserContext();
        context.addImport("MvelHelper", MvelHelper.class);
        context.addImport("CollectionUtils", SelectizeCollectionUtils.class);
        context.addImport("StringUtils", StringUtils.class);
        if (MapUtils.isNotEmpty(additionalContextImports)) {
            for (Entry<String, Class<?>> entry : additionalContextImports.entrySet()) {
                context.addImport(entry.getKey(), entry.getValue());
            }
        }
        return context;
    }

    /**
     * Validates that the rule stays within the restricted rule language enforced by {@link MvelRuleSandbox}.
     *
     * @param rule                   the rule to validate
     * @param additionalContextTypes names of additional context imports the rule will be evaluated with
     * @throws MvelRuleViolationException when the rule is not allowed
     */
    public static void validateRule(String rule, Collection<String> additionalContextTypes) {
        if (rule == null || rule.isEmpty()) {
            return;
        }
        MvelRuleSandbox.validate(rewriteLegacyExpressions(rule), additionalContextTypes);
    }

    /**
     * Rules generated by older versions of the rule builder call {@code MVEL.eval("toUpperCase()", value)} and
     * {@code java.text.DateFormat.getDateTimeInstance(3,3).parse(value)}, neither of which is reachable from the
     * sandbox. Those calls are rewritten to {@link #toUpperCase(String)} and {@link #parseLegacyDate(String)}. String
     * literals are left untouched.
     */
    protected static String rewriteLegacyExpressions(String rule) {
        if (rule.indexOf("MVEL") < 0 && rule.indexOf("DateFormat") < 0) {
            return rule;
        }
        StringBuilder sb = new StringBuilder(rule.length());
        Matcher toUpperCase = LEGACY_TO_UPPER_CASE.matcher(rule);
        Matcher dateParse = LEGACY_DATE_PARSE.matcher(rule);
        int length = rule.length();
        int i = 0;
        while (i < length) {
            char c = rule.charAt(i);
            if (c == '"' || c == '\'') {
                int end = i + 1;
                while (end < length && rule.charAt(end) != c) {
                    end += rule.charAt(end) == '\\' ? 2 : 1;
                }
                end = Math.min(end + 1, length);
                sb.append(rule, i, end);
                i = end;
            } else if (toUpperCase.region(i, length).lookingAt()) {
                sb.append("MvelHelper.toUpperCase(");
                i = toUpperCase.end();
            } else if (dateParse.region(i, length).lookingAt()) {
                sb.append("MvelHelper.parseLegacyDate(");
                i = dateParse.end();
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * Equivalent of {@code java.text.DateFormat.getDateTimeInstance(3,3).parse(value)} used by legacy rules.
     */
    public static Date parseLegacyDate(String value) throws ParseException {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).parse(value);
    }

    protected static Collection<String> getContextImportNames(Map<String, Class<?>> additionalContextImports) {
        if (MapUtils.isEmpty(additionalContextImports)) {
            return Collections.emptySet();
        }
        return additionalContextImports.keySet();
    }

    /**
     * <p>
     * Provides a hook point to modify the final expression before it's built. By default, this looks for attribute
     * maps and replaces them such that it does string comparison.
     *
     * <p>
     * For example, given an expression like getProductAttributes()['somekey'] == 'someval', getProductAttributes()['somekey']
     * actually returns a ProductAttribute object, not a String, so the comparison is wrong. Instead, we actually want
     * to do this: getProductAttributes().?get('somekey').?value == 'someval'. This function performs that replacement
     *
     * <p>
     * The modification regex will support both simple and complex expressions like:
     * "(MvelHelper.convertField("INTEGER",orderItem.?product.?getProductAttributes()["myinteger"])>0&&MvelHelper.convertField("INTEGER",orderItem.?product.?getProductAttributes()["myinteger"])<10)"
     *
     * @param rule the rule to replace
     * @return a modified version of <b>rule</b>
     * @see {@link #getRuleAttributeMaps()}
     */
    protected static String modifyExpression(String rule, Map<String, Object> ruleParameters, ParserContext context) {
        String modifiedExpression = rule;
        for (String attributeMap : getRuleAttributeMaps()) {
            modifiedExpression = modifiedExpression.replaceAll(
                    attributeMap + "\\(\\)\\[(.*?)\\](?!\\.\\?value)",
                    attributeMap + "().?get($1).?value"
            );
        }

        modifiedExpression = unescapeUnicode(modifiedExpression);

        return modifiedExpression;
    }

    /**
     * Decodes unicode escapes like \u0022 into actual characters.
     */
    protected static String unescapeUnicode(String input) {
        if (input == null) return null;
        input = Matcher.quoteReplacement(input);
        input = Pattern.compile("(?<=.intersection).*?(?=.size)").matcher(input).replaceAll(mr -> mr.group()
                .replace("\\u0022", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\\u0027", "'")
                .replace("\\u005C", "\\")
        );
        return input;
    }

    /**
     * Returns an array of attribute map field names that we need to do replacements for in
     * {@link #modifyExpression(String, Map, ParserContext)}
     */
    protected static String[] getRuleAttributeMaps() {
        // intentionally left out pricing context getPricingContextAttributes because that's a Map<String, String>
        return new String[]{"getProductAttributes",
                "getCategoryAttributesMap",
                "getSkuAttributes",
                "getOrderItemAttributes",
                "getCustomerAttributes",
                // Map<String, PageAttribute>
                "getAdditionalAttributes",
                // Map<String, AdminUserAttribute>
                "getAdditionalFields"};
    }

    /**
     * When true, LOG.info statement will be suppressed.   Should only be set from within MvelHelperTest.
     * Prevents an error from displaying during unit test runs.
     *
     * @param testMode
     */
    public static void setTestMode(boolean testMode) {
        TEST_MODE = testMode;
    }

    /**
     * Builds parameters using time, request, customer, and cart.
     * <p>
     * Should be called from within a valid web request.
     *
     * @return
     */
    public static Map<String, Object> buildMvelParameters() {
        Map<String, Object> mvelParameters = new HashMap<>();
        BroadleafRequestContext brc = BroadleafRequestContext.getBroadleafRequestContext();
        if (brc != null && brc.getRequest() != null) {
            TimeDTO timeDto = new TimeDTO(SystemTime.asCalendar());
            HttpServletRequest request = brc.getRequest();
            RequestDTO requestDto = brc.getRequestDTO();
            mvelParameters.put("time", timeDto);
            mvelParameters.put("request", requestDto);

            Map<String, Object> blcRuleMap = (Map<String, Object>) request.getAttribute(BLC_RULE_MAP_PARAM);
            populateParamsFromMap(blcRuleMap, mvelParameters);
        } else if (brc != null) {
            populateParamsFromMap((Map<String, Object>) brc.getAdditionalProperties().get("blRuleMap"), mvelParameters);
        }

        return mvelParameters;
    }

    /**
     * Cached in place of a compiled expression for rules rejected by {@link MvelRuleSandbox}.
     */
    private enum RejectedRule {
        INSTANCE
    }

    protected static void populateParamsFromMap(Map<String, Object> blcRuleMap, Map<String, Object> mvelParameters) {
        if (blcRuleMap != null) {
            for (String mapKey : blcRuleMap.keySet()) {
                mvelParameters.put(mapKey, blcRuleMap.get(mapKey));
            }
        }
    }

}
