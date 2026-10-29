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
package org.broadleafcommerce.cms.web.processor;

import org.apache.commons.lang3.StringUtils;
import org.broadleafcommerce.cms.file.service.StaticAssetService;
import org.broadleafcommerce.common.file.service.StaticAssetPathService;
import org.broadleafcommerce.common.util.BLCSystemProperty;
import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.presentation.condition.ConditionalOnTemplating;
import org.broadleafcommerce.presentation.dialect.AbstractBroadleafAttributeModifierProcessor;
import org.broadleafcommerce.presentation.model.BroadleafAttributeModifier;
import org.broadleafcommerce.presentation.model.BroadleafTemplateContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * A Thymeleaf processor that processes the given url through the StaticAssetService's
 * {@link StaticAssetService#convertAssetPath(String, String, boolean)} method to determine
 * the appropriate URL for the asset to be served from.
 *
 * @author apazzolini
 */
@Component("blUrlRewriteProcessor")
@ConditionalOnTemplating
public class UrlRewriteProcessor extends AbstractBroadleafAttributeModifierProcessor {

    private static final String SAFE_QUERY_PUNCTUATION = "-._~=&+";
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    @Resource(name = "blStaticAssetPathService")
    protected StaticAssetPathService staticAssetPathService;

    @Override
    public String getName() {
        return "src";
    }

    @Override
    public int getPrecedence() {
        return 1000;
    }

    @Override
    public BroadleafAttributeModifier getModifiedAttributes(
            String tagName,
            Map<String, String> tagAttributes,
            String attributeName,
            String attributeValue,
            BroadleafTemplateContext context
    ) {
        Map<String, String> newAttributes = new HashMap<>();
        newAttributes.put("src", getFullAssetPath(tagName, attributeValue, context));
        return new BroadleafAttributeModifier(newAttributes);
    }

    protected String getFullAssetPath(String tagName, String attributeValue, BroadleafTemplateContext context) {
        HttpServletRequest request = BroadleafRequestContext.getBroadleafRequestContext().getRequest();
        boolean secureRequest = true;
        if (request != null) {
            secureRequest = isRequestSecure(request);
        }

        String assetPath = parsePath(attributeValue, context);

        String extension = getFileExtension(assetPath);
        if (isImageTag(tagName) && isAdminRequest() && !isImageExtension(extension)) {
            String defaultFileTypeImagePath = getDefaultFileTypeImagePath(extension);
            String queryString = sanitizeQueryString(getQueryString(assetPath));

            // Only the constant placeholder path is evaluated. The query string comes from the already-evaluated
            // (and possibly user-supplied) asset path, so it must never be fed back into the expression parser.
            assetPath = parsePath(defaultFileTypeImagePath, context) + queryString;
        }

        // We are forcing an evaluation of @{} from Thymeleaf above which will automatically add a contextPath,
        // no need to add it twice
        return staticAssetPathService.convertAssetPath(assetPath, null, secureRequest);
    }

    /**
     * @return true if the current request.scheme = HTTPS or if the request.isSecure value is true.
     */
    protected boolean isRequestSecure(HttpServletRequest request) {
        return ("HTTPS".equalsIgnoreCase(request.getScheme()) || request.isSecure());
    }

    protected boolean isImageTag(String tagName) {
        return "img".equals(tagName);
    }

    protected boolean isAdminRequest() {
        return BroadleafRequestContext.getBroadleafRequestContext().getAdmin();
    }

    protected Boolean isImageExtension(String extension) {
        if (StringUtils.isBlank(extension)) {
            return false;
        }
        String imageExtensions = getImageExtensions();
        if (imageExtensions == null) {
            return false;
        }
        for (String imageExtension : imageExtensions.split(",")) {
            if (extension.equalsIgnoreCase(imageExtension.trim())) {
                return true;
            }
        }
        return false;
    }

    protected String getImageExtensions() {
        return BLCSystemProperty.resolveSystemProperty("admin.image.file.extensions");
    }

    protected String parsePath(String attributeValue, BroadleafTemplateContext context) {
        String newAttributeValue = attributeValue;
        if (newAttributeValue.startsWith("/")) {
            newAttributeValue = "@{ " + newAttributeValue + " }";
        }
        return context.parseExpression(newAttributeValue);
    }

    protected String getFileExtension(String assetPath) {
        if (assetPath == null) {
            return "";
        }
        String path = assetPath;
        int queryStartIndex = getQueryStartIndex(path);
        if (queryStartIndex >= 0) {
            path = path.substring(0, queryStartIndex);
        }
        int fragmentStartIndex = path.indexOf('#');
        if (fragmentStartIndex >= 0) {
            path = path.substring(0, fragmentStartIndex);
        }

        int extensionStartIndex = path.lastIndexOf('.');
        if (extensionStartIndex < 0 || extensionStartIndex < path.lastIndexOf('/')) {
            return "";
        }

        return path.substring(extensionStartIndex + 1).toLowerCase();
    }

    protected String getDefaultFileTypeImagePath(String extension) {
        String imageUrl;
        switch (extension) {
            case "txt":
                imageUrl = "/img/admin/file-txt.png";
                break;
            case "pdf":
                imageUrl = "/img/admin/file-pdf.png";
                break;
            case "doc":
            case "docx":
                imageUrl = "/img/admin/file-doc.png";
                break;
            case "xls":
            case "xlsx":
                imageUrl = "/img/admin/file-xls.png";
                break;
            case "ppt":
            case "pptx":
                imageUrl = "/img/admin/file-ppt.png";
                break;
            default:
                imageUrl = "/img/admin/file-unkn.png";
        }
        return imageUrl;
    }

    protected String getQueryString(String assetPath) {
        if (assetPath == null) {
            return "";
        }
        int queryStartIndex = getQueryStartIndex(assetPath);

        return (queryStartIndex > 0) ? assetPath.substring(queryStartIndex) : "";
    }

    protected int getQueryStartIndex(String assetPath) {
        return assetPath.indexOf('?');
    }

    /**
     * Percent-encodes every character of the given query string (including its leading '?') that is not a URL
     * unreserved character, a query delimiter, '+' or part of an existing percent-encoded triplet, so it can be
     * safely appended to an already-evaluated URL as data without changing how existing values decode.
     */
    protected String sanitizeQueryString(String queryString) {
        if (StringUtils.isEmpty(queryString)) {
            return "";
        }
        String query = queryString.startsWith("?") ? queryString.substring(1) : queryString;
        int fragmentStartIndex = query.indexOf('#');
        if (fragmentStartIndex >= 0) {
            query = query.substring(0, fragmentStartIndex);
        }
        if (query.isEmpty()) {
            return "";
        }

        StringBuilder sanitized = new StringBuilder(query.length() + 1).append('?');
        byte[] bytes = query.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            char c = (char) (b & 0xFF);
            if (isSafeQueryCharacter(c) || (c == '%' && isPercentEncodedTriplet(bytes, i))) {
                sanitized.append(c);
            } else {
                sanitized.append('%').append(HEX_DIGITS[(b >> 4) & 0x0F]).append(HEX_DIGITS[b & 0x0F]);
            }
        }
        return sanitized.toString();
    }

    protected boolean isPercentEncodedTriplet(byte[] bytes, int index) {
        return index + 2 < bytes.length && isHexDigit(bytes[index + 1]) && isHexDigit(bytes[index + 2]);
    }

    private boolean isHexDigit(byte b) {
        return (b >= '0' && b <= '9') || (b >= 'a' && b <= 'f') || (b >= 'A' && b <= 'F');
    }

    protected boolean isSafeQueryCharacter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || SAFE_QUERY_PUNCTUATION.indexOf(c) >= 0;
    }

}
