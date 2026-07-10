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

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping(PreviewTemplateController.REQUEST_MAPPING_PREFIX + "**")
public class PreviewTemplateController {
    public static final String REQUEST_MAPPING_PREFIX = "/preview/";
    private final String templatePathPrefix = "templates";

    /**
     * Allow-list of characters permitted in a preview template path. Only alphanumerics, hyphen,
     * underscore, dot and the path separator are accepted. Anything outside this set (including the
     * characters used by Thymeleaf/SpEL expression syntax such as {@code $ # ~ @ { } : ()} and
     * whitespace) is rejected before the value is ever handed to the view resolver.
     */
    private static final Pattern SAFE_TEMPLATE_PATH = Pattern.compile("[A-Za-z0-9_./-]+");

    @RequestMapping
    public String displayPreview(HttpServletRequest httpServletRequest) {
        String requestURIPrefix = httpServletRequest.getContextPath() + REQUEST_MAPPING_PREFIX;
        String requestURI = httpServletRequest.getRequestURI();
        if (requestURI.length() < requestURIPrefix.length()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // The leading slash is intentionally retained so the resulting view name is
        // templatePathPrefix + "/" + <path>.
        String templatePath = requestURI.substring(requestURIPrefix.length() - 1);
        validateTemplatePath(templatePath);
        return templatePathPrefix + templatePath;
    }

    /**
     * Rejects any request whose template path could be used for view-name injection (Thymeleaf
     * SSTI) or path traversal. The value must contain only allow-listed characters and must not
     * contain a parent-directory reference.
     */
    protected void validateTemplatePath(String templatePath) {
        if (templatePath == null || templatePath.isEmpty()
                || !SAFE_TEMPLATE_PATH.matcher(templatePath).matches()
                || templatePath.contains("..")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

}
