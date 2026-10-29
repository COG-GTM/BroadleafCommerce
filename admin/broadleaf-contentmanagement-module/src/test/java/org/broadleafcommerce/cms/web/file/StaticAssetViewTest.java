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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StaticAssetViewTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private StaticAssetView view;
    private File assetFile;

    @Before
    public void setUp() throws Exception {
        view = new StaticAssetView();
        assetFile = tempFolder.newFile("asset");
        Files.write(assetFile.toPath(), "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void htmlAssetIsDownloadedAndSandboxed() throws Exception {
        MockHttpServletResponse response = render("text/html");

        assertEquals("attachment", response.getHeader("Content-Disposition"));
        assertTrue(response.getHeader("Content-Security-Policy").startsWith("sandbox"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    }

    @Test
    public void activeContentMatchIgnoresCaseAndParameters() throws Exception {
        assertEquals("attachment", render("Text/HTML; charset=UTF-8").getHeader("Content-Disposition"));
        assertEquals("attachment", render("image/svg+xml").getHeader("Content-Disposition"));
        assertEquals("attachment", render("application/xhtml+xml").getHeader("Content-Disposition"));
    }

    @Test
    public void imageAssetIsStillServedInline() throws Exception {
        MockHttpServletResponse response = render("image/png");

        assertEquals("image/png", response.getContentType());
        assertNull(response.getHeader("Content-Disposition"));
        assertNull(response.getHeader("Content-Security-Policy"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    }

    @Test
    public void notModifiedResponsesAlsoCarryTheHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("If-Modified-Since", System.currentTimeMillis());

        MockHttpServletResponse response = render("text/html", request);

        assertEquals(304, response.getStatus());
        assertEquals("attachment", response.getHeader("Content-Disposition"));
    }

    private MockHttpServletResponse render(String mimeType) throws Exception {
        return render(mimeType, new MockHttpServletRequest());
    }

    private MockHttpServletResponse render(String mimeType, MockHttpServletRequest request) throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("cacheFilePath", assetFile.getAbsolutePath());
        model.put("mimeType", mimeType);
        MockHttpServletResponse response = new MockHttpServletResponse();
        view.render(model, request, response);
        return response;
    }
}
