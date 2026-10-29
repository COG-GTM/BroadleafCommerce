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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.servlet.View;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Created by jfischer
 */
public class StaticAssetView implements View {

    private static final Log LOG = LogFactory.getLog(StaticAssetView.class);

    protected boolean browserAssetCachingEnabled = true;
    protected long cacheSeconds = 60 * 60 * 24;

    /**
     * MIME types that a browser would execute as a document (and so could run script on the asset host) if they were
     * opened directly. These are still served, so they keep working when embedded (e.g. an SVG in an img tag), but are
     * sandboxed and downloaded instead of rendered when navigated to.
     */
    protected Set<String> activeContentMimeTypes = new HashSet<>(Arrays.asList(
            "text/html",
            "application/xhtml+xml",
            "image/svg+xml",
            "text/xml",
            "application/xml",
            "text/xsl",
            "application/xslt+xml",
            "application/vnd.wap.xhtml+xml",
            "multipart/x-mixed-replace"
    ));

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String cacheFilePath = (String) model.get("cacheFilePath");
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(cacheFilePath));
        try {
            String mimeType = (String) model.get("mimeType");
            response.setContentType(mimeType);
            applyContentSecurityHeaders(mimeType, response);
            if (!browserAssetCachingEnabled) {
                response.setHeader("Cache-Control", "no-cache");
                response.setHeader("Pragma", "no-cache");
                response.setDateHeader("Expires", 0);
            } else {
                response.setHeader("Cache-Control", "public");
                response.setHeader("Pragma", "cache");
                if (!StringUtils.isEmpty(request.getHeader("If-Modified-Since"))) {
                    long lastModified = request.getDateHeader("If-Modified-Since");
                    Calendar last = Calendar.getInstance();
                    last.setTime(new Date(lastModified));
                    Calendar check = Calendar.getInstance();
                    check.add(Calendar.SECOND, -2 * Long.valueOf(cacheSeconds).intValue());
                    if (check.compareTo(last) < 0) {
                        response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                        return;
                    }
                } else {
                    Calendar check = Calendar.getInstance();
                    check.add(Calendar.SECOND, -1 * Long.valueOf(cacheSeconds).intValue());
                    response.setDateHeader("Last-Modified", check.getTimeInMillis());
                }
                Calendar cal = Calendar.getInstance();
                long year = cacheSeconds * 365;
                cal.add(Calendar.SECOND, Long.valueOf(year).intValue());
                response.setDateHeader("Expires", cal.getTimeInMillis());
            }
            OutputStream os = response.getOutputStream();
            boolean eof = false;
            while (!eof) {
                int temp = bis.read();
                if (temp < 0) {
                    eof = true;
                } else {
                    os.write(temp);
                }
            }
            os.flush();
        } catch (Exception e) {
            if (e.getCause() instanceof SocketException) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Unable to stream asset", e);
                }
            } else {
                LOG.error("Unable to stream asset", e);
                throw e;
            }
        } finally {
            try {
                bis.close();
            } catch (Throwable e) {
                //do nothing
            }
        }
    }

    protected void applyContentSecurityHeaders(String mimeType, HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (isActiveContent(mimeType)) {
            response.setHeader("Content-Disposition", "attachment");
            response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'");
        }
    }

    protected boolean isActiveContent(String mimeType) {
        if (StringUtils.isBlank(mimeType)) {
            return false;
        }
        String baseType = StringUtils.substringBefore(mimeType, ";").trim().toLowerCase(Locale.ROOT);
        return activeContentMimeTypes.contains(baseType);
    }

    public Set<String> getActiveContentMimeTypes() {
        return activeContentMimeTypes;
    }

    public void setActiveContentMimeTypes(Set<String> activeContentMimeTypes) {
        this.activeContentMimeTypes = activeContentMimeTypes;
    }

    public boolean isBrowserAssetCachingEnabled() {
        return browserAssetCachingEnabled;
    }

    public void setBrowserAssetCachingEnabled(boolean browserAssetCachingEnabled) {
        this.browserAssetCachingEnabled = browserAssetCachingEnabled;
    }

    public long getCacheSeconds() {
        return cacheSeconds;
    }

    public void setCacheSeconds(long cacheSeconds) {
        this.cacheSeconds = cacheSeconds;
    }

}
