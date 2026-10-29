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

import org.broadleafcommerce.common.file.service.StaticAssetPathService;
import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.presentation.model.BroadleafTemplateContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UrlRewriteProcessorTest {

    private static final String TEMPLATE_EXPRESSION = "@{*{fields['url'].value}}";

    private final List<String> parsedExpressions = new ArrayList<>();
    private String storedUrl;
    private boolean adminRequest = true;
    private UrlRewriteProcessor processor;

    @Before
    public void setUp() {
        BroadleafRequestContext.setBroadleafRequestContext(new BroadleafRequestContext());
        processor = new UrlRewriteProcessor() {
            @Override
            protected boolean isAdminRequest() {
                return adminRequest;
            }

            @Override
            protected String getImageExtensions() {
                return "bmp,jpg,jpeg,png,img,tiff,gif";
            }
        };
        processor.staticAssetPathService = (StaticAssetPathService) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { StaticAssetPathService.class },
                (proxy, method, args) -> "convertAssetPath".equals(method.getName()) ? args[0] : null);
    }

    @After
    public void tearDown() {
        BroadleafRequestContext.setBroadleafRequestContext(null);
    }

    private BroadleafTemplateContext context() {
        return (BroadleafTemplateContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { BroadleafTemplateContext.class },
                (proxy, method, args) -> {
                    if (!"parseExpression".equals(method.getName())) {
                        return null;
                    }
                    String expression = (String) args[0];
                    parsedExpressions.add(expression);
                    if (TEMPLATE_EXPRESSION.equals(expression)) {
                        return storedUrl;
                    }
                    if (expression.startsWith("@{ ") && expression.endsWith(" }")) {
                        return "/ctx" + expression.substring(3, expression.length() - 2);
                    }
                    throw new AssertionError("Unexpected expression: " + expression);
                });
    }

    private String render(String url) {
        storedUrl = url;
        return processor.getFullAssetPath("img", TEMPLATE_EXPRESSION, context());
    }

    @Test
    public void storedQueryStringIsNeverReEvaluatedAsAnExpression() {
        String payload = "/x.txt?__${T(java.lang.Runtime).getRuntime()}__ } + ${1+1} + @{ b";

        String result = render(payload);

        assertEquals(2, parsedExpressions.size());
        assertEquals(TEMPLATE_EXPRESSION, parsedExpressions.get(0));
        assertEquals("@{ /img/admin/file-txt.png }", parsedExpressions.get(1));
        for (String expression : parsedExpressions.subList(1, parsedExpressions.size())) {
            assertFalse(expression, expression.contains("$"));
            assertFalse(expression, expression.contains("Runtime"));
        }
        assertTrue(result, result.startsWith("/ctx/img/admin/file-txt.png?"));
        assertFalse(result, result.contains("$"));
        assertFalse(result, result.contains("{"));
        assertFalse(result, result.contains("}"));
        assertFalse(result, result.contains(" "));
    }

    @Test
    public void namedThumbnailOperationIsPreservedOnPlaceholderIcon() {
        assertEquals("/ctx/img/admin/file-pdf.png?largeAdminThumbnail", render("/cms/doc.pdf?largeAdminThumbnail"));
        assertEquals("/ctx/img/admin/file-doc.png?a=1&b=two", render("/cms/doc.docx?a=1&b=two"));
        assertEquals("/ctx/img/admin/file-xls.png", render("/cms/sheet.xlsx"));
    }

    @Test
    public void imageAssetsAreNotReplacedOrReEvaluated() {
        assertEquals("/cms/photo.PNG?largeAdminThumbnail", render("/cms/photo.PNG?largeAdminThumbnail"));
        assertEquals(1, parsedExpressions.size());
    }

    @Test
    public void storefrontRequestsUseTheEvaluatedPathAsIs() {
        adminRequest = false;
        assertEquals("/cms/doc.txt?x", render("/cms/doc.txt?x"));
        assertEquals(1, parsedExpressions.size());
    }

    @Test
    public void dotOrSecondQuestionMarkInQueryStringDoesNotThrow() {
        assertEquals("/ctx/img/admin/file-txt.png?a.b%3Fc", render("/cms/doc.txt?a.b?c"));
        assertEquals("/ctx/img/admin/file-unkn.png?v=1.2", render("/cms/download?v=1.2"));
    }

    @Test
    public void getFileExtensionIgnoresQueryStringFragmentAndDirectories() {
        assertEquals("txt", processor.getFileExtension("/a.b/c.TXT?x.y?z"));
        assertEquals("", processor.getFileExtension("/a.b/c?x.y"));
        assertEquals("pdf", processor.getFileExtension("/c.pdf#page.2"));
        assertEquals("", processor.getFileExtension("/c"));
    }

    @Test
    public void isImageExtensionRequiresExactMatch() {
        assertTrue(processor.isImageExtension("png"));
        assertTrue(processor.isImageExtension("JPG"));
        assertFalse(processor.isImageExtension("ng"));
        assertFalse(processor.isImageExtension(""));
        assertFalse(processor.isImageExtension("txt"));
    }

    @Test
    public void sanitizeQueryStringPercentEncodesUnsafeCharacters() {
        assertEquals("", processor.sanitizeQueryString(""));
        assertEquals("", processor.sanitizeQueryString("?"));
        assertEquals("?largeAdminThumbnail", processor.sanitizeQueryString("?largeAdminThumbnail"));
        assertEquals("?a%20%7D%20+%20%24%7B1+1%7D%20+%20%40%7B%20b",
                processor.sanitizeQueryString("?a } + ${1+1} + @{ b"));
        assertEquals("?q=%C3%A9", processor.sanitizeQueryString("?q=\u00e9"));
        assertEquals("?a=1", processor.sanitizeQueryString("?a=1#frag"));
        assertEquals("?q=a+b&path=x%2Fy", processor.sanitizeQueryString("?q=a+b&path=x%2Fy"));
        assertEquals("?p=100%25&bad=%25zz&end=%25", processor.sanitizeQueryString("?p=100%25&bad=%zz&end=%"));
    }
}
