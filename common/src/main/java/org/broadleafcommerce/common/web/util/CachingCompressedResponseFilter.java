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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.broadleafcommerce.common.io.AtomicMove;
import org.broadleafcommerce.common.io.AtomicMoveImpl;
import org.broadleafcommerce.common.web.filter.AbstractIgnorableOncePerRequestFilter;
import org.broadleafcommerce.common.web.filter.FilterOrdered;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Provides either dynamic, or cached static compression of response data. The 'filter.compression.enabled' property must
 * be set to true to enable this filter.
 * </p>
 * Cached static file compression generally refers to compression requests for static artifacts, such as css, js and similar files. To
 * allow static file caching, the 'filter.compression.allow.static.file.cache' property must be true and the file
 * must be recognized in the list of extension to mimetype mapping represented in the 'filter.compression.extension.mime.mappings'
 * property. Should a static file fail to be found in the mapping list, the system will fallback to dynamic compression for
 * the artifact. The 'filter.compression.extension.mime.mappings' property is a comma delimited list of request URI matching
 * regular expressions to mime types. For example, the following property setting would limit static compressed file caching
 * to only files that end in the '.css' and '.js' extensions: {@code filter.compression.extension.mime.mappings=.*\\.css:text/css,.*\\.js}.
 * See the javadoc for {@link #compressionExtensionToMimeMappings} for the default values. 'filter.compression.allow.static.file.cache'
 * is false by default, which makes the most sense when a CDN is in place, since the CDN will be the primary source of
 * asset caching.
 * </p>
 * The directory in which cached static files should be stored is denoted via the 'filter.compression.file.temp.directory'
 * property. By default, this property is set to 'none', which signifies that a dedicated 'blc-compressed-static-cache'
 * directory under the standard java temp directory should be used. Cached files in that directory are purged when the
 * filter starts (see 'filter.compression.static.file.cache.purge.on.startup') and expire after
 * 'filter.compression.static.file.cache.ttl.seconds'.
 * If using static file compression caching, make sure you have enough free hard disk space to accommodate compressed versions
 * of all the interesting static files.
 * </p>
 * Dynamic compression generally refers to compression requests for dynamic web pages, such a standard HTML page requests,
 * or JSON responses for RESTful endpoints. There is no caching of the compressed response and the response is compressed
 * on every request.
 * </p>
 * There is an additional sendfile optimization for static/cached files. For larger files, the OS can use the 'sendfile'
 * optimization to increase network throughput and reduce CPU overhead. By default, this is enabled for files over 49152 bytes.
 * This threshold can be changed via the 'filter.compression.sendfile.size' property. While the sendfile feature is enabled
 * by default, it can be turned off altogether via the 'filter.compression.use.sendfile' property (set to false).
 * </p>
 * Certain request URI can be blacklisted for compression altogether via the 'filter.compression.blacklist.uri.regex'
 * property. This is a comma delimited list of regular expression that, when matched against a request URI, will cause
 * that URI to not be compressed.
 * </p>
 * The static cache is keyed by host name and request URI. It is only consulted for plain GET requests without Range or
 * conditional headers and without ';' path parameters, and it is only populated from a complete 200 response that does not
 * set a cookie, carry a Content-Range, or declare 'Cache-Control: private' or 'no-store'. Anything else falls back to the
 * downstream handler with dynamic compression. HEAD requests are never compressed. The number of cache files is capped via
 * 'filter.compression.static.file.cache.max.entries'.
 *
 * @author Jeff Fischer
 */
@Component("blCachingCompressedResponseFilter")
@ConditionalOnProperty("filter.compression.enabled")
public class CachingCompressedResponseFilter extends AbstractIgnorableOncePerRequestFilter {

    private static final Log LOG = LogFactory.getLog(CachingCompressedResponseFilter.class);
    /**
     * Whether or not compression is enabled in the default Spring environment. Used to turn off during development. True
     * by default (compression enabled).
     */
    @Value("${filter.compression.use.default.environment:true}")
    protected Boolean useWhileInDefaultEnvironment = true;
    /**
     * It is likely resource versioning will be disabled in the default, development environment. This setting will tell
     * the system to not cache static resources in the local filesystem in the default environment to avoid static
     * asset caching issues where a versioned name change is not involved.
     */
    @Value("${filter.compression.cache.default.environment:false}")
    protected Boolean cacheWhileInDefaultEnvironment = false;
    /**
     * Specify a filesystem directory in which to store compressed static files. 'none' by default, which means use
     * the java temp directory.
     */
    @Value("${filter.compression.file.temp.directory:none}")
    protected String compressedFileTempDirectory;
    /**
     * The minimum size of a cached,compressed file (in bytes) for which to use the OS sendfile feature. 49152 bytes
     * by default.
     */
    @Value("${filter.compression.sendfile.size:49152}")
    protected long sendFileSize;
    /**
     * Whether or not to ever use the OS sendfile feature. True by default (use the feature).
     */
    @Value("${filter.compression.use.sendfile:true}")
    protected Boolean useSendFile;
    /**
     * Comma delimited URI matching regular expression to mime type mapping. The default value is
     * {@code .*\\.svg:image/svg+xml,.*\\.png:image/png,.*\\.xml:text/xml,.*\\.css:text/css,.*\\.js:application/javascript,.*\\.otf:application/x-font-opentype,.*\\.json:application/json,.*\\.css\.map:application/json,.*\\.js\.map:application/json}
     */
    @Value("${filter.compression.extension.mime.mappings:.*\\.svg:image/svg+xml,.*\\.png:image/png,.*\\.xml:text/xml,.*\\.css:text/css,.*\\.js:application/javascript,.*\\.otf:application/x-font-opentype,.*\\.json:application/json,.*\\.css\\.map:application/json,.*\\.js\\.map:application/json}")
    protected String compressionExtensionToMimeMappings;
    /**
     * Whether or not to cache static file compression results. The default value is false (don't use the cache).
     */
    @Value("${filter.compression.allow.static.file.cache:false}")
    protected Boolean allowStaticFileCache;
    /**
     * A comma delimited list of URI matching regular expressions for requests that should be ignored for any compression.
     * The default value is '.*\\.jpg,.*\\.jpeg,.*\\.gif,.*\\.png'.
     */
    @Value("${filter.compression.blacklist.uri.regex:.*\\.jpg,.*\\.jpeg,.*\\.gif,.*\\.png}")
    protected String blackListURIs;
    /**
     * Maximum age, in seconds, of a cached compressed static file before it is regenerated. A value of 0 or less disables
     * expiry. The default value is 86400 (one day).
     */
    @Value("${filter.compression.static.file.cache.ttl.seconds:86400}")
    protected long staticFileCacheTtlSeconds = 86400L;
    /**
     * Whether to delete previously cached compressed static files from the cache directory when the filter starts. The
     * default value is true.
     */
    @Value("${filter.compression.static.file.cache.purge.on.startup:true}")
    protected Boolean purgeStaticFileCacheOnStartup = true;
    /**
     * Maximum number of compressed static files this filter writes to the cache directory. Once reached, uncached static
     * files are compressed dynamically. A value of 0 or less disables the limit. The default value is 10000.
     */
    @Value("${filter.compression.static.file.cache.max.entries:10000}")
    protected int staticFileCacheMaxEntries = 10000;
    @Value("${resource.versioning.enabled:true}")
    protected Boolean resourceVersioningEnabled;
    protected Boolean isDefaultEnvironment = false;
    protected Boolean initialized = false;
    @Autowired
    protected Environment environment;
    @Autowired
    protected CacheAwareResponseHandler cacheAwareResponseHandler;
    protected AtomicMove atomicMove = new AtomicMoveImpl();
    protected Map<Pattern, String> extensionToMime = new HashMap<>();
    protected List<Pattern> blackListPatterns = new ArrayList<>();
    protected AtomicInteger staticFileCacheEntries;

    protected static final String DEFAULT_CACHE_DIRECTORY_NAME = "blc-compressed-static-cache";
    protected static final Pattern CACHE_FILE_NAME = Pattern.compile("([0-9a-f]{32}|[0-9a-f]{64})\\.gz");
    protected static final String[] UNCACHEABLE_REQUEST_HEADERS = {
            "Range", "If-Range", "If-Match", "If-None-Match", "If-Modified-Since", "If-Unmodified-Since"
    };
    /**
     * Smallest possible gzip member: 10 byte header, 2 byte empty deflate block and 8 byte trailer.
     */
    protected static final int MIN_GZIP_LENGTH = 20;

    @Override
    protected void doFilterInternalUnlessIgnored(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {
        if (!isDefaultEnvironment || useWhileInDefaultEnvironment) {
            if (!"HEAD".equals(request.getMethod()) && useGzipCompression(request, response)) {
                String mimeType = getMimeType(request);
                if (mimeType != null && shouldUseStaticCache() && isStaticCacheEligibleRequest(request)) {
                    boolean isValid = processStatic(request, response, chain, mimeType);
                    if (!isValid) {
                        processDynamic(request, response, chain);
                    }
                } else {
                    processDynamic(request, response, chain);
                }
            } else {
                chain.doFilter(request, response);
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public int getOrder() {
        return FilterOrdered.PRE_SECURITY_HIGH - 1200;
    }

    @Override
    protected void initFilterBean() throws ServletException {
        if (!initialized) {
            String[] pairs = compressionExtensionToMimeMappings.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                extensionToMime.put(Pattern.compile(keyValue[0]), keyValue[1]);
            }
            if (!"none".equals(blackListURIs)) {
                String[] rawPatterns = blackListURIs.split(",");
                for (String rawPattern : rawPatterns) {
                    blackListPatterns.add(Pattern.compile(rawPattern));
                }
            }
            isDefaultEnvironment = !(ArrayUtils.isNotEmpty(environment.getActiveProfiles())
                    && Arrays.binarySearch(environment.getActiveProfiles(), "default") < 0);

            if (!resourceVersioningEnabled && shouldUseStaticCache()) {
                LOG.warn("Static file compression cache is enabled, but resource versioning is not enabled. This can lead " +
                        "to unversioned resources being cached in the filesystem. If these resources are updated, you will " +
                        "not see the changes because of the unversioned file cache of the same name. It is recommended to " +
                        "not use static file compression cache when resource versioning is not enabled. Static file " +
                        "compression cache can be controlled with the 'filter.compression.allow.static.file.cache' property.");
            }
            if (shouldUseStaticCache() && purgeStaticFileCacheOnStartup) {
                purgeStaticFileCache();
            }
            initialized = true;
        }
    }

    protected boolean shouldUseStaticCache() {
        return allowStaticFileCache && (!isDefaultEnvironment || cacheWhileInDefaultEnvironment);
    }

    /**
     * Whether the static cache may serve or populate the response for this request. Only plain GET requests are eligible:
     * HEAD, Range and conditional requests are answered by the downstream handler (which produces the correct 206/304/412),
     * and URIs with ';' path parameters are excluded so a non-static route cannot be given a static-looking cache key.
     */
    protected boolean isStaticCacheEligibleRequest(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return false;
        }
        for (String header : UNCACHEABLE_REQUEST_HEADERS) {
            if (request.getHeader(header) != null) {
                return false;
            }
        }
        String uri = request.getRequestURI();
        return uri != null && uri.indexOf(';') < 0;
    }

    /**
     * Whether a response captured from the downstream chain is a complete, shareable response that may be cached.
     */
    protected boolean isStaticCacheableResponse(FileSystemResponseWrapper wrapper, File capturedBody) {
        if (wrapper.isErrorSent() || wrapper.isCookieAdded() || wrapper.isCommitted()
                || wrapper.getStatus() != HttpServletResponse.SC_OK
                || wrapper.containsHeader("Set-Cookie") || wrapper.containsHeader("Content-Range")) {
            return false;
        }
        String cacheControl = wrapper.getHeader("Cache-Control");
        if (cacheControl != null) {
            String directives = cacheControl.toLowerCase(Locale.ROOT);
            if (directives.contains("private") || directives.contains("no-store")) {
                return false;
            }
        }
        long bodyLength = capturedBody.length();
        if (bodyLength == 0L) {
            return false;
        }
        String contentLength = wrapper.getHeader("Content-Length");
        if (contentLength != null) {
            try {
                return Long.parseLong(contentLength.trim()) == bodyLength;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Basic integrity and freshness check of a cache file before it is served: it must not be expired, must look like a
     * gzip file, and must not decompress to an empty body (the gzip trailer records the uncompressed length).
     */
    protected boolean isValidStaticCacheFile(File targetFile) {
        long length = targetFile.length();
        if (length < MIN_GZIP_LENGTH) {
            return false;
        }
        if (staticFileCacheTtlSeconds > 0
                && System.currentTimeMillis() - targetFile.lastModified() > staticFileCacheTtlSeconds * 1000L) {
            return false;
        }
        try (RandomAccessFile raf = new RandomAccessFile(targetFile, "r")) {
            if (raf.read() != 0x1f || raf.read() != 0x8b) {
                return false;
            }
            raf.seek(length - 4);
            int uncompressedSize = raf.read() | (raf.read() << 8) | (raf.read() << 16) | (raf.read() << 24);
            return uncompressedSize != 0;
        } catch (IOException e) {
            return false;
        }
    }

    protected String getMimeType(HttpServletRequest request) {
        String response = null;
        String uri = request.getRequestURI().toLowerCase();
        for (Map.Entry<Pattern, String> entry : extensionToMime.entrySet()) {
            if (entry.getKey().matcher(uri).matches()) {
                response = entry.getValue();
                break;
            }
        }
        return response;
    }

    protected void processDynamic(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrapper);
        if (wrapper.getContentSize() == 0 || wrapper.containsHeader("Content-Range")) {
            // nothing to compress (204, 304, ...) or a partial body that must be sent as-is
            wrapper.copyBodyToResponse();
            return;
        }
        FastByteArrayOutputStream baos = new FastByteArrayOutputStream(1024);
        GZIPOutputStream zip = new GZIPOutputStream(baos);
        StreamUtils.copy(wrapper.getContentInputStream(), zip);
        zip.close();
        response.addHeader("Content-Encoding", "gzip");
        response.setContentLength(baos.size());
        StreamUtils.copy(new ByteArrayInputStream(baos.toByteArray()), response.getOutputStream());
    }

    protected boolean processStatic(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain,
            String mimeType
    ) throws IOException, ServletException {
        boolean success = true;
        try {
            File targetFile = prepareTargetFile(request);
            if (targetFile.exists() && !isValidStaticCacheFile(targetFile)) {
                if (targetFile.delete()) {
                    releaseStaticCacheEntry();
                }
            }
            if (targetFile.exists()) {
                cacheAwareResponseHandler.setHeaders(response, targetFile, mimeType);
            } else if (!reserveStaticCacheEntry(targetFile)) {
                return false;
            } else if (!cacheStaticCompressedFileInFileSystem(request, response, chain, targetFile)) {
                // the downstream response was not cacheable and has already been written to the caller
                return true;
            }
            emitStaticFileToResponse(request, response, targetFile);
        } catch (Exception e) {
            LOG.error(String.format("Unable to send cached static/compressed version of resource %s. Falling back to dynamic compression.", request.getRequestURI()), e);
            success = false;
        }
        return success;
    }

    protected void emitStaticFileToResponse(HttpServletRequest request, HttpServletResponse response, File targetFile) throws IOException {
        long byteSize = targetFile.length();
        response.addHeader("Content-Encoding", "gzip");
        response.setContentLength(Long.valueOf(byteSize).intValue());
        if (sendFileSize > byteSize || !useSendFile) {
            //don't use sendFile for small files
            StreamUtils.copy(new FileInputStream(targetFile), response.getOutputStream());
        } else {
            request.setAttribute("org.apache.tomcat.sendfile.filename", targetFile.getAbsolutePath());
            request.setAttribute("org.apache.tomcat.sendfile.start", 0L);
            request.setAttribute("org.apache.tomcat.sendfile.end", byteSize);
        }
    }

    protected File prepareTargetFile(HttpServletRequest request) {
        return new File(getStaticCacheDirectory(), DigestUtils.sha256Hex(buildStaticCacheKey(request)) + ".gz");
    }

    /**
     * The cache key: host name plus request URI. The query string is deliberately not part of the key so that it cannot be
     * used to create unbounded cache entries for the same resource.
     */
    protected String buildStaticCacheKey(HttpServletRequest request) {
        String host = request.getServerName() == null ? "" : request.getServerName().toLowerCase(Locale.ROOT);
        return host + "\n" + request.getRequestURI();
    }

    protected File getStaticCacheDirectory() {
        File targetDir;
        if ("none".equals(compressedFileTempDirectory)) {
            targetDir = new File(System.getProperty("java.io.tmpdir"), DEFAULT_CACHE_DIRECTORY_NAME);
        } else {
            targetDir = new File(compressedFileTempDirectory);
        }
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        return targetDir;
    }

    /**
     * Delete cache files left over from a previous run so that a stale or poisoned entry does not survive a restart.
     */
    protected void purgeStaticFileCache() {
        File[] cached = getStaticCacheDirectory().listFiles((dir, name) -> CACHE_FILE_NAME.matcher(name).matches());
        int purged = 0;
        if (cached != null) {
            for (File file : cached) {
                if (file.delete()) {
                    purged++;
                }
            }
        }
        if (purged > 0 && LOG.isInfoEnabled()) {
            LOG.info(String.format("Purged %s compressed static files from %s", purged, getStaticCacheDirectory()));
        }
    }

    /**
     * Reserve a slot for a new cache file. Returns false once 'filter.compression.static.file.cache.max.entries' is
     * reached. A reservation that does not result in a new cache file is returned via {@link #releaseStaticCacheEntry()}.
     */
    protected boolean reserveStaticCacheEntry(File targetFile) {
        if (staticFileCacheMaxEntries <= 0) {
            return true;
        }
        if (staticFileCacheEntries == null) {
            synchronized (this) {
                if (staticFileCacheEntries == null) {
                    String[] existing = targetFile.getParentFile().list(
                            (dir, name) -> CACHE_FILE_NAME.matcher(name).matches()
                    );
                    staticFileCacheEntries = new AtomicInteger(existing == null ? 0 : existing.length);
                }
            }
        }
        while (true) {
            int current = staticFileCacheEntries.get();
            if (current >= staticFileCacheMaxEntries) {
                LOG.debug(String.format("Static file compression cache is full (%s entries); compressing %s dynamically",
                        staticFileCacheMaxEntries, targetFile.getName()));
                return false;
            }
            if (staticFileCacheEntries.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    protected void releaseStaticCacheEntry() {
        if (staticFileCacheMaxEntries > 0 && staticFileCacheEntries != null) {
            staticFileCacheEntries.updateAndGet(count -> Math.max(0, count - 1));
        }
    }

    /**
     * Run the downstream chain and store its response in {@code targetFile} if it is cacheable (see
     * {@link #isStaticCacheableResponse(FileSystemResponseWrapper, File)}). Expects a slot to have been reserved via
     * {@link #reserveStaticCacheEntry(File)}.
     *
     * @return true if the response was stored in the cache and still needs to be emitted, false if it was not cacheable
     * and has already been written to the caller
     */
    protected boolean cacheStaticCompressedFileInFileSystem(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain,
            File targetFile
    ) throws IOException, ServletException {
        boolean newEntry = false;
        String tempRoot = UUID.randomUUID().toString();
        File tempDir = targetFile.getParentFile();
        File tempFile = File.createTempFile(tempRoot, ".tmp", tempDir);
        FileSystemResponseWrapper wrapper = new FileSystemResponseWrapper(response, tempFile);
        try {
            chain.doFilter(request, wrapper);
            wrapper.closeFileOutputStream();
            if (!isStaticCacheableResponse(wrapper, tempFile)) {
                emitUncachedResponse(response, tempFile);
                return false;
            }
            File compressedFile = File.createTempFile(tempRoot, ".tmpgz", tempDir);
            OutputStream compressedOut = new GZIPOutputStream(new FileOutputStream(compressedFile));
            try (InputStream in = new BufferedInputStream(new FileInputStream(tempFile))) {
                StreamUtils.copy(in, compressedOut);
            } finally {
                IOUtils.closeQuietly(compressedOut);
            }
            newEntry = !targetFile.exists();
            atomicMove.replaceExisting(compressedFile, targetFile);
            return true;
        } finally {
            if (!newEntry) {
                releaseStaticCacheEntry();
            }
            wrapper.closeFileOutputStream();
            tempFile.delete();
        }
    }

    /**
     * Write a response captured by {@link #cacheStaticCompressedFileInFileSystem} that is not eligible for the cache
     * (for example a 404 or a private response) straight to the caller, compressing any body.
     */
    protected void emitUncachedResponse(HttpServletResponse response, File capturedBody) throws IOException {
        if (capturedBody.length() == 0L) {
            return;
        }
        if (response.isCommitted()) {
            try (InputStream in = new BufferedInputStream(new FileInputStream(capturedBody))) {
                StreamUtils.copy(in, response.getOutputStream());
            }
            return;
        }
        FastByteArrayOutputStream baos = new FastByteArrayOutputStream(1024);
        GZIPOutputStream zip = new GZIPOutputStream(baos);
        try (InputStream in = new BufferedInputStream(new FileInputStream(capturedBody))) {
            StreamUtils.copy(in, zip);
        }
        zip.close();
        response.addHeader("Content-Encoding", "gzip");
        response.setContentLength(baos.size());
        StreamUtils.copy(baos.getInputStream(), response.getOutputStream());
    }

    protected boolean useGzipCompression(HttpServletRequest request, HttpServletResponse response) throws MalformedURLException {
        for (Pattern pattern : blackListPatterns) {
            String uri = request.getRequestURI().toLowerCase();
            if (pattern.matcher(uri).matches()) {
                return false;
            }
        }

        // If Content-Encoding header is already set on response, skip compression
        if (response.containsHeader("Content-Encoding")) {
            return false;
        }

        // Are we allowed to compress ?
        String s = request.getParameter("gzip");
        if ("false".equals(s)) {
            return false;
        }

        Enumeration<?> e = request.getHeaders("Accept-Encoding");

        while (e.hasMoreElements()) {
            String name = (String) e.nextElement();
            if (name.contains("gzip")) {
                return true;
            }
        }

        return false;
    }

    @Configuration
    static class BroadleafSpringResourceConfig {

        @Bean
        public CacheAwareResponseHandler blCacheAwareReponseHandler(
                @Value("${staticResourceBrowserCacheSeconds}") int cacheSeconds
        ) {
            CacheAwareResponseHandler handler = new CacheAwareResponseHandler();
            handler.setCacheSeconds(cacheSeconds);
            return handler;
        }
    }

}
