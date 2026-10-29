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
package org.broadleafcommerce.cms.file.service;

import org.broadleafcommerce.common.file.service.StaticAssetPathServiceImpl;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static junit.framework.TestCase.assertTrue;

/**
 * Created by bpolster.
 */
public class StaticAssetServiceImplTest {

    @Test
    public void testConvertURLProperties() throws Exception {
        StaticAssetPathServiceImpl staticAssetPathService = new StaticAssetPathServiceImpl();
        staticAssetPathService.setStaticAssetUrlPrefix("cmsstatic");
        staticAssetPathService.setStaticAssetEnvironmentUrlPrefix("http://images.mysite.com/myapp/cmsstatic");

        String url = staticAssetPathService.convertAssetPath("/cmsstatic/product.jpg", "myapp", false);
        assertTrue(url.equals("http://images.mysite.com/myapp/cmsstatic/product.jpg"));

        staticAssetPathService.setStaticAssetEnvironmentUrlPrefix("http://images.mysite.com");
        url = staticAssetPathService.convertAssetPath("/cmsstatic/product.jpg", "myapp", false);
        assertTrue(url.equals("http://images.mysite.com/product.jpg"));

        url = staticAssetPathService.convertAssetPath("/cmsstatic/product.jpg", "myapp", true);
        assertTrue(url.equals("https://images.mysite.com/product.jpg"));


        staticAssetPathService.setStaticAssetEnvironmentUrlPrefix(null);
        url = staticAssetPathService.convertAssetPath("/cmsstatic/product.jpg", "myapp", true);
        assertTrue(url.equals("/myapp/cmsstatic/product.jpg"));

        url = staticAssetPathService.convertAssetPath("cmsstatic/product.jpg", "myapp", true);
        assertTrue(url.equals("/myapp/cmsstatic/product.jpg"));

    }

    @Test
    public void testWhenEmptyFileExtensions() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        MockMultipartFile file = new MockMultipartFile("text.txt", this.getClass().getResourceAsStream("/testfile/text-file.txt"));
        assetService.validateFileExtension(file);
    }

    @Test(expected = IOException.class)
    public void testThrowDisabledFileExtensions() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setDisabledFileExtensions("txt");
        MockMultipartFile file = new MockMultipartFile("text.txt", this.getClass().getResourceAsStream("/testfile/text-file.txt"));
        assetService.validateFileExtension(file);
    }

    @Test
    public void testOkWhenMultipleDisableFileExtensions() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setDisabledFileExtensions("pdf,png");
        MockMultipartFile file = new MockMultipartFile("text.txt", this.getClass().getResourceAsStream("/testfile/text-file.txt"));
        assetService.validateFileExtension(file);
    }

    @Test
    public void testWhitelistFirstDisableFileExtensions() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setDisabledFileExtensions("pdf,png");
        assetService.setAllowedFileExtensions("txt,png");
        InputStream resourceAsStream = this.getClass().getResourceAsStream("/testfile/text-file.txt");
        MockMultipartFile file = new MockMultipartFile("text.txt", resourceAsStream);
        assetService.validateFileExtension(file);
        file = new MockMultipartFile("img.png", this.getClass().getResourceAsStream("/testfile/img.png"));
        assetService.validateFileExtension(file);
    }

    @Test(expected = IOException.class)
    public void testWhitelistFileExtensions() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setAllowedFileExtensions("txt");
        MockMultipartFile file = new MockMultipartFile("text.txt", this.getClass().getResourceAsStream("/testfile/text-file.txt"));
        assetService.validateFileExtension(file);
        file = new MockMultipartFile("img.png", this.getClass().getResourceAsStream("/testfile/img.png"));
        assetService.validateFileExtension(file);
    }

    @Test(expected = IOException.class)
    public void testWhitelistRejectsImagePolyglotWithDisallowedName() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setAllowedFileExtensions("png");
        assetService.validateFileExtension(upload("test.html", pngWithHtmlPayload()));
    }

    @Test(expected = IOException.class)
    public void testWhitelistRejectsDisallowedContentWithAllowedName() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setAllowedFileExtensions("png");
        assetService.setRejectMismatchedContentType(false);
        assetService.validateFileExtension(upload("test.png", HTML_PAYLOAD.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void testWhitelistAcceptsMatchingNameAndContent() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setAllowedFileExtensions("png");
        assetService.validateFileExtension(upload("Test.PNG", resource("/testfile/img.png")));
    }

    @Test
    public void testWhitelistAcceptsAlternateExtensionOfDetectedType() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setAllowedFileExtensions("jpeg");
        assetService.validateFileExtension(upload("photo.jpeg", JPEG_HEADER));
    }

    @Test
    public void testWhitelistAcceptsTextSubtypeByName() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setAllowedFileExtensions("csv");
        assetService.validateFileExtension(upload("prices.csv", "sku,price\nA1,10.00\n".getBytes(StandardCharsets.UTF_8)));
    }

    @Test(expected = IOException.class)
    public void testBlacklistChecksFileNameExtension() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setDisabledFileExtensions("html");
        assetService.setRejectMismatchedContentType(false);
        assetService.validateFileExtension(upload("test.html", pngWithHtmlPayload()));
    }

    @Test(expected = IOException.class)
    public void testBlacklistChecksDetectedContentType() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setDisabledFileExtensions("pdf");
        assetService.setRejectMismatchedContentType(false);
        assetService.validateFileExtension(upload("brochure.png", PDF_HEADER));
    }

    @Test(expected = IOException.class)
    public void testRejectsNameThatDoesNotMatchContent() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setDisabledFileExtensions("pdf");
        assetService.validateFileExtension(upload("promo.html", pngWithHtmlPayload()));
    }

    @Test
    public void testMismatchCheckCanBeDisabled() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setDisabledFileExtensions("pdf");
        assetService.setRejectMismatchedContentType(false);
        assetService.validateFileExtension(upload("promo.html", pngWithHtmlPayload()));
    }

    @Test
    public void testAcceptsCommonTextAssets() throws IOException {
        StaticAssetServiceImpl assetService = new StaticAssetServiceImpl();
        assetService.setDisabledFileExtensions("pdf");
        assetService.validateFileExtension(upload("site.css", "body { color: red; }\n".getBytes(StandardCharsets.UTF_8)));
        assetService.validateFileExtension(upload("site.js", "var a = 1;\n".getBytes(StandardCharsets.UTF_8)));
        assetService.validateFileExtension(upload("data.json", "{\"a\": 1}\n".getBytes(StandardCharsets.UTF_8)));
        assetService.validateFileExtension(upload("page.html", HTML_PAYLOAD.getBytes(StandardCharsets.UTF_8)));
        assetService.validateFileExtension(upload("img.png", resource("/testfile/img.png")));
    }

    private static final String HTML_PAYLOAD = "<html><body><script>alert(document.cookie)</script></body></html>";

    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
            'J', 'F', 'I', 'F', 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00};

    private static final byte[] PDF_HEADER = "%PDF-1.4\n%\u00e2\u00e3\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);

    private static MockMultipartFile upload(String originalFilename, byte[] content) {
        return new MockMultipartFile("file", originalFilename, null, content);
    }

    private byte[] resource(String path) throws IOException {
        try (InputStream in = this.getClass().getResourceAsStream(path)) {
            return in.readAllBytes();
        }
    }

    private byte[] pngWithHtmlPayload() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(resource("/testfile/img.png"));
        out.write(HTML_PAYLOAD.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

}
