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
package org.broadleafcommerce.cms.web.file;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StaticAssetViewTest {

    @Test
    public void testImageServedInlineWithNosniff() throws Exception {
        MockHttpServletResponse response = render("image/png");
        assertEquals("image/png", response.getContentType());
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertNull(response.getHeader("Content-Disposition"));
    }

    @Test
    public void testHtmlServedAsAttachment() throws Exception {
        MockHttpServletResponse response = render("text/html");
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("attachment", response.getHeader("Content-Disposition"));
    }

    @Test
    public void testSvgAndXmlServedAsAttachment() throws Exception {
        assertEquals("attachment", render("image/svg+xml").getHeader("Content-Disposition"));
        assertEquals("attachment", render("application/xhtml+xml").getHeader("Content-Disposition"));
        assertEquals("attachment", render("text/xml").getHeader("Content-Disposition"));
    }

    @Test
    public void testScriptAndStylesheetServedInline() throws Exception {
        assertNull(render("text/css").getHeader("Content-Disposition"));
        assertNull(render("application/javascript").getHeader("Content-Disposition"));
    }

    @Test
    public void testMissingMimeTypeServedAsOctetStream() throws Exception {
        MockHttpServletResponse response = render(null);
        assertEquals("application/octet-stream", response.getContentType());
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertNull(response.getHeader("Content-Disposition"));
    }

    @Test
    public void testForceDownloadCanBeDisabled() throws Exception {
        StaticAssetView view = new StaticAssetView();
        view.setForceDownloadActiveContent(false);
        MockHttpServletResponse response = render(view, "text/html");
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertNull(response.getHeader("Content-Disposition"));
    }

    private MockHttpServletResponse render(String mimeType) throws Exception {
        return render(new StaticAssetView(), mimeType);
    }

    private MockHttpServletResponse render(StaticAssetView view, String mimeType) throws Exception {
        File asset = createAsset();
        try {
            Map<String, Object> model = new HashMap<>();
            model.put("cacheFilePath", asset.getAbsolutePath());
            model.put("mimeType", mimeType);
            MockHttpServletResponse response = new MockHttpServletResponse();
            view.render(model, new MockHttpServletRequest(), response);
            return response;
        } finally {
            asset.delete();
        }
    }

    private File createAsset() throws IOException {
        File asset = File.createTempFile("static-asset", ".bin");
        Files.write(asset.toPath(), "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));
        return asset;
    }

}
