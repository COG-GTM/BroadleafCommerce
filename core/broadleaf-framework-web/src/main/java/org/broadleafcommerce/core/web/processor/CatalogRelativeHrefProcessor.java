/*-
 * #%L
 * BroadleafCommerce Framework Web
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
package org.broadleafcommerce.core.web.processor;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.core.catalog.domain.Category;
import org.broadleafcommerce.core.catalog.domain.Product;
import org.broadleafcommerce.core.catalog.service.CatalogURLService;
import org.broadleafcommerce.core.web.util.ProcessorUtils;
import org.broadleafcommerce.presentation.condition.ConditionalOnTemplating;
import org.broadleafcommerce.presentation.dialect.AbstractBroadleafAttributeModifierProcessor;
import org.broadleafcommerce.presentation.model.BroadleafAttributeModifier;
import org.broadleafcommerce.presentation.model.BroadleafTemplateContext;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * For use with category and product entities.   Creates a relative URL using the
 * current URI appended with the url-key (or last fragment of the url).
 * <p>
 * Takes in a category or product object as a parameter.
 * <p>
 * Uses the current request for the baseURI.
 * <p>
 * This implementation will also a categoryId or productId to the end of the URL it generates.
 *
 * @author bpolster
 */
@Component("blCatalogRelativeHrefProcessor")
@ConditionalOnTemplating
public class CatalogRelativeHrefProcessor extends AbstractBroadleafAttributeModifierProcessor {

    private static final String RHREF = "rhref";
    private static final String HREF = "href";

    @Resource(name = "blCatalogURLService")
    protected CatalogURLService catalogURLService;

    @Override
    public String getName() {
        return "RHREF";
    }

    @Override
    public int getPrecedence() {
        return 0;
    }

    protected String buildRelativeHref(
            String tagName,
            Map<String, String> tagAttributes,
            String attributeName,
            String attributeValue,
            BroadleafTemplateContext context
    ) {
        Object result = context.parseExpression(attributeValue);
        HttpServletRequest request = BroadleafRequestContext.getBroadleafRequestContext().getRequest();
        String currentUrl = ProcessorUtils.getUrl(request.getRequestURI(), getQueryParameters(request.getQueryString()));

        if (result instanceof Product) {
            return catalogURLService.buildRelativeProductURL(currentUrl, (Product) result);
        } else if (result instanceof Category) {
            return catalogURLService.buildRelativeCategoryURL(currentUrl, (Category) result);
        }
        return "";
    }

    /**
     * Decodes only the URL query string (not POST form fields) so it can be safely re-encoded.
     * Pairs that cannot be decoded are dropped.
     */
    protected Map<String, String[]> getQueryParameters(String queryString) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (StringUtils.isNotEmpty(queryString)) {
            for (String pair : queryString.split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                int idx = pair.indexOf('=');
                try {
                    String key = URLDecoder.decode(idx < 0 ? pair : pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = idx < 0 ? "" : URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    if (!key.isEmpty()) {
                        values.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
                    }
                } catch (IllegalArgumentException e) {
                    // malformed percent-escape
                }
            }
        }
        Map<String, String[]> parameters = new LinkedHashMap<>();
        values.forEach((key, list) -> parameters.put(key, list.toArray(new String[0])));
        return parameters;
    }

    @Override
    public BroadleafAttributeModifier getModifiedAttributes(
            String tagName,
            Map<String, String> tagAttributes,
            String attributeName,
            String attributeValue,
            BroadleafTemplateContext context
    ) {
        String relativeHref = buildRelativeHref(tagName, tagAttributes, attributeName, attributeValue, context);
        Map<String, String> newAttributes = new HashMap<>();
        newAttributes.put(HREF, StringEscapeUtils.escapeHtml4(relativeHref));
        return new BroadleafAttributeModifier(newAttributes);
    }

}
