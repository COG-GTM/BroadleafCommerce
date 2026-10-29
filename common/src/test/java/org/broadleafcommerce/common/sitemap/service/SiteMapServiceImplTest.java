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
package org.broadleafcommerce.common.sitemap.service;

import org.broadleafcommerce.common.file.service.BroadleafFileServiceImpl;
import org.broadleafcommerce.common.sitemap.domain.SiteMapConfiguration;
import org.broadleafcommerce.common.sitemap.domain.SiteMapConfigurationImpl;
import org.broadleafcommerce.common.sitemap.exception.SiteMapException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies that storefront sitemap requests only trigger generation for sitemap file names the active configuration
 * produces, and that on-demand generations are serialized and throttled.
 */
public class SiteMapServiceImplTest {

    protected File siteMapDir;
    protected SiteMapConfiguration siteMapConfiguration;
    protected TestSiteMapService siteMapService;

    @Before
    public void setup() throws IOException {
        siteMapDir = Files.createTempDirectory("sitemap-service-test").toFile();
        siteMapConfiguration = new SiteMapConfigurationImpl();
        siteMapConfiguration.setIndexedSiteMapFileName("sitemap_index.xml");

        siteMapService = new TestSiteMapService();
        siteMapService.broadleafFileService = new BroadleafFileServiceImpl() {
            @Override
            public File getResource(String name, Long localTimeout) {
                return new File(siteMapDir, name.replaceFirst("^/+", ""));
            }
        };
    }

    @After
    public void cleanup() {
        File[] files = siteMapDir.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        siteMapDir.delete();
    }

    @Test
    public void testUnknownFileNamesDoNotTriggerGeneration() throws Exception {
        String[] names = {
                "/sitemapZZZ.xml", "/sitemap0.xml", "/sitemap1234567890.xml", "/sitemap1.xml.bak", "/foo.xml",
                "/sitemap.xml.gz.gz", "/", "", null, "/../sitemap.xml", "/x/sitemap.xml"
        };
        for (String name : names) {
            Assert.assertNull("Expected no sitemap file for " + name, siteMapService.getSiteMapFile(name));
        }
        Assert.assertEquals(0, siteMapService.generationCount.get());
    }

    @Test
    public void testNoActiveConfigurationDoesNotTriggerGeneration() throws Exception {
        siteMapConfiguration = null;
        Assert.assertNull(siteMapService.getSiteMapFile("/sitemap.xml"));
        Assert.assertEquals(0, siteMapService.generationCount.get());
    }

    @Test
    public void testConfiguredFileNamesAreRecognized() {
        String[] names = {
                "/sitemap.xml", "sitemap.xml", "/sitemap.xml.gz", "/sitemap_index.xml", "/sitemap_index.xml.gz",
                "/sitemap1.xml", "/sitemap42.xml.gz", "/sitemap999999999.xml"
        };
        for (String name : names) {
            Assert.assertTrue("Expected " + name + " to be a sitemap file", siteMapService.isGeneratedSiteMapFileName(name));
        }
    }

    @Test
    public void testCustomIndexPatternIsRecognized() {
        siteMapConfiguration.setIndexedSiteMapFilePattern("products-###.xml");
        Assert.assertTrue(siteMapService.isGeneratedSiteMapFileName("/products-3.xml.gz"));
        Assert.assertFalse(siteMapService.isGeneratedSiteMapFileName("/sitemap3.xml"));
        Assert.assertFalse(siteMapService.isGeneratedSiteMapFileName("/products-x.xml"));
    }

    @Test
    public void testMissingConfiguredFileIsGeneratedOnceThenServedFromDisk() throws Exception {
        siteMapService.filesToCreate = new String[] { "sitemap_index.xml", "sitemap1.xml" };

        File first = siteMapService.getSiteMapFile("/sitemap_index.xml");
        Assert.assertNotNull(first);
        Assert.assertTrue(first.exists());
        Assert.assertEquals(1, siteMapService.generationCount.get());

        File second = siteMapService.getSiteMapFile("/sitemap_index.xml");
        Assert.assertTrue(second.exists());
        Assert.assertTrue(siteMapService.getSiteMapFile("/sitemap1.xml").exists());
        Assert.assertEquals(1, siteMapService.generationCount.get());
    }

