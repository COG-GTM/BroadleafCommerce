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
package org.broadleafcommerce.common.web.util;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CachingCompressedResponseFilterTest {

    private static final String SCRIPT = "function checkout() { return 'full script body'; }\n";

    private File cacheDir;
    private CachingCompressedResponseFilter filter;
    private AtomicInteger backendCalls;

    @Before
    public void setUp() throws Exception {
        cacheDir = Files.createTempDirectory("blc-compression-cache").toFile();
        backendCalls = new AtomicInteger();
        filter = new CachingCompressedResponseFilter();
        filter.compressionExtensionToMimeMappings = ".*\\.js:application/javascript,.*\\.css:text/css";
        filter.blackListURIs = "none";
        filter.allowStaticFileCache = true;
        filter.compressedFileTempDirectory = cacheDir.getAbsolutePath();
        filter.useSendFile = false;
        filter.resourceVersioningEnabled = true;
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        filter.environment = environment;
        filter.cacheAwareResponseHandler = new CacheAwareResponseHandler() {
            @Override
            public void setHeaders(HttpServletResponse response, File targetFile, String mimeType) {
                response.setContentType(mimeType);
            }
        };
        filter.afterPropertiesSet();
    }

    @After
    public void tearDown() {
        FileSystemUtils.deleteRecursively(cacheDir);
    }

    @Test
    public void testHeadRequestDoesNotPoisonCache() throws Exception {
        MockHttpServletResponse head = execute(request("HEAD", "/js/checkout.js"));
        assertEquals(200, head.getStatus());
        assertEquals(0, cacheFileCount());

        assertFullScriptServedAndCached();
    }

    @Test
    public void testRangeRequestDoesNotPoisonCache() throws Exception {
        MockHttpServletRequest range = request("GET", "/js/checkout.js");
        range.addHeader("Range", "bytes=0-0");
        MockHttpServletResponse partial = execute(range);
        assertEquals(206, partial.getStatus());
        assertEquals(SCRIPT.substring(0, 1), gunzip(partial));
        assertEquals(0, cacheFileCount());

        assertFullScriptServedAndCached();
    }

    @Test
    public void testConditionalRequestDoesNotPoisonCache() throws Exception {
        MockHttpServletRequest conditional = request("GET", "/js/checkout.js");
        conditional.addHeader("If-Modified-Since", "Thu, 01 Jan 2026 00:00:00 GMT");
        MockHttpServletResponse notModified = execute(conditional);
        assertEquals(304, notModified.getStatus());
        assertEquals(0, notModified.getContentAsByteArray().length);
        assertEquals(0, cacheFileCount());

        assertFullScriptServedAndCached();
    }

    @Test
    public void testErrorResponsesAreNotCached() throws Exception {
        MockHttpServletResponse notFound = execute(request("GET", "/js/missing.js"));
        assertEquals(404, notFound.getStatus());
        MockHttpServletResponse badRequest = execute(request("GET", "/js/bad.js"));
        assertEquals(400, badRequest.getStatus());
        assertEquals("bad request", gunzip(badRequest));
        assertEquals(0, cacheFileCount());
    }

    @Test
    public void testPrivateOrCookieResponsesAreNotCached() throws Exception {
        for (String uri : new String[] {"/js/private.js", "/js/no-store.js", "/js/cookie.js"}) {
            MockHttpServletResponse response = execute(request("GET", uri));
            assertEquals(200, response.getStatus());
            assertEquals(SCRIPT, gunzip(response));
            execute(request("GET", uri));
        }
        assertEquals(0, cacheFileCount());
        assertEquals(6, backendCalls.get());
    }

    @Test
    public void testCachedFileServesHeadButDefersRangeAndConditionalRequestsToBackend() throws Exception {
        assertFullScriptServedAndCached();
        int calls = backendCalls.get();

        MockHttpServletResponse head = execute(request("HEAD", "/js/checkout.js"));
        assertEquals(200, head.getStatus());
        assertEquals(calls, backendCalls.get());

        MockHttpServletRequest range = request("GET", "/js/checkout.js");
        range.addHeader("Range", "bytes=0-0");
        MockHttpServletResponse partial = execute(range);
        assertEquals(206, partial.getStatus());
        assertEquals(SCRIPT.substring(0, 1), gunzip(partial));

        MockHttpServletRequest conditional = request("GET", "/js/checkout.js");
        conditional.addHeader("If-Modified-Since", "Thu, 01 Jan 2026 00:00:00 GMT");
        assertEquals(304, execute(conditional).getStatus());
        assertEquals(calls + 2, backendCalls.get());

        assertFullScriptServedAndCached();
    }

    @Test
    public void testConcurrentCacheFillDoesNotAppendCachedBody() throws Exception {
        FilterChain privateResponseWhileAnotherRequestCaches = (req, res) -> {
            filter.doFilter(request("GET", "/js/race.js"), new MockHttpServletResponse(), backend());
            HttpServletResponse response = (HttpServletResponse) res;
            response.setHeader("Cache-Control", "private");
            response.setStatus(200);
            response.getOutputStream().write("private body".getBytes(StandardCharsets.UTF_8));
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/js/race.js"), response, privateResponseWhileAnotherRequestCaches);
        assertEquals("private body", gunzip(response));
        assertEquals(1, cacheFileCount());
    }

    @Test
    public void testCacheEntryLimitUnderConcurrency() throws Exception {
        filter.staticFileCacheMaxEntries = 2;
        int threads = 5;
        CountDownLatch allInBackend = new CountDownLatch(threads);
        FilterChain slowBackend = (req, res) -> {
            allInBackend.countDown();
            try {
                allInBackend.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            backend().doFilter(req, res);
        };
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<MockHttpServletResponse>> responses = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String uri = "/js/concurrent" + i + ".js";
                responses.add(executor.submit(() -> {
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    filter.doFilter(request("GET", uri), response, slowBackend);
                    return response;
                }));
            }
            for (Future<MockHttpServletResponse> response : responses) {
                assertEquals(SCRIPT, gunzip(response.get(10, TimeUnit.SECONDS)));
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(2, cacheFileCount());
    }

    @Test
    public void testDeletedEmptyCacheFileFreesEntry() throws Exception {
        filter.staticFileCacheMaxEntries = 1;
        File empty = new File(cacheDir, DigestUtils.md5Hex("/js/empty.js") + ".gz");
        assertTrue(empty.createNewFile());

        assertEquals(SCRIPT, gunzip(execute(request("GET", "/js/other.js"))));
        assertEquals(1, cacheFileCount());
        assertTrue(empty.exists());

        assertEquals(SCRIPT, gunzip(execute(request("GET", "/js/empty.js"))));
        assertEquals(0, cacheFileCount());

        assertEquals(SCRIPT, gunzip(execute(request("GET", "/js/other.js"))));
        assertTrue(new File(cacheDir, DigestUtils.md5Hex("/js/other.js") + ".gz").exists());
        assertEquals(1, cacheFileCount());
    }

    @Test
    public void testNonGetMethodsBypassCache() throws Exception {
        MockHttpServletResponse post = execute(request("POST", "/js/checkout.js"));
        assertEquals(SCRIPT, gunzip(post));
        assertEquals(0, cacheFileCount());
    }

    @Test
    public void testCacheEntryLimit() throws Exception {
        filter.staticFileCacheMaxEntries = 2;
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = execute(request("GET", "/js/file" + i + ".js"));
            assertEquals(SCRIPT, gunzip(response));
        }
        assertEquals(2, cacheFileCount());
    }

    private void assertFullScriptServedAndCached() throws Exception {
        MockHttpServletResponse first = execute(request("GET", "/js/checkout.js"));
        assertEquals(200, first.getStatus());
        assertEquals(SCRIPT, gunzip(first));
        assertEquals(1, cacheFileCount());

        int calls = backendCalls.get();
        MockHttpServletResponse second = execute(request("GET", "/js/checkout.js"));
        assertEquals(200, second.getStatus());
        assertEquals(SCRIPT, gunzip(second));
        assertEquals(calls, backendCalls.get());
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Accept-Encoding", "gzip, deflate");
        return request;
    }

    private MockHttpServletResponse execute(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, backend());
        return response;
    }

    /**
     * Mimics the status and body behaviour of Spring's ResourceHttpRequestHandler.
     */
    private FilterChain backend() {
        return (req, res) -> {
            backendCalls.incrementAndGet();
            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) res;
            byte[] body = SCRIPT.getBytes(StandardCharsets.UTF_8);
            String uri = request.getRequestURI();
            if (uri.endsWith("missing.js")) {
                response.sendError(404);
                return;
            }
            if (uri.endsWith("bad.js")) {
                response.setStatus(400);
                response.getOutputStream().write("bad request".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (uri.endsWith("private.js")) {
                response.setHeader("Cache-Control", "private, max-age=60");
            } else if (uri.endsWith("no-store.js")) {
                response.setHeader("Cache-Control", "no-store");
            } else if (uri.endsWith("cookie.js")) {
                response.addCookie(new Cookie("SESSION", "secret"));
            }
            response.setContentType("application/javascript");
            if (request.getHeader("If-Modified-Since") != null) {
                response.setStatus(304);
                return;
            }
            if (request.getHeader("Range") != null) {
                response.setStatus(206);
                response.setHeader("Content-Range", "bytes 0-0/" + body.length);
                response.getOutputStream().write(body, 0, 1);
                return;
            }
            response.setStatus(200);
            response.setContentLength(body.length);
            if (!"HEAD".equals(request.getMethod())) {
                response.getOutputStream().write(body);
            }
        };
    }

    private String gunzip(MockHttpServletResponse response) throws IOException {
        assertEquals("gzip", response.getHeader("Content-Encoding"));
        byte[] bytes = response.getContentAsByteArray();
        return new String(
                StreamUtils.copyToByteArray(new GZIPInputStream(new ByteArrayInputStream(bytes))),
                StandardCharsets.UTF_8
        );
    }

    private int cacheFileCount() {
        String[] files = cacheDir.list((dir, name) -> name.endsWith(".gz"));
        return files == null ? 0 : files.length;
    }
}
