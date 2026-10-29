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

import org.broadleafcommerce.common.io.AtomicMove;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CachingCompressedResponseFilterTest {

    private static final String HOST = "shop.example.com";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File resourceDir;
    private File cacheDir;
    private CachingCompressedResponseFilter filter;
    private ResourceHttpRequestHandler resourceHandler;
    private AtomicInteger chainInvocations;
    private FilterChain resourceChain;

    @Before
    public void setUp() throws Exception {
        resourceDir = folder.newFolder("resources");
        cacheDir = folder.newFolder("cache");
        writeResource("app.js", javascript("app"));
        writeResource("other.js", javascript("other"));

        resourceHandler = new ResourceHttpRequestHandler();
        resourceHandler.setLocations(Collections.singletonList(new FileSystemResource(resourceDir.getAbsolutePath() + "/")));
        resourceHandler.setServletContext(new MockServletContext());
        resourceHandler.afterPropertiesSet();

        chainInvocations = new AtomicInteger();
        resourceChain = (req, res) -> {
            chainInvocations.incrementAndGet();
            resourceHandler.handleRequest((HttpServletRequest) req, (HttpServletResponse) res);
        };

        filter = new CachingCompressedResponseFilter();
        filter.compressedFileTempDirectory = cacheDir.getAbsolutePath();
        filter.allowStaticFileCache = true;
        filter.useWhileInDefaultEnvironment = true;
        filter.cacheWhileInDefaultEnvironment = true;
        filter.isDefaultEnvironment = false;
        filter.useSendFile = false;
        filter.sendFileSize = 49152L;
        filter.extensionToMime.put(Pattern.compile(".*\\.js"), "application/javascript");
        filter.cacheAwareResponseHandler = new CacheAwareResponseHandler() {
            @Override
            public void setHeaders(HttpServletResponse response, File targetFile, String mimeType) {
                response.setContentType(mimeType);
            }
        };
        filter.initialized = true;
    }

    @Test
    public void getIsCachedAndServedFromCache() throws Exception {
        MockHttpServletResponse first = execute(get("app.js"));
        assertFullGzipBody(first, javascript("app"));
        assertEquals(1, cacheFiles().length);

        MockHttpServletResponse second = execute(get("app.js"));
        assertFullGzipBody(second, javascript("app"));
        assertEquals("cache hit must not invoke the downstream chain", 1, chainInvocations.get());
    }

    @Test
    public void headRequestDoesNotPopulateCache() throws Exception {
        MockHttpServletResponse head = execute(request("HEAD", "app.js"));
        assertEquals(200, head.getStatus());
        assertNull(head.getHeader("Content-Encoding"));
        assertEquals(0, cacheFiles().length);

        assertFullGzipBody(execute(get("app.js")), javascript("app"));
    }

    @Test
    public void conditionalRequestDoesNotPopulateCache() throws Exception {
        MockHttpServletRequest conditional = get("app.js");
        conditional.addHeader("If-Modified-Since", "Thu, 01 Jan 2099 00:00:00 GMT");
        MockHttpServletResponse notModified = execute(conditional);
        assertEquals(304, notModified.getStatus());
        assertEquals(0, notModified.getContentAsByteArray().length);
        assertEquals(0, cacheFiles().length);

        assertFullGzipBody(execute(get("app.js")), javascript("app"));
    }

    @Test
    public void conditionalRequestIsNotAnsweredFromCache() throws Exception {
        execute(get("app.js"));
        MockHttpServletRequest conditional = get("app.js");
        conditional.addHeader("If-None-Match", "\"anything\"");
        execute(conditional);
        assertEquals(2, chainInvocations.get());
    }

    @Test
    public void rangeRequestDoesNotPopulateCache() throws Exception {
        MockHttpServletRequest range = get("app.js");
        range.addHeader("Range", "bytes=0-0");
        MockHttpServletResponse partial = execute(range);
        assertEquals(206, partial.getStatus());
        assertNull(partial.getHeader("Content-Encoding"));
        assertEquals(1, partial.getContentAsByteArray().length);
        assertEquals(0, cacheFiles().length);

        assertFullGzipBody(execute(get("app.js")), javascript("app"));
    }

    @Test
    public void errorResponseDoesNotPopulateCache() throws Exception {
        FilterChain notFound = (req, res) -> ((HttpServletResponse) res).sendError(404);
        MockHttpServletResponse response = execute(get("missing.js"), notFound);
        assertEquals(404, response.getStatus());
        assertEquals(0, cacheFiles().length);
    }

    @Test
    public void nonOkResponseIsReturnedButNotCached() throws Exception {
        byte[] body = "not here".getBytes(StandardCharsets.UTF_8);
        FilterChain notFound = (req, res) -> {
            ((HttpServletResponse) res).setStatus(404);
            res.getOutputStream().write(body);
        };
        MockHttpServletResponse response = execute(get("missing.js"), notFound);
        assertEquals(404, response.getStatus());
        assertArrayEquals(body, gunzip(response.getContentAsByteArray()));
        assertEquals(0, cacheFiles().length);
    }

    @Test
    public void privateOrCookieResponsesAreNotCached() throws Exception {
        byte[] body = "var user = 'alice';".getBytes(StandardCharsets.UTF_8);
        FilterChain noStore = (req, res) -> {
            ((HttpServletResponse) res).setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
            res.getOutputStream().write(body);
        };
        FilterChain cookie = (req, res) -> {
            ((HttpServletResponse) res).addCookie(new Cookie("SESSION", "abc"));
            res.getOutputStream().write(body);
        };
        assertArrayEquals(body, gunzip(execute(get("user.js"), noStore).getContentAsByteArray()));
        assertArrayEquals(body, gunzip(execute(get("user.js"), cookie).getContentAsByteArray()));
        assertEquals(0, cacheFiles().length);
    }

    @Test
    public void truncatedResponseIsNotCached() throws Exception {
        FilterChain truncated = (req, res) -> {
            res.setContentLength(100);
            res.getOutputStream().write(new byte[10]);
        };
        execute(get("app.js"), truncated);
        assertEquals(0, cacheFiles().length);
    }

    @Test
    public void pathParameterUriDoesNotUseStaticCache() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/account;x.js");
        request.setServerName(HOST);
        request.addHeader("Accept-Encoding", "gzip");
        byte[] body = "<html>alice</html>".getBytes(StandardCharsets.UTF_8);
        FilterChain page = (req, res) -> res.getOutputStream().write(body);
        assertArrayEquals(body, gunzip(execute(request, page).getContentAsByteArray()));
        assertEquals(0, cacheFiles().length);
    }

    @Test
    public void cacheKeyIncludesHost() throws Exception {
        execute(get("app.js"));
        MockHttpServletRequest otherHost = get("app.js");
        otherHost.setServerName("other.example.com");
        execute(otherHost);
        assertEquals(2, cacheFiles().length);
        assertFalse(filter.prepareTargetFile(get("app.js")).equals(filter.prepareTargetFile(otherHost)));
    }

    @Test
    public void poisonedCacheFileIsReplaced() throws Exception {
        File target = filter.prepareTargetFile(get("app.js"));
        Files.write(target.toPath(), gzip(new byte[0]));

        assertFullGzipBody(execute(get("app.js")), javascript("app"));
        assertEquals(1, chainInvocations.get());
        assertArrayEquals(javascript("app"), gunzip(Files.readAllBytes(target.toPath())));
    }

    @Test
    public void expiredCacheFileIsRegenerated() throws Exception {
        execute(get("app.js"));
        File target = filter.prepareTargetFile(get("app.js"));
        assertTrue(target.setLastModified(System.currentTimeMillis() - 2 * 86400 * 1000L));
        writeResource("app.js", javascript("app-v2"));

        assertFullGzipBody(execute(get("app.js")), javascript("app-v2"));
        assertEquals(2, chainInvocations.get());
    }

    @Test
    public void purgeRemovesOnlyCacheFiles() throws Exception {
        execute(get("app.js"));
        File unrelated = new File(cacheDir, "keep.txt");
        Files.write(unrelated.toPath(), new byte[] {1});
        File legacy = new File(cacheDir, "0123456789abcdef0123456789abcdef.gz");
        Files.write(legacy.toPath(), gzip(new byte[0]));

        filter.purgeStaticFileCache();

        assertEquals(0, cacheFiles().length);
        assertFalse(legacy.exists());
        assertTrue(unrelated.exists());
    }

    @Test
    public void defaultDirectoryIsDedicatedSubdirectoryOfTempDir() {
        filter.compressedFileTempDirectory = "none";
        File dir = filter.getStaticCacheDirectory();
        assertEquals(new File(System.getProperty("java.io.tmpdir"), "blc-compressed-static-cache"), dir);
    }

    @Test
    public void maxEntriesLimitsCacheFiles() throws Exception {
        filter.staticFileCacheMaxEntries = 1;
        assertFullGzipBody(execute(get("app.js")), javascript("app"));
        assertFullGzipBody(execute(get("other.js")), javascript("other"));
        assertEquals(1, cacheFiles().length);
    }

    @Test
    public void nonResourceHandlerResponseIsNotCached() throws Exception {
        MockHttpServletRequest request = get("user.json");
        request.removeAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        byte[] body = "{\"user\":\"alice\"}".getBytes(StandardCharsets.UTF_8);
        FilterChain controller = (req, res) -> res.getOutputStream().write(body);
        MockHttpServletResponse response = execute(request, controller);
        assertEquals(200, response.getStatus());
        assertArrayEquals(body, gunzip(response.getContentAsByteArray()));
        assertEquals(0, cacheFiles().length);
    }

    @Test
    public void responseVaryingOnOtherHeadersIsNotCached() throws Exception {
        byte[] body = "var user = 'alice';".getBytes(StandardCharsets.UTF_8);
        FilterChain varying = (req, res) -> {
            ((HttpServletResponse) res).addHeader("Vary", "Accept-Encoding, Cookie");
            res.getOutputStream().write(body);
        };
        assertArrayEquals(body, gunzip(execute(get("user.js"), varying).getContentAsByteArray()));
        assertEquals(0, cacheFiles().length);
    }

    @Test
    public void corsVaryHeadersStillAllowCaching() throws Exception {
        FilterChain cors = (req, res) -> {
            HttpServletResponse response = (HttpServletResponse) res;
            response.addHeader("Vary", "Origin");
            response.addHeader("Vary", "Access-Control-Request-Method");
            response.addHeader("Vary", "Access-Control-Request-Headers");
            resourceChain.doFilter(req, res);
        };
        assertFullGzipBody(execute(get("app.js"), cors), javascript("app"));
        assertEquals(1, cacheFiles().length);
    }

    @Test
    public void authorizationOrQueryStringRequestsBypassCache() throws Exception {
        execute(get("app.js"));
        MockHttpServletRequest authorized = get("app.js");
        authorized.addHeader("Authorization", "Bearer token");
        assertFullGzipBody(execute(authorized), javascript("app"));
        MockHttpServletRequest query = get("other.js");
        query.setQueryString("user=alice");
        assertFullGzipBody(execute(query), javascript("other"));
        assertEquals(3, chainInvocations.get());
        assertEquals(1, cacheFiles().length);
    }

    @Test
    public void expiredEntriesAreEvictedWhenCacheIsFull() throws Exception {
        filter.staticFileCacheMaxEntries = 1;
        execute(get("app.js"));
        File appTarget = filter.prepareTargetFile(get("app.js"));
        assertTrue(appTarget.setLastModified(System.currentTimeMillis() - 2 * 86400 * 1000L));
        filter.lastStaticCacheScan = 0L;

        assertFullGzipBody(execute(get("other.js")), javascript("other"));
        assertFalse(appTarget.exists());
        assertTrue(filter.prepareTargetFile(get("other.js")).exists());
        assertEquals(1, cacheFiles().length);
    }

    @Test
    public void failedCacheWriteDoesNotConsumeAnEntry() throws Exception {
        filter.staticFileCacheMaxEntries = 1;
        AtomicMove realMove = filter.atomicMove;
        filter.atomicMove = (src, dest) -> {
            throw new IOException("disk full");
        };
        assertFullGzipBody(execute(get("app.js")), javascript("app"));
        assertEquals(0, cacheFiles().length);

        filter.atomicMove = realMove;
        assertFullGzipBody(execute(get("other.js")), javascript("other"));
        assertTrue(filter.prepareTargetFile(get("other.js")).exists());
        String[] leftovers = cacheDir.list((dir, name) -> name.endsWith(".tmp") || name.endsWith(".tmpgz"));
        assertEquals(0, leftovers.length);
    }

    @Test
    public void rewritingAnExistingEntryCountsOnce() throws Exception {
        filter.staticFileCacheMaxEntries = 2;
        File target = filter.prepareTargetFile(get("app.js"));
        for (int i = 0; i < 2; i++) {
            File compressed = new File(cacheDir, "app" + i + ".tmpgz");
            Files.write(compressed.toPath(), gzip(javascript("app")));
            assertTrue(filter.storeStaticCacheFile(compressed, target));
        }
        assertFullGzipBody(execute(get("other.js")), javascript("other"));
        assertEquals(2, cacheFiles().length);
    }

    private MockHttpServletResponse execute(MockHttpServletRequest request) throws Exception {
        return execute(request, resourceChain);
    }

    private MockHttpServletResponse execute(MockHttpServletRequest request, FilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternalUnlessIgnored(request, response, chain);
        return response;
    }

    private MockHttpServletRequest get(String file) {
        return request("GET", file);
    }

    private MockHttpServletRequest request(String method, String file) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/js/" + file);
        request.setServerName(HOST);
        request.addHeader("Accept-Encoding", "gzip, deflate");
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, file);
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, resourceHandler);
        return request;
    }

    private void assertFullGzipBody(MockHttpServletResponse response, byte[] expected) throws IOException {
        assertEquals(200, response.getStatus());
        assertEquals("gzip", response.getHeader("Content-Encoding"));
        assertArrayEquals(expected, gunzip(response.getContentAsByteArray()));
    }

    private File[] cacheFiles() {
        File[] files = cacheDir.listFiles((dir, name) -> name.endsWith(".gz"));
        return files == null ? new File[0] : files;
    }

    private void writeResource(String name, byte[] content) throws IOException {
        Files.write(new File(resourceDir, name).toPath(), content);
    }

    private static byte[] javascript(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("function ").append(name.replace('-', '_')).append(i).append("() { return ").append(i).append("; }\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] gzip(byte[] content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(baos)) {
            out.write(content);
        }
        return baos.toByteArray();
    }

    private static byte[] gunzip(byte[] content) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(content))) {
            return StreamUtils.copyToByteArray(in);
        }
    }
}
