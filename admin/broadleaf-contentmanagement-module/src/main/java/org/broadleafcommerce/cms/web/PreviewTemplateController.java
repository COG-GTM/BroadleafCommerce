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

import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Renders a template under {@code templates/} for an admin sandbox preview. Requests outside a
 * non-production sandbox, or whose path is not a plain template path, get a 404.
 */
@Controller
@RequestMapping(PreviewTemplateController.REQUEST_MAPPING_PREFIX + "**")
public class PreviewTemplateController {
    public static final String REQUEST_MAPPING_PREFIX = "/preview/";
    private final String templatePathPrefix = "templates";

    /**
     * Slash-separated segments of letters, digits, '_' and '-'. Excludes '.', ':', '~', '%' and
     * Thymeleaf expression characters, so no traversal, fragment selectors or expressions.
     */
    protected static final Pattern SAFE_TEMPLATE_PATH = Pattern.compile("(/[A-Za-z0-9_-]+)+");

    @RequestMapping
    public String displayPreview(HttpServletRequest httpServletRequest) {
        if (!isPreviewSandBoxActive()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String requestURIPrefix = httpServletRequest.getContextPath() + REQUEST_MAPPING_PREFIX;
        String requestURI = httpServletRequest.getRequestURI();
        if (requestURI == null || !requestURI.startsWith(requestURIPrefix)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String templatePath = requestURI.substring(requestURIPrefix.length() - 1);
        if (!SAFE_TEMPLATE_PATH.matcher(templatePath).matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return templatePathPrefix + templatePath;
    }

    /**
     * The sandbox resolver only assigns a non-production sandbox to requests authenticated from
     * the admin with sandbox preview enabled, so this restricts rendering to admin preview sessions.
     */
    protected boolean isPreviewSandBoxActive() {
        BroadleafRequestContext context = BroadleafRequestContext.getBroadleafRequestContext(false);
        return context != null && !context.isProductionSandBox();
    }

}