    @Test
    public void testRepeatedRequestsForMissingIndexedFileAreThrottled() throws Exception {
        siteMapService.filesToCreate = new String[] { "sitemap_index.xml", "sitemap1.xml" };

        for (int i = 2; i < 50; i++) {
            File file = siteMapService.getSiteMapFile("/sitemap" + i + ".xml");
            Assert.assertNotNull(file);
            Assert.assertFalse(file.exists());
        }
        Assert.assertEquals(1, siteMapService.generationCount.get());

        siteMapService.minIntervalMillis = 0L;
        siteMapService.getSiteMapFile("/sitemap2.xml");
        Assert.assertEquals(2, siteMapService.generationCount.get());
    }

    @Test
    public void testFailedGenerationIsAlsoThrottled() throws Exception {
        siteMapService.failGeneration = true;
        try {
            siteMapService.getSiteMapFile("/sitemap.xml");
            Assert.fail("Expected generation failure");
        } catch (SiteMapException e) {
            // expected
        }
        File file = siteMapService.getSiteMapFile("/sitemap.xml");
        Assert.assertFalse(file.exists());
        Assert.assertEquals(1, siteMapService.generationCount.get());
    }

    @Test
    public void testCreateIfNotFoundDisabledReturnsNull() throws Exception {
        siteMapService.createIfNotFound = false;
        Assert.assertNull(siteMapService.getSiteMapFile("/sitemap.xml"));
        Assert.assertEquals(0, siteMapService.generationCount.get());
    }

    @Test
    public void testRequestDuringRunningGenerationDoesNotStartAnother() throws Exception {
        siteMapService.filesToCreate = new String[] { "sitemap.xml" };
        siteMapService.minIntervalMillis = 0L;
        siteMapService.generationStarted = new CountDownLatch(1);
        siteMapService.releaseGeneration = new CountDownLatch(1);

        Thread generator = new Thread(() -> {
            try {
                siteMapService.getSiteMapFile("/sitemap.xml");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        generator.start();
        Assert.assertTrue(siteMapService.generationStarted.await(10, TimeUnit.SECONDS));

        File concurrent = siteMapService.getSiteMapFile("/sitemap.xml");
        Assert.assertFalse(concurrent.exists());

        siteMapService.releaseGeneration.countDown();
        generator.join(10000);
        Assert.assertEquals(1, siteMapService.generationCount.get());
        Assert.assertTrue(siteMapService.getSiteMapFile("/sitemap.xml").exists());
    }

    @Test
    public void testExpiredFileRegenerationIsThrottled() throws Exception {
        siteMapService.autoGenerateAfterTimeout = true;
        siteMapService.timeoutMillis = 1000L;
        File siteMap = new File(siteMapDir, "sitemap.xml");
        Assert.assertTrue(siteMap.createNewFile());
        Assert.assertTrue(siteMap.setLastModified(System.currentTimeMillis() - 10000L));

        siteMapService.getSiteMapFile("/sitemap.xml");
        siteMapService.getSiteMapFile("/sitemap.xml");
        Assert.assertEquals(1, siteMapService.generationCount.get());
    }

    protected class TestSiteMapService extends SiteMapServiceImpl {

        protected final AtomicInteger generationCount = new AtomicInteger();
        protected String[] filesToCreate = new String[0];
        protected boolean failGeneration = false;
        protected boolean createIfNotFound = true;
        protected boolean autoGenerateAfterTimeout = false;
        protected long minIntervalMillis = 60000L;
        protected long timeoutMillis = 3600000L;
        protected CountDownLatch generationStarted;
        protected CountDownLatch releaseGeneration;

        @Override
        protected SiteMapGenerationResponse generateSiteMapInternal() throws SiteMapException, IOException {
            generationCount.incrementAndGet();
            if (generationStarted != null) {
                generationStarted.countDown();
                try {
                    releaseGeneration.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failGeneration) {
                throw new SiteMapException("generation failed");
            }
            for (String fileName : filesToCreate) {
                File file = new File(siteMapDir, fileName);
                file.createNewFile();
                file.setLastModified(System.currentTimeMillis());
            }
            return new SiteMapGenerationResponse();
        }

        @Override
        protected SiteMapConfiguration findActiveSiteMapConfiguration() {
            return siteMapConfiguration;
        }

        @Override
        public boolean getCreateSiteMapIfNotFound() {
            return createIfNotFound;
        }

        @Override
        public boolean getAutoGenerateSiteMapAfterTimeout() {
            return autoGenerateAfterTimeout;
        }

        @Override
        public long getOnDemandGenerationMinIntervalInMillis() {
            return minIntervalMillis;
        }

        @Override
        public Long getSiteMapTimeoutInMillis() {
            return timeoutMillis;
        }
    }
}
